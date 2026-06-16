package com.ulticode.modules.submission.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单测覆盖 OJSignatureParser 对 python / java / cpp starter_code 的解析,
 * 以及各种退化场景(无注解、缺 class Solution、不支持语言等)。
 */
class OJSignatureParserTest {

    private static void assertTypes(List<String> actual, String... expected) {
        assertEquals(List.of(expected), actual);
    }

    @Nested
    class Python {

        @Test
        void linkedListOptionalAnnotation() {
            String code = "class Solution:\n"
                    + "    def addTwoNumbers(self, l1: Optional[ListNode], l2: Optional[ListNode]) -> Optional[ListNode]:\n"
                    + "        pass\n";
            assertTypes(OJSignatureParser.parse(code, "python"), "ListNode", "ListNode");
        }

        @Test
        void listOfListsIsArray() {
            // mergeKLists: List[Optional[ListNode]] -> ListNode[]
            String code = "class Solution:\n"
                    + "    def mergeKLists(self, lists: List[Optional[ListNode]]) -> Optional[ListNode]:\n"
                    + "        pass\n";
            assertTypes(OJSignatureParser.parse(code, "python"), "ListNode[]");
        }

        @Test
        void treeAnnotation() {
            String code = "class Solution:\n"
                    + "    def inorderTraversal(self, root: Optional[TreeNode]) -> List[int]:\n"
                    + "        pass\n";
            assertTypes(OJSignatureParser.parse(code, "python"), "TreeNode");
        }

        @Test
        void scalarParamsAreNull() {
            // twoSum: nums: List[int], target: int -> [null, null]
            String code = "class Solution:\n"
                    + "    def twoSum(self, nums: List[int], target: int) -> List[int]:\n"
                    + "        pass\n";
            List<String> types = OJSignatureParser.parse(code, "python");
            assertEquals(2, types.size());
            assertNull(types.get(0));
            assertNull(types.get(1));
        }

        @Test
        void unannotatedParamIsNull() {
            // No annotation on head -> cannot infer, position is null (safe degrade).
            String code = "class Solution:\n"
                    + "    def reverse(self, head):\n"
                    + "        pass\n";
            List<String> types = OJSignatureParser.parse(code, "python");
            assertEquals(1, types.size());
            assertNull(types.get(0));
        }

        @Test
        void selfIsSkipped() {
            // self must not occupy a position, otherwise types shift by one.
            String code = "class Solution:\n"
                    + "    def hasCycle(self, head: Optional[ListNode]) -> bool:\n"
                    + "        pass\n";
            assertTypes(OJSignatureParser.parse(code, "python"), "ListNode");
        }

        @Test
        void ignoresCommentedListNodeDefinition() {
            // The commented-out ListNode class definition must not fool the parser.
            String code = "# Definition for singly-linked list.\n"
                    + "# class ListNode:\n"
                    + "#     def __init__(self, val=0, next=None):\n"
                    + "#         self.val = val\n"
                    + "#         self.next = next\n"
                    + "class Solution:\n"
                    + "    def addTwoNumbers(self, l1: Optional[ListNode], l2: Optional[ListNode]) -> Optional[ListNode]:\n"
                    + "        return None\n";
            assertTypes(OJSignatureParser.parse(code, "python"), "ListNode", "ListNode");
        }

        @Test
        void realProblem2Starter() {
            String code = "# Definition for singly-linked list.\n"
                    + "# class ListNode:\n"
                    + "#     def __init__(self, val=0, next=None):\n"
                    + "#         self.val = val\n"
                    + "#         self.next = next\n"
                    + "class Solution:\n"
                    + "    def addTwoNumbers(self, l1: Optional[ListNode], l2: Optional[ListNode]) -> Optional[ListNode]:\n"
                    + "        \"\"\"返回表示两数之和的链表头节点\"\"\"\n"
                    + "        # TODO: 请在此实现\n"
                    + "        return None\n";
            assertTypes(OJSignatureParser.parse(code, "python"), "ListNode", "ListNode");
        }
    }

    @Nested
    class Java {

