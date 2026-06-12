/**
 * Standard LeetCode-style singly linked list node.
 *
 * <p>Declared in the default (unnamed) package so that user-submitted
 * {@code class Solution} files can reference {@code ListNode} directly
 * without an {@code import} statement — matching the convention that
 * LeetCode, HackerRank, and the legacy in-source harness all used.
 *
 * <p>Fields are {@code public} (not encapsulated) because the LeetCode
 * convention assumes user code reads and writes {@code val} / {@code next}
 * directly. Do not retrofit getters/setters — it would break every existing
 * starter snippet.
 */
public class ListNode {

    public int val;
    public ListNode next;

    public ListNode() {}

    public ListNode(int val) {
        this.val = val;
    }

    public ListNode(int val, ListNode next) {
        this.val = val;
        this.next = next;
    }
}
