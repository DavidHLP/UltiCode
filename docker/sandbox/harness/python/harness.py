"""Argument adaptation, result serialization, and OJ data-structure helpers
for the UltiCode sandbox Python harness.

The harness layer here is intentionally small because Python ships with a
fast, correct ``json`` module — the parser/serializer gymnastics required
in the Java harness (no third-party deps allowed) are unnecessary.
"""

from __future__ import annotations

import inspect
import json
from collections import deque
from typing import Any, Callable, List, Optional, Tuple

from oj_types import ListNode, TreeNode

LIST_NODE_TRAVERSAL_CAP = 100_000


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
    while queue:
        node = queue.popleft()
        if node is None:
            raw.append(None)
        else:
            raw.append(node.val)
            queue.append(node.left)
            queue.append(node.right)
    while raw and raw[-1] is None:
        raw.pop()
    return raw


def adapt_arg(value: Any, hint: Optional[str]) -> Any:
    """Adapt a JSON-parsed value to a hinted shape.

    Python is dynamically typed so the hint is only a directive when the
    type cannot be inferred from the value alone (e.g. ``"ListNode"`` →
    convert a list to a linked list). For everything else the parsed value
    is passed through unchanged.
    """
    if hint == "ListNode":
        return to_list_node(value)
    if hint == "TreeNode":
        return to_tree_node(value)
    if hint == "ListNode[]" and isinstance(value, list):
        return [to_list_node(v) for v in value]
    return value


def jsonable(value: Any) -> Any:
    """Convert a returned value to a JSON-serializable shape."""
    if isinstance(value, ListNode):
        return from_list_node(value)
    if isinstance(value, TreeNode):
        return from_tree_node(value)
    if isinstance(value, (list, tuple)):
        return [jsonable(v) for v in value]
    if isinstance(value, dict):
        return {str(k): jsonable(v) for k, v in value.items()}
    return value


def parse_input_value(input_spec: Any) -> Any:
    """Extract and parse a single input value from the backend's input spec."""
    if isinstance(input_spec, dict):
        v = input_spec.get("value")
        if isinstance(v, str):
            return json.loads(v)
        return v
    return input_spec


def normalize_json_str(s: str) -> str:
    """Canonicalize a JSON string for output comparison."""
    return json.dumps(json.loads(s), separators=(",", ":"), sort_keys=False)


def find_first_public_method(solution_obj: Any) -> Tuple[Callable[..., Any], str, List[str]]:
    """Return (callable, method_name, param_type_hints) for the first
    non-dunder callable attribute on the Solution instance.
    """
    for name in dir(solution_obj):
        if name.startswith("_"):
            continue
        attr = getattr(solution_obj, name)
        if callable(attr):
            sig = inspect.signature(attr)
            hints = []
            for p in sig.parameters.values():
                ann = p.annotation
                hints.append(ann.__name__ if isinstance(ann, type) else str(ann) if ann is not inspect.Parameter.empty else "")
            return attr, name, hints
    raise RuntimeError(
        "Solution class has no public method. User code must define a "
        "Solution class with at least one public (non-underscore) method."
    )
