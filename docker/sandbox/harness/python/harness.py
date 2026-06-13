"""Argument adaptation, result serialization, and OJ data-structure helpers
for the UltiCode sandbox Python harness.

CR fixes applied:
- jsonable() uses id-set cycle detection + depth + node-count caps so a
  cyclic List/dict/TreeNode from user code is converted to a Runtime Error
  per case instead of recursing into RecursionError / OOM.
- adapt_arg() honors an explicit type_override (passed by main.py from the
  input spec's 'type' field). Annotation is still consulted as a fallback.
- normalize_json_str() uses allow_nan=False so a user returning float('inf')
  surfaces as a per-case Runtime Error rather than emitting non-standard JSON.
"""

from __future__ import annotations

import inspect
import json
from collections import deque
from typing import Any, Callable, List, Optional, Tuple

from oj_types import ListNode, TreeNode

LIST_NODE_TRAVERSAL_CAP = 100_000
MAX_JSONABLE_DEPTH = 512
MAX_JSONABLE_NODES = 1_000_000
MAX_USER_STDOUT_BYTES = 64 * 1024
MAX_USER_STDERR_BYTES = 64 * 1024


def to_list_node(values: Optional[List[Any]]) -> Optional[ListNode]:
    if not values:
        return None
    head = ListNode(int(values[0]))
    cur = head
    for v in values[1:]:
        cur.next = ListNode(int(v))
        cur = cur.next
    return head


def from_list_node(head: Optional[ListNode]) -> List[int]:
    out: List[int] = []
    cur = head
    while cur is not None and len(out) < LIST_NODE_TRAVERSAL_CAP:
        out.append(cur.val)
        cur = cur.next
    return out


def to_tree_node(values: Optional[List[Any]]) -> Optional[TreeNode]:
    if not values:
        return None
    if values[0] is None:
        return None
    root = TreeNode(int(values[0]))
    queue: deque = deque([root])
    i = 1
    while queue and i < len(values):
        node = queue.popleft()
        if i < len(values):
            v = values[i]
            i += 1
            if v is not None:
                node.left = TreeNode(int(v))
                queue.append(node.left)
        if i < len(values):
            v = values[i]
            i += 1
            if v is not None:
                node.right = TreeNode(int(v))
                queue.append(node.right)
    return root


def from_tree_node(root: Optional[TreeNode]) -> List[Optional[int]]:
    if root is None:
        return []
    raw: List[Optional[int]] = []
    queue: deque = deque([root])
    visited: set = set()
    while queue:
        node = queue.popleft()
        if node is None:
            raw.append(None)
        else:
            # Cycle guard against malformed user trees (id-based).
            oid = id(node)
            if oid in visited:
                raise ValueError("Cyclic reference in TreeNode result")
            visited.add(oid)
            raw.append(node.val)
            queue.append(node.left)
            queue.append(node.right)
            if len(raw) > MAX_JSONABLE_NODES:
                raise ValueError(f"TreeNode exceeds node limit {MAX_JSONABLE_NODES}")
    while raw and raw[-1] is None:
        raw.pop()
    return raw


def adapt_arg(value: Any, hint: Optional[str], type_override: Optional[str] = None) -> Any:
    """Adapt a JSON-parsed value to a hinted shape.

    Precedence:
      1. ``type_override`` from input spec's ``type`` field (most explicit)
      2. ``hint`` from ``inspect.signature`` annotation
      3. Pass-through (Python is dynamically typed)
    """
    effective = type_override or hint
    if effective == "ListNode":
        return to_list_node(value)
    if effective == "TreeNode":
        return to_tree_node(value)
    if effective == "ListNode[]" and isinstance(value, list):
        return [to_list_node(v) for v in value]
    if effective == "TreeNode[]" and isinstance(value, list):
        return [to_tree_node(v) for v in value]
    return value


class _JsonableCtx:
    __slots__ = ("seen", "depth", "nodes")

    def __init__(self) -> None:
        self.seen: set = set()
        self.depth: int = 0
        self.nodes: int = 0


def jsonable(value: Any) -> Any:
    """Convert a returned value to a JSON-serializable shape.

    CR fix #7: detects identity cycles and enforces ``MAX_JSONABLE_DEPTH`` /
    ``MAX_JSONABLE_NODES``. Raises ``ValueError`` on violation so the caller
    treats this case as a Runtime Error (rather than RecursionError or OOM
    becoming a harness panic that breaks the envelope contract).
    """
    return _jsonable_with_ctx(value, _JsonableCtx())


