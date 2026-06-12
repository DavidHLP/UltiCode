/**
 * Standard LeetCode-style binary tree node.
 *
 * <p>Same default-package convention as {@link ListNode}; see that class's
 * javadoc for the rationale.
 *
 * <p>Serialization format used by {@link Harness#toTreeNode(java.util.List)}
 * and {@link Harness#fromTreeNode(TreeNode)} is the LeetCode level-order
 * (BFS) encoding with {@code null} placeholders for absent children.
 */
public class TreeNode {

    public int val;
    public TreeNode left;
    public TreeNode right;

    public TreeNode() {}

    public TreeNode(int val) {
        this.val = val;
    }

    public TreeNode(int val, TreeNode left, TreeNode right) {
        this.val = val;
        this.left = left;
        this.right = right;
    }
}
