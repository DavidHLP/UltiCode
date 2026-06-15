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
import sys
from collections import deque
from typing import Any, Callable, Dict, List, Optional, Tuple, get_args, get_origin

from oj_types import ListNode, TreeNode

# Module name the user's solution file is imported under. main.py and
# _case_runner.py both load it via load_solution_module() so the LeetCode
# preamble (typing names + ListNode/TreeNode) is injected into the user
# module's namespace before its code executes — bare annotations like
# ``List[Optional[ListNode]]`` then resolve at runtime instead of raising
# ``NameError`` on Python <3.14 (the sandbox base image is bookworm = 3.11).
USER_SOLUTION_MODULE = "solution"

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


def normalize_return_value(result: Any, method: Callable[..., Any]) -> Any:
    """Map a method's return to its LeetCode JSON shape.

    On LeetCode a ``None`` return from a ListNode/TreeNode-typed method is an
    *empty* structure — it serializes to ``[]``, not ``null``. Without this
    remap a correct solution that returns ``None`` for an empty input (e.g.
    ``mergeKLists([])``) compares ``'null'`` against an ``'[]'`` expected
    output and is wrongly judged Wrong Answer. Non-OJ return types are
    unaffected, so ``Optional[int] -> None`` still serializes to ``null``.
    """
    if result is not None:
        return result
    try:
        return_ann = inspect.signature(method).return_annotation
    except (TypeError, ValueError):
        return result
    if _leaf_oj_type(return_ann):
        return []
    return result


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


def _leaf_oj_type(ann: Any) -> Optional[str]:
    """Return 'ListNode' / 'TreeNode' if the annotation (recursively) carries
    that OJ data-structure type, else None.

    Walks ``typing.get_args`` rather than parsing ``str(annotation)`` — the
    string format of typing generics changed between 3.9 / 3.11 / 3.14, so a
    substring match would be fragile. Handles bare types (``ListNode``),
    Optionals (``Optional[ListNode]`` = ``Union[ListNode, None]``) and nested
    generics (``List[Optional[ListNode]]``).
    """
    if isinstance(ann, type):
        if ann is ListNode:
            return "ListNode"
        if ann is TreeNode:
            return "TreeNode"
        return None
    for sub in get_args(ann):
        if sub is type(None):  # skip NoneType produced by Optional[X]
            continue
        found = _leaf_oj_type(sub)
        if found:
            return found
    return None


def _classify_annotation(ann: Any) -> str:
    """Map a parameter annotation to an ``adapt_arg`` hint.

    Returns one of ``''`` | ``'ListNode'`` | ``'TreeNode'`` | ``'ListNode[]'``
    | ``'TreeNode[]'``. A top-level ``list`` origin means the supplied value is
    a list whose elements should each be converted (e.g.
    ``List[Optional[ListNode]]`` for mergeKLists). Non-OJ annotations yield
    ``''`` (pass-through).
    """
    leaf = _leaf_oj_type(ann)
    if not leaf:
        return ""
    if get_origin(ann) is list:
        return f"{leaf}[]"
    return leaf


def _param_hints(method: Callable[..., Any]) -> List[str]:
    sig = inspect.signature(method)
    return [_classify_annotation(p.annotation) for p in sig.parameters.values()]


def build_solution_preamble() -> Dict[str, Any]:
    """Symbols seeded into the user solution namespace so user code needs no
    imports — the "just write the algorithm" UX this platform wants.

    Three groups are injected:
      1. ``from typing import *`` — so bare annotations like
         ``List[Optional[ListNode]]`` resolve at runtime on Python <3.14 (the
         sandbox base image is Debian bookworm = 3.11, where annotations are
         evaluated eagerly at ``def`` time during ``import solution``).
      2. Common pure-compute standard-library modules (heapq, math, bisect,
         itertools, functools, operator, string, fractions, decimal,
         statistics, re, collections) as module objects, plus the
         high-frequency ``collections`` symbols (deque, Counter, defaultdict,
         OrderedDict, namedtuple). Users can thus write ``heapq.heappush`` or
         ``deque()`` with no ``import`` statement.
      3. Platform data structures ``ListNode`` / ``TreeNode``.

    Deliberately NOT injected: ``os``, ``sys``, ``subprocess``, ``socket``,
    ``shutil``, ``ctypes``, ``multiprocessing`` — modules that can break
    sandbox isolation (spawn processes, touch the filesystem, exit the
    interpreter, open sockets). The exit guard in main.py blocks
    ``os._exit``/``sys.exit``; withholding these modules is defense in depth.
    """
    import bisect
    import collections
    import decimal
    import fractions
    import functools
    import heapq
    import itertools
    import math
    import operator
    import re
    import statistics
    import string
    import typing

    names: Dict[str, Any] = {}

    # 1. typing names (LeetCode `from typing import *`).
    typing_names = getattr(typing, "__all__", None) or [
        n for n in dir(typing) if not n.startswith("_")
    ]
    for name in typing_names:
        names[name] = getattr(typing, name)

    # 2. Pure-compute stdlib modules, exposed as module objects so user code
    # can call ``heapq.heappush`` / ``math.inf`` / ``collections.deque`` with
    # no import. Only safe, side-effect-free modules — never os/sys/etc.
    for mod in (
        heapq, math, bisect, itertools, functools, operator,
        string, fractions, decimal, statistics, re, collections,
    ):
        names[mod.__name__] = mod

    # High-frequency collections symbols also exposed bare (deque(),
    # Counter(), defaultdict(), OrderedDict(), namedtuple) so users don't need
    # `from collections import ...`.
    for sym in ("deque", "Counter", "defaultdict", "OrderedDict", "namedtuple"):
        names[sym] = getattr(collections, sym)

    # 3. Platform data structures.
    names["ListNode"] = ListNode
    names["TreeNode"] = TreeNode
    return names


def load_solution_module(solution_path: str) -> Any:
    """Import the user's solution.py with the LeetCode preamble injected.

    The preamble (typing names + ListNode/TreeNode) is seeded into the
    module's ``__dict__`` *before* ``exec_module`` runs the user code, so bare
    annotations resolve at runtime instead of raising ``NameError``. The
    module is also registered in ``sys.modules['solution']`` so user code (or
    any downstream reference to ``import solution``) keeps working.
    """
    import importlib.util

    spec = importlib.util.spec_from_file_location(USER_SOLUTION_MODULE, solution_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load solution module from {solution_path}")
    module = importlib.util.module_from_spec(spec)
    module.__dict__.update(build_solution_preamble())
    sys.modules[USER_SOLUTION_MODULE] = module
    spec.loader.exec_module(module)
    return module
