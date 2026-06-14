export default {
  // Tabs (EditCasesView / ViewCasesView)
  tabs: {
    samples: 'Public Samples',
    hidden: 'Hidden Judge Cases',
  },

  // Per-case scope radio (TestCaseForm)
  scope: {
    sample: 'Public Sample',
    sampleHelp: 'Shown in the problem statement, visible to submitters',
    hidden: 'Hidden Judge Case',
    hiddenHelp: 'Admin/judge only, never exposed to submitters',
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
  },

  // View mode (read-only)
  view: {
    noCases: 'No test cases for this problem',
    hiddenSectionTitle: 'Hidden Judge Cases ({count})',
    hiddenSectionHelp: 'Admin only — never exposed to submitters',
    publicSectionTitle: 'Public Samples ({count})',
  },
}