        @Test
        void linkedList() {
            String code = "/** Definition for singly-linked list. */\n"
                    + "class Solution {\n"
                    + "    public ListNode addTwoNumbers(ListNode l1, ListNode l2) {\n"
                    + "        return null;\n"
                    + "    }\n"
                    + "}\n";
            assertTypes(OJSignatureParser.parse(code, "java"), "ListNode", "ListNode");
        }

        @Test
        void linkedListArray() {
            String code = "class Solution {\n"
                    + "    public ListNode mergeKLists(ListNode[] lists) {\n"
                    + "        return null;\n"
                    + "    }\n"
                    + "}\n";
            assertTypes(OJSignatureParser.parse(code, "java"), "ListNode[]");
        }

        @Test
        void scalarParamsAreNull() {
            String code = "class Solution {\n"
                    + "    public int[] twoSum(int[] nums, int target) {\n"
                    + "        return null;\n"
                    + "    }\n"
                    + "}\n";
            List<String> types = OJSignatureParser.parse(code, "java");
            assertEquals(2, types.size());
            assertNull(types.get(0));
            assertNull(types.get(1));
        }

        @Test
        void ignoresListNodeDefInComment() {
            String code = "/**\n"
                    + " * Definition for singly-linked list.\n"
                    + " * public class ListNode { int val; ListNode next; ListNode(int x) { val = x; } }\n"
                    + " */\n"
                    + "class Solution {\n"
                    + "    public ListNode addTwoNumbers(ListNode l1, ListNode l2) {\n"
                    + "        return null;\n"
                    + "    }\n"
                    + "}\n";
            assertTypes(OJSignatureParser.parse(code, "java"), "ListNode", "ListNode");
        }
    }

    @Nested
    class Cpp {

        @Test
        void linkedListPointer() {
            String code = "/** Definition for singly-linked list. */\n"
                    + "class Solution {\n"
                    + "public:\n"
                    + "    ListNode* addTwoNumbers(ListNode* l1, ListNode* l2) {\n"
                    + "        return nullptr;\n"
                    + "    }\n"
                    + "};\n";
            assertTypes(OJSignatureParser.parse(code, "cpp"), "ListNode", "ListNode");
        }

        @Test
        void vectorOfPointersIsArray() {
            String code = "class Solution {\n"
                    + "public:\n"
                    + "    ListNode* mergeKLists(vector<ListNode*>& lists) {\n"
                    + "        return nullptr;\n"
                    + "    }\n"
                    + "};\n";
            assertTypes(OJSignatureParser.parse(code, "cpp"), "ListNode[]");
        }

        @Test
        void realProblem2Starter() {
            String code = "/**\n"
                    + " * Definition for singly-linked list.\n"
                    + " * struct ListNode {\n"
                    + " *     int val;\n"
                    + " *     ListNode *next;\n"
                    + " *     ListNode(int x) : val(x), next(NULL) {}\n"
                    + " * };\n"
                    + " */\n"
                    + "class Solution {\n"
                    + "public:\n"
                    + "    ListNode* addTwoNumbers(ListNode* l1, ListNode* l2) {\n"
                    + "        // TODO: 请在此实现\n"
                    + "        return nullptr;\n"
                    + "    }\n"
                    + "};\n";
            assertTypes(OJSignatureParser.parse(code, "cpp"), "ListNode", "ListNode");
        }
    }

    @Nested
    class Degrades {

        @Test
        void emptyCode() {
            assertTrue(OJSignatureParser.parse("", "python").isEmpty());
            assertTrue(OJSignatureParser.parse(null, "python").isEmpty());
        }

        @Test
        void noClassSolution() {
            String code = "def addTwoNumbers(l1, l2):\n    return None\n";
            assertTrue(OJSignatureParser.parse(code, "python").isEmpty());
        }

        @Test
        void unsupportedLanguage() {
            String code = "/**\n * function twoSum(nums, target) {}\n */\n";
            assertTrue(OJSignatureParser.parse(code, "javascript").isEmpty());
        }

        @Test
        void caseInsensitiveLanguage() {
            String code = "class Solution:\n"
                    + "    def addTwoNumbers(self, l1: Optional[ListNode], l2: Optional[ListNode]) -> Optional[ListNode]:\n"
                    + "        pass\n";
            assertTypes(OJSignatureParser.parse(code, "PYTHON"), "ListNode", "ListNode");
        }
    }
}