def _jsonable_with_ctx(value: Any, ctx: _JsonableCtx) -> Any:
    ctx.nodes += 1
    if ctx.nodes > MAX_JSONABLE_NODES:
        raise ValueError(f"Result exceeds node limit {MAX_JSONABLE_NODES}")
    if ctx.depth > MAX_JSONABLE_DEPTH:
        raise ValueError(f"Result nesting exceeds limit {MAX_JSONABLE_DEPTH}")
    if value is None:
        return None
    if isinstance(value, ListNode):
        return from_list_node(value)
    if isinstance(value, TreeNode):
        return from_tree_node(value)
    if isinstance(value, bool):
        # bool is subclass of int — handle before numbers so json sees True/False.
        return value
    if isinstance(value, (int, str)):
        return value
    if isinstance(value, float):
        # CR fix #8: reject non-finite floats here so the per-case path
        # surfaces this as Runtime Error rather than corrupting the envelope.
        import math
        if not math.isfinite(value):
            raise ValueError(f"Non-finite number in result: {value}")
        return value
    if isinstance(value, (list, tuple)):
        oid = id(value)
        if oid in ctx.seen:
            raise ValueError("Cyclic reference in result (list/tuple)")
        ctx.seen.add(oid)
        try:
            ctx.depth += 1
            return [_jsonable_with_ctx(v, ctx) for v in value]
        finally:
            ctx.depth -= 1
            ctx.seen.discard(oid)
    if isinstance(value, dict):
        oid = id(value)
        if oid in ctx.seen:
            raise ValueError("Cyclic reference in result (dict)")
        ctx.seen.add(oid)
        try:
            ctx.depth += 1
            return {str(k): _jsonable_with_ctx(v, ctx) for k, v in value.items()}
        finally:
            ctx.depth -= 1
            ctx.seen.discard(oid)
    return value


def parse_input_value(input_spec: Any) -> Any:
    """Extract and parse a single input value from the backend's input spec."""
    if isinstance(input_spec, dict):
        v = input_spec.get("value")
        if isinstance(v, str):
            return json.loads(v)
        return v
    return input_spec


def input_type_hint(input_spec: Any) -> Optional[str]:
    """Extract the optional ``type`` field from an input spec, if present."""
    if isinstance(input_spec, dict):
        t = input_spec.get("type")
        if isinstance(t, str) and t:
            return t
    return None


def normalize_json_str(s: str) -> str:
    """Canonicalize a JSON string for output comparison.

    ``allow_nan=False`` so that any latent Infinity / NaN in an expected output
    surfaces as an exception (caller catches it as Wrong Answer rather than
    silently emitting non-standard JSON).
    """
    return json.dumps(json.loads(s), separators=(",", ":"), allow_nan=False, sort_keys=False)


def resolve_method(solution_obj: Any, method_hint: Optional[str]) -> Tuple[Callable[..., Any], str, List[str]]:
    """Return (callable, method_name, param_type_hints) for the Solution method.

    Precedence:
      1. ``method_hint`` from input.json's ``method_name`` field (explicit)
      2. The single public non-dunder callable attribute (must be unique)
    Raises ``RuntimeError`` on ambiguity or absence.
    """
    if method_hint:
        attr = getattr(solution_obj, method_hint, None)
        if not callable(attr):
            raise RuntimeError(
                f"Method '{method_hint}' not found (or not callable) on Solution instance"
            )
        return attr, method_hint, _param_hints(attr)
    candidates: List[str] = []
    for name in dir(solution_obj):
        if name.startswith("_"):
            continue
        attr = getattr(solution_obj, name)
        if callable(attr):
            candidates.append(name)
    if not candidates:
        raise RuntimeError(
            "Solution class has no public method. User code must define a "
            "Solution class with at least one public (non-underscore) method."
        )
    if len(candidates) > 1:
        raise RuntimeError(
            f"Solution has multiple public methods ({', '.join(sorted(candidates))}); "
            "supply 'method_name' in input.json to disambiguate."
        )
    attr = getattr(solution_obj, candidates[0])
    return attr, candidates[0], _param_hints(attr)


def _param_hints(method: Callable[..., Any]) -> List[str]:
    sig = inspect.signature(method)
    hints: List[str] = []
    for p in sig.parameters.values():
        ann = p.annotation
        if isinstance(ann, type):
            hints.append(ann.__name__)
        elif ann is inspect.Parameter.empty:
            hints.append("")
        else:
            hints.append(str(ann))
    return hints
