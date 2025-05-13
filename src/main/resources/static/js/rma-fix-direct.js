// Direct fix for RMA form JavaScript errors
(function() {
  console.log("Applying RMA form JavaScript error fix...");
  
  // Check if DOM is ready, if not wait for it
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", fixScript);
  } else {
    fixScript();
  }
  
  function fixScript() {
    try {
      // Remove the problematic script elements with duplicate declarations
      const scripts = document.querySelectorAll('script:not([src])');
      let mainScript = null;
      
      for (const script of scripts) {
        const content = script.textContent;
        if (content.includes('const excelFileInput = document.getElementById(\'excelFileInput\');') && 
            content.includes('const uploadExcelBtn = document.getElementById(\'uploadExcelBtn\');')) {
          mainScript = script;
          break;
        }
      }
      
      if (mainScript) {
        // Get the original script content
        let content = mainScript.textContent;
        
        // Remove duplicate declarations section
        content = content.replace(/\/\/ Excel Upload Feature\s+const excelFileInput = document\.getElementById\('excelFileInput'\);\s+const uploadExcelBtn = document\.getElementById\('uploadExcelBtn'\);\s+const excelUploadResult = document\.getElementById\('excelUploadResult'\);\s+const excelDropArea = document\.getElementById\('excel-drop-area'\);[\s\S]*?\/\/ Add drag and drop functionality for Excel files[\s\S]*?\/\/ Handle file selection via input\s+fileInput\.addEventListener\('change', function\(\) \{\s+handleFileSelect\(this\.files\);\s+\}\);/, 
        "// Excel functionality is now handled in external script");
        
        // Create a new script element with the fixed content
        const newScript = document.createElement('script');
        newScript.textContent = content;
        
        // Remove the old script and add the new one
        mainScript.parentNode.replaceChild(newScript, mainScript);
        
        console.log("RMA form JavaScript fixed successfully!");
      } else {
        console.error("Could not find the problematic script element");
      }
    } catch (e) {
      console.error("Error fixing RMA form JavaScript:", e);
    }
  }
})(); 