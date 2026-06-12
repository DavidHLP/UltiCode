"""LeetCode-style data structure definitions for the UltiCode sandbox harness.

The Python harness expects the user's ``solution.py`` to define a ``Solution``
class. ``ListNode`` and ``TreeNode`` are made available in the module's
namespace under the same name used in starter snippets — users do NOT need
to import anything.
"""

from __future__ import annotations

from typing import Optional


class ListNode:
    """Singly linked list node (LeetCode convention).

    Attributes are public; the platform never wraps them.
    """

    __slots__ = ("val", "next")

    def __init__(self, val: int = 0, nxt: "Optional[ListNode]" = None) -> None:
        self.val = val
        self.next = nxt


class TreeNode:
    """Binary tree node (LeetCode convention)."""

    __slots__ = ("val", "left", "right")

    def __init__(
        self,
        val: int = 0,
        left: "Optional[TreeNode]" = None,
        right: "Optional[TreeNode]" = None,
    ) -> None:
        self.val = val
        self.left = left
        self.right = right
