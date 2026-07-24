export default {
  // Field labels (HiddenCasesView / TestCaseForm)
  input: 'Input',
  output: 'Output',
  explanation: 'Explanation',

  // Form / dialog (TestCaseForm)
  editTestCase: 'Edit Test Case',
  createTestCase: 'Create Test Case',
  inputPlaceholder: 'Enter test input...',
  outputPlaceholder: 'Enter expected output...',
  explanationPlaceholder: 'Enter explanation...',

  // Confirm dialog (useTestCases)
  confirmDelete: 'Delete this test case?',

  // Hidden tab list / list-page header (HiddenTestCasesEditor)
  title: 'Test Cases',
  sample: 'Sample',
  hidden: 'Hidden',
  noTestCases: 'No test cases yet',
  add: 'Add',
  addFirst: 'Add first test case',
  import: 'Import',
  export: 'Export',
  importTestCases: 'Import Test Cases',
  importData:
    'Paste a JSON array or use Input:/Output: (or </>) blocks separated by --- / ===',
  // Placeholder mirrors the focused import-normalization module's supported
  // grammars (JSON array or line-oriented Input:/Output: and </> markers).
  // Each block is treated as a HIDDEN case.
  importPlaceholder:
    '< {input}\n> {output}\n---\n< {input 2}\n> {output 2}\n\nor JSON:\n[\n  { "inputText": "...", "outputText": "...", "isSample": true, "isHidden": false }\n]',
  importHelp:
    'JSON accepts camelCase (inputText/outputText) plus legacy snake_case and loose {input, output} pastes. Line grammar always creates HIDDEN cases.',
  replaceExisting: 'Replace existing test cases',
  importing: 'Importing',

  // Tabs (EditCasesView / ViewCasesView)
  tabs: {
    samples: 'Public Samples',
    hidden: 'Hidden Judge Cases',
  },

  // Dropdown labels (TestCaseList)
  markAsSample: 'Mark as Sample',
  markAsHidden: 'Mark as Hidden',
  scope: {
    sample: 'Sample',
    sampleHelp: 'Visible in problem statement; visible to submitter',
    hidden: 'Hidden',
    hiddenHelp: 'Only visible to admin and judge; submitter cannot see',
  },

  // Hidden tab list badges / counts
  count: {
    sample: 'Sample',
    hidden: 'Hidden',
    total: 'Total',
  },

  // Section headers / empty / loading
  section: {
    title: 'Test Cases',
    subtitle: 'Manage samples and hidden judge cases',
    addFirst: 'Add first test case',
  },

  // Form validation
  validation: {
    scopeRequired: 'Please choose a test case scope',
    inputOutputRequired: 'Input and output are required',
    importTextRequired: 'Import content is required',
    noValidTestCases: 'No valid test cases found to import',
  },

  // Confirm / dialog
  confirm: {
    delete: 'Delete this test case?',
  },

  // Toast
  toast: {
    loadFailed: 'Failed to load test cases',
    createSuccess: 'Test case created',
    updateSuccess: 'Test case updated',
    saveFailed: 'Failed to save test case',
    deleteSuccess: 'Test case deleted',
    deleteFailed: 'Failed to delete test case',
    exportSuccess: 'Test cases exported',
    exportFailed: 'Failed to export test cases',
    importing: 'Importing...',
    imported: 'Imported {count} test cases',
    importFailed: 'Failed to import test cases',
    importSuccess: 'Imported {count} test cases',
    updateFailed: 'Failed to update test case',
  },

  // View mode (read-only)
  view: {
    noCases: 'No test cases for this problem',
    hiddenSectionTitle: 'Hidden Judge Cases ({count})',
    hiddenSectionHelp: 'Admin only — never exposed to submitters',
    publicSectionTitle: 'Public Samples ({count})',
  },
  // Read-only detail card heading (TestCaseDetail)
  details: {
    title: 'Test Case Details',
  },
}
