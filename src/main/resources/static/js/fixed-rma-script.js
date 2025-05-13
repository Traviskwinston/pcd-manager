// Fixed RMA form JavaScript without duplicate declarations
document.addEventListener('DOMContentLoaded', function() {
    console.log("DOM loaded - initializing upload handlers");
    
    // Excel Upload Feature
    const excelFileInput = document.getElementById('excelFileInput');
    const uploadExcelBtn = document.getElementById('uploadExcelBtn');
    const excelUploadResult = document.getElementById('excelUploadResult');
    const excelDropArea = document.getElementById('excel-drop-area');
    
    console.log("Excel elements:", 
                "excelFileInput:", excelFileInput ? "Found" : "Not found", 
                "uploadExcelBtn:", uploadExcelBtn ? "Found" : "Not found",
                "excelDropArea:", excelDropArea ? "Found" : "Not found");
    
    // Add drag and drop functionality for Excel files
    if (excelDropArea) {
        // Prevent default drag behaviors
        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
            excelDropArea.addEventListener(eventName, e => {
                console.log("Excel drag event:", eventName);
                e.preventDefault();
                e.stopPropagation();
            }, false);
        });
        
        // Make the entire drop area clickable
        excelDropArea.addEventListener('click', () => {
            console.log("Excel drop area clicked");
            excelFileInput.click();
        });
    }
    
    // Add drag and drop functionality for regular file uploads
    const dropArea = document.getElementById('drop-area');
    const fileInput = document.getElementById('fileInput');
    const fileUploadsTracker = document.getElementById('fileUploadsTracker');
    
    console.log("File upload elements:", 
              "dropArea:", dropArea ? "Found" : "Not found", 
              "fileInput:", fileInput ? "Found" : "Not found");
    
    // Initialize file upload tracking
    let selectedFiles = [];
    
    if (dropArea && fileInput) {
        // Make the entire drop area clickable
        dropArea.addEventListener('click', () => {
            console.log("File drop area clicked");
            fileInput.click();
        });
        
        // Handle file selection via input
        fileInput.addEventListener('change', function() {
            console.log("File input changed, files selected:", this.files ? this.files.length : 0);
            handleFileSelect(this.files);
        });
    }
    
    if (uploadExcelBtn && excelFileInput && excelUploadResult) {
        // Update displayed filename when selected via file input
        excelFileInput.addEventListener('change', () => {
            console.log("Excel file input changed");
            if (excelFileInput.files && excelFileInput.files[0]) {
                const fileName = excelFileInput.files[0].name;
                console.log("Excel file selected:", fileName);
                if (excelDropArea) {
                    excelDropArea.querySelector('small').innerHTML = `Selected: <span class="text-success">${fileName}</span>`;
                }
            }
        });
        
        uploadExcelBtn.addEventListener('click', function() {
            if (!excelFileInput.files || !excelFileInput.files[0]) {
                showExcelResult('Please select an Excel file first.', 'danger');
                return;
            }
            
            // Show loading state
            uploadExcelBtn.disabled = true;
            uploadExcelBtn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Uploading...';
            showExcelResult('Uploading and parsing Excel file...', 'info');
            
            const formData = new FormData();
            formData.append('file', excelFileInput.files[0]);
            
            console.log("Sending Excel file to server");
            fetch('/rma/uploadExcel', {
                method: 'POST',
                body: formData
            })
            .then(response => {
                console.log("Excel upload response received", response.status);
                return response.json();
            })
            .then(data => {
                // Reset button state
                uploadExcelBtn.disabled = false;
                uploadExcelBtn.innerHTML = '<i class="bi bi-upload me-1"></i> Upload & Parse Excel';
                
                if (data.success) {
                    console.log("Excel data extract successful");
                    showExcelResult('Excel data extracted successfully! Form fields have been populated.', 'success');
                    populateFormWithExcelData(data.data);
                } else {
                    console.error("Excel data extract failed:", data.error);
                    showExcelResult('Error: ' + (data.error || 'Failed to extract data from the Excel file.'), 'danger');
                }
            })
            .catch(error => {
                console.error('Error uploading Excel:', error);
                uploadExcelBtn.disabled = false;
                uploadExcelBtn.innerHTML = '<i class="bi bi-upload me-1"></i> Upload & Parse Excel';
                showExcelResult('Error: ' + error.message, 'danger');
            });
        });
    }

    // Process Impact Information conditional logic
    function setupConditionalFields() {
        // Downtime field logic
        const interruptionFlowRadios = document.querySelectorAll('input[name="interruptionToFlow"]');
        const interruptionProdRadios = document.querySelectorAll('input[name="interruptionToProduction"]');
        const downtimeContainer = document.getElementById('downtimeContainer');
        
        // Exposed to Process Gas fields logic
        const exposedRadios = document.querySelectorAll('input[name="exposedToProcessGasOrChemicals"]');
        const exposedFieldsContainer = document.getElementById('exposedFieldsContainer');
        
        // Function to check if downtime should be visible
        function updateDowntimeVisibility() {
            const flowYes = document.getElementById('interruptionToFlowYes');
            const prodYes = document.getElementById('interruptionToProductionYes');
            
            if ((flowYes && flowYes.checked) || (prodYes && prodYes.checked)) {
                downtimeContainer.style.display = 'block';
                } else {
                downtimeContainer.style.display = 'none';
            }
        }
        
        // Function to check if exposed fields should be visible
        function updateExposedFieldsVisibility() {
            const exposedYes = document.getElementById('exposedYes');
            
            if (exposedYes && exposedYes.checked) {
                exposedFieldsContainer.style.display = 'block';
                } else {
                exposedFieldsContainer.style.display = 'none';
            }
        }
        
        // Add event listeners to interruption radios
        interruptionFlowRadios.forEach(radio => {
            radio.addEventListener('change', updateDowntimeVisibility);
        });
        
        interruptionProdRadios.forEach(radio => {
            radio.addEventListener('change', updateDowntimeVisibility);
        });
        
        // Add event listeners to exposed radios
        exposedRadios.forEach(radio => {
            radio.addEventListener('change', updateExposedFieldsVisibility);
        });
        
        // Initial setup based on current values
        updateDowntimeVisibility();
        updateExposedFieldsVisibility();
    }
    
    // Call setup function once DOM is loaded
    setupConditionalFields();
    
    // Enhanced drag-and-drop support for both file sections
    function enhanceDragDrop() {
        // For Excel file uploads
        if (excelDropArea && excelFileInput) {
            // Highlight drop area when item is dragged over it
            ['dragenter', 'dragover'].forEach(eventName => {
                excelDropArea.addEventListener(eventName, () => {
                    excelDropArea.classList.add('highlight');
                    excelDropArea.classList.add('bg-success-subtle');
                }, false);
            });
            
            // Remove highlight when item is dragged out or dropped
            ['dragleave', 'drop'].forEach(eventName => {
                excelDropArea.addEventListener(eventName, () => {
                    excelDropArea.classList.remove('highlight');
                    excelDropArea.classList.remove('bg-success-subtle');
                }, false);
            });
            
            // Handle dropped files for Excel
            excelDropArea.addEventListener('drop', e => {
                const dt = e.dataTransfer;
                const files = dt.files;
                
                if (files.length > 0) {
                    // Use DataTransfer API to set files on input
                    const dataTransfer = new DataTransfer();
                    dataTransfer.items.add(files[0]);
                    excelFileInput.files = dataTransfer.files;
                    
                    // Show selected file name in drop area
                    const fileName = files[0].name;
                    if (excelDropArea.querySelector('small')) {
                        excelDropArea.querySelector('small').innerHTML = `Selected: <span class="text-success">${fileName}</span>`;
                    }
                    
                    // Automatically trigger upload
                    if (uploadExcelBtn) {
                        uploadExcelBtn.click();
                    }
                }
            }, false);
        }
        
        // For regular file uploads
        if (dropArea && fileInput) {
            // Prevent default drag behaviors
            ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
                dropArea.addEventListener(eventName, e => {
                    e.preventDefault();
                    e.stopPropagation();
                }, false);
            });
            
            // Highlight drop area when item is dragged over it
            ['dragenter', 'dragover'].forEach(eventName => {
                dropArea.addEventListener(eventName, () => {
                    dropArea.classList.add('highlight');
                    dropArea.classList.add('bg-primary-subtle');
                }, false);
            });
            
            // Remove highlight when item is dragged out or dropped
            ['dragleave', 'drop'].forEach(eventName => {
                dropArea.addEventListener(eventName, () => {
                    dropArea.classList.remove('highlight');
                    dropArea.classList.remove('bg-primary-subtle');
                }, false);
            });
            
            // Handle dropped files for regular uploads
            dropArea.addEventListener('drop', e => {
                const dt = e.dataTransfer;
                const files = dt.files;
                
                if (files.length > 0) {
                    // Set the files on the input
                    fileInput.files = files;
                    
                    // Process the files using the existing handleFileSelect function
                    handleFileSelect(files);
                }
            }, false);
        }
    }
    
    enhanceDragDrop();
    
    // Helper function to show upload results
    function showExcelResult(message, type) {
        if (!excelUploadResult) return;
        
        excelUploadResult.textContent = message;
        excelUploadResult.style.display = 'block';
        excelUploadResult.className = 'alert alert-' + type;
        
        // Auto-hide success messages after 8 seconds
        if (type === 'success') {
            setTimeout(() => {
                excelUploadResult.style.display = 'none';
            }, 8000);
        }
    }
    
    // Original file handling functions
    function handleFileSelect(files) {
        if (!files || files.length === 0) return;
        
        // Initialize counters
        let imageCount = 0;
        let documentCount = 0;
        
        // Clear previous lists
        const imageList = document.getElementById('image-list');
        const documentList = document.getElementById('document-list');
        if (imageList) imageList.innerHTML = '';
        if (documentList) documentList.innerHTML = '';
        
        // Store the selected files for form submission
        selectedFiles = Array.from(files);
        
        // Add each file to the appropriate list with delete buttons
        selectedFiles.forEach((file, index) => {
            // Check file type to categorize as image or document
            const isImage = file.type.startsWith('image/');
            
            // Create list item for preview with delete button
            const listItem = document.createElement('li');
            listItem.className = 'd-flex justify-content-between align-items-center mb-1';
            listItem.dataset.fileIndex = index;
            
            const fileNameSpan = document.createElement('span');
            fileNameSpan.textContent = file.name;
            fileNameSpan.className = 'text-truncate me-1';
            fileNameSpan.title = file.name;
            listItem.appendChild(fileNameSpan);
            
            const deleteBtn = document.createElement('button');
            deleteBtn.type = 'button';
            deleteBtn.className = 'btn btn-sm btn-link text-danger p-0';
            deleteBtn.innerHTML = '<i class="bi bi-x-circle"></i>';
            deleteBtn.title = 'Remove file';
            deleteBtn.style.fontSize = '0.7rem';
            deleteBtn.onclick = function() {
                removeFileFromSelection(index);
            };
            listItem.appendChild(deleteBtn);
            
            if (isImage) {
                imageCount++;
                if (imageList) imageList.appendChild(listItem);
            } else {
                documentCount++;
                if (documentList) documentList.appendChild(listItem);
            }
        });
        
        // Update counters
        const imageCountElement = document.getElementById('image-count');
        const documentCountElement = document.getElementById('document-count');
        if (imageCountElement) imageCountElement.textContent = imageCount;
        if (documentCountElement) documentCountElement.textContent = documentCount;
        
        // Update file tracker
        updateFileTracker();
        
        // Show feedback to user
        const smallElement = dropArea.querySelector('small');
        if (smallElement) {
            smallElement.innerHTML = `${selectedFiles.length} files selected`;
        }
    }
    
    // Function to remove a file from the selection
    function removeFileFromSelection(index) {
        // Remove the file from the selectedFiles array
        selectedFiles.splice(index, 1);
        
        // Create a new DataTransfer object
        const dataTransfer = new DataTransfer();
        
        // Add remaining files to the DataTransfer object
        selectedFiles.forEach(file => {
            dataTransfer.items.add(file);
        });
        
        // Update the file input
        fileInput.files = dataTransfer.files;
        
        // Update the UI
        handleFileSelect(dataTransfer.files);
    }
    
    // Function to update the hidden input tracker
    function updateFileTracker() {
        // Just set a value to indicate files have been selected
        if (fileUploadsTracker) {
            fileUploadsTracker.value = 'files-selected';
        }
    }
    
    // Before form submission, ensure files are properly attached
    const form = document.querySelector('form');
    if (form) {
        form.addEventListener('submit', function(e) {
            // No need to prevent default - the multipart form handles file uploads natively
            console.log(`Submitting form with ${selectedFiles.length} files attached`);
        });
    }
    
    // Initialize form elements after DOM is fully loaded
    var toolSelect = document.getElementById('toolSelect');
    var toolChemicalBtn = document.getElementById('btn-add-chemical');
    var toolCommissionBtn = document.getElementById('btn-add-commission');
    var toolStartupBtn = document.getElementById('btn-add-startup');
    
    var chemicalInput = document.getElementById('chemical-gas-service-input');
    var commissionInput = document.getElementById('commission-date-input');
    var startupInput = document.getElementById('startup-sl03-date-input');
    
    var updateChemicalInput = document.getElementById('update-tool-chemical-gas-service');
    var updateCommissionInput = document.getElementById('update-tool-commission-date');
    var updateStartupInput = document.getElementById('update-tool-startup-sl03-date');
    
    var chemicalContainer = document.getElementById('chemical-edit-container');
    var commissionContainer = document.getElementById('commission-edit-container');
    var startupContainer = document.getElementById('startup-edit-container');
    
    // Initialize event listeners for the edit buttons only if elements exist
    if (document.getElementById('btn-cancel-chemical')) {
        document.getElementById('btn-cancel-chemical').addEventListener('click', function() {
            chemicalContainer.classList.add('d-none');
            toolChemicalBtn.classList.remove('d-none');
            updateChemicalInput.value = '';
        });
    }
    
    if (document.getElementById('btn-cancel-commission')) {
        document.getElementById('btn-cancel-commission').addEventListener('click', function() {
            commissionContainer.classList.add('d-none');
            toolCommissionBtn.classList.remove('d-none');
            updateCommissionInput.value = '';
        });
    }
    
    if (document.getElementById('btn-cancel-startup')) {
        document.getElementById('btn-cancel-startup').addEventListener('click', function() {
            startupContainer.classList.add('d-none');
            toolStartupBtn.classList.remove('d-none');
            updateStartupInput.value = '';
        });
    }
    
    if (toolChemicalBtn) {
        toolChemicalBtn.addEventListener('click', function() {
            chemicalContainer.classList.remove('d-none');
            toolChemicalBtn.classList.add('d-none');
            chemicalInput.focus();
        });
    }
    
    if (toolCommissionBtn) {
        toolCommissionBtn.addEventListener('click', function() {
            commissionContainer.classList.remove('d-none');
            toolCommissionBtn.classList.add('d-none');
            commissionInput.focus();
        });
    }
    
    if (toolStartupBtn) {
        toolStartupBtn.addEventListener('click', function() {
            startupContainer.classList.remove('d-none');
            toolStartupBtn.classList.add('d-none');
            startupInput.focus();
        });
    }
    
    // Event listeners for input changes
    if (chemicalInput) {
        chemicalInput.addEventListener('input', function() {
            updateChemicalInput.value = this.value;
        });
    }
    
    if (commissionInput) {
        commissionInput.addEventListener('change', function() {
            updateCommissionInput.value = this.value;
        });
    }
    
    if (startupInput) {
        startupInput.addEventListener('change', function() {
            updateStartupInput.value = this.value;
        });
    }
    
    // NOTE: Tool selection functionality has been moved to rma-form-fix.js
    // to prevent duplication and conflicts.
    
    // Moving Parts functionality
    const movingPartsTableBody = document.getElementById('moving-parts-table-body');
    const addMovingPartBtn = document.getElementById('add-moving-part-btn');
    const saveMovingPartBtn = document.getElementById('saveMovingPartBtn');
    const movingPartNameInput = document.getElementById('movingPartName');
    const fromToolIdSelect = document.getElementById('fromToolId');
    const toToolIdSelect = document.getElementById('toToolId');
    const movingPartNotesInput = document.getElementById('movingPartNotes');
    
    // Initialize moving parts array to store the data
    let movingParts = [];
    
    // Set selected tool as default "to" tool when tool is selected
    if (toolSelect) {
        toolSelect.addEventListener('change', function() {
            const selectedToolId = this.value;
            if (selectedToolId && toToolIdSelect) {
                toToolIdSelect.value = selectedToolId;
            }
        });
        
        // Set initial value if a tool is already selected
        if (toolSelect.value && toToolIdSelect) {
            toToolIdSelect.value = toolSelect.value;
        }
    }
    
    // Function to add a new moving part
    function addMovingPart() {
        const partName = movingPartNameInput.value.trim();
        const fromToolId = fromToolIdSelect.value;
        const toToolId = toToolIdSelect.value;
        const notes = movingPartNotesInput.value.trim();
        
        // Validate inputs
        if (!partName) {
            alert('Please enter a part name');
            return;
        }
        
        if (!fromToolId) {
            alert('Please select a source tool');
            return;
        }
        
        if (!toToolId) {
            alert('Please select a destination tool');
            return;
        }
        
        // Get tool names for display
        const fromToolName = fromToolIdSelect.options[fromToolIdSelect.selectedIndex].text;
        const toToolName = toToolIdSelect.options[toToolIdSelect.selectedIndex].text;
        
        // Create new moving part object
        const newMovingPart = {
            id: Date.now(), // Temporary unique ID
            partName: partName,
            fromToolId: fromToolId,
            fromToolName: fromToolName,
            toToolId: toToolId,
            toToolName: toToolName,
            notes: notes
        };
        
        // Add to array
        movingParts.push(newMovingPart);
        
        // Update the table
        renderMovingPartsTable();
        
        // Create hidden inputs for form submission
        createHiddenMovingPartInputs();
        
        // Reset form and close modal
        movingPartNameInput.value = '';
        movingPartNotesInput.value = '';
        const modal = bootstrap.Modal.getInstance(document.getElementById('addMovingPartModal'));
        modal.hide();
    }
    
    // Function to render the moving parts table
    function renderMovingPartsTable() {
        // Clear the table
        if (!movingPartsTableBody) return;
        
        movingPartsTableBody.innerHTML = '';
        
        if (movingParts.length === 0) {
            // Show empty message
            const emptyRow = document.createElement('tr');
            emptyRow.innerHTML = `<td colspan="5" class="text-center">No moving parts added yet.</td>`;
            movingPartsTableBody.appendChild(emptyRow);
            return;
        }
        
        // Add rows for each moving part
        movingParts.forEach((part, index) => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${part.partName}</td>
                <td>${part.fromToolName}</td>
                <td>${part.toToolName}</td>
                <td>${part.notes || '-'}</td>
                <td>
                    <button type="button" class="btn btn-sm btn-outline-danger remove-moving-part" data-index="${index}">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            `;
            movingPartsTableBody.appendChild(row);
        });
        
        // Add event listeners to remove buttons
        document.querySelectorAll('.remove-moving-part').forEach(btn => {
            btn.addEventListener('click', function() {
                const index = parseInt(this.getAttribute('data-index'));
                removeMovingPart(index);
            });
        });
    }
    
    // Function to remove a moving part
    function removeMovingPart(index) {
        if (confirm('Are you sure you want to remove this moving part?')) {
            movingParts.splice(index, 1);
            renderMovingPartsTable();
            createHiddenMovingPartInputs();
        }
    }
    
    // Function to create hidden inputs for form submission
    function createHiddenMovingPartInputs() {
        // Remove existing hidden inputs
        document.querySelectorAll('.moving-part-hidden-input').forEach(input => input.remove());
        
        // Create form container if it doesn't exist
        let container = document.getElementById('moving-parts-hidden-inputs');
        if (!container) {
            container = document.createElement('div');
            container.id = 'moving-parts-hidden-inputs';
            container.style.display = 'none';
            const formElement = document.querySelector('form');
            if (formElement) {
                formElement.appendChild(container);
            }
        } else {
            container.innerHTML = '';
        }
        
        // Add hidden inputs for each moving part
        movingParts.forEach((part, index) => {
            const nameInput = document.createElement('input');
            nameInput.type = 'hidden';
            nameInput.name = `movingParts[${index}].partName`;
            nameInput.value = part.partName;
            nameInput.className = 'moving-part-hidden-input';
            container.appendChild(nameInput);
            
            const fromInput = document.createElement('input');
            fromInput.type = 'hidden';
            fromInput.name = `movingParts[${index}].fromToolId`;
            fromInput.value = part.fromToolId;
            fromInput.className = 'moving-part-hidden-input';
            container.appendChild(fromInput);
            
            const toInput = document.createElement('input');
            toInput.type = 'hidden';
            toInput.name = `movingParts[${index}].toToolId`;
            toInput.value = part.toToolId;
            toInput.className = 'moving-part-hidden-input';
            container.appendChild(toInput);
            
            const notesInput = document.createElement('input');
            notesInput.type = 'hidden';
            notesInput.name = `movingParts[${index}].notes`;
            notesInput.value = part.notes || '';
            notesInput.className = 'moving-part-hidden-input';
            container.appendChild(notesInput);
        });
    }
    
    // Add event listener to save button
    if (saveMovingPartBtn) {
        saveMovingPartBtn.addEventListener('click', addMovingPart);
    }
    
    // Initial render
    renderMovingPartsTable();
    
    // Part line items handling
    let partIndex = 0;
    const partsContainer = document.getElementById('parts-container');
    
    // Find the highest index of existing part items to ensure new items have unique indices
    if (partsContainer) {
        const existingItems = partsContainer.querySelectorAll('.part-item:not(.template)');
        if (existingItems.length > 0) {
            partIndex = existingItems.length;
        }
        
        // Clone and prepare the template for use
        const template = partsContainer.querySelector('.part-item.template');
        
        // Add event listeners to existing remove buttons
        document.querySelectorAll('.remove-part').forEach(button => {
            button.addEventListener('click', removePartItem);
        });
        
        // When no parts exist initially, add an empty one
        if (existingItems.length === 0 && template) {
            addPartItem();
        }
        
        // Add a global "Add Part" button after the parts container if it doesn't exist already
        if (!document.querySelector('.global-add-part')) {
            const addButton = document.createElement('button');
            addButton.type = 'button';
            addButton.className = 'btn btn-sm btn-primary global-add-part mt-2';
            addButton.innerHTML = '<i class="bi bi-plus-circle"></i> Add Part';
            addButton.addEventListener('click', addPartItem);
            partsContainer.parentNode.insertBefore(addButton, partsContainer.nextSibling);
        }
    }
    
    // Function to add a new part item
    function addPartItem() {
        if (!partsContainer) return;
        
        const template = partsContainer.querySelector('.part-item.template');
        if (!template) return;
        
        // Clone the template
        const newItem = template.cloneNode(true);
        newItem.classList.remove('template', 'd-none');
        
        // Set the HTML directly with the correct index
        const html = `
            <div class="row mb-2">
                <div class="col-md-2">
                    <input type="text" class="form-control form-control-sm" name="partLineItems[${partIndex}].partName" placeholder="Part Name">
                </div>
                <div class="col-md-3">
                    <input type="text" class="form-control form-control-sm" name="partLineItems[${partIndex}].partNumber" placeholder="Part Number">
                </div>
                <div class="col-md-3">
                    <input type="text" class="form-control form-control-sm" name="partLineItems[${partIndex}].productDescription" placeholder="Description" style="width: 100%; box-sizing: border-box;">
                </div>
                <div class="col-md-1">
                    <input type="number" class="form-control form-control-sm" name="partLineItems[${partIndex}].quantity" value="1" min="1" placeholder="Qty" style="min-width: 60px;">
                </div>
                <div class="col-md-2">
                    <div class="form-check form-switch">
                        <input class="form-check-input" type="checkbox" name="partLineItems[${partIndex}].replacementRequired">
                    </div>
                </div>
                <div class="col-md-1">
                    <button type="button" class="btn btn-sm btn-outline-danger remove-part" title="Remove Part">
                        <i class="bi bi-trash"></i>
                    </button>
                </div>
            </div>
        `;
        
        newItem.innerHTML = html;
        
        // Add event listener to the new remove button
        const removeBtn = newItem.querySelector('.remove-part');
        if (removeBtn) {
            removeBtn.addEventListener('click', removePartItem);
        }
        
        // Add the new item to the container
        partsContainer.appendChild(newItem);
        
        // Focus the first input in the new item
        const firstInput = newItem.querySelector('input');
        if (firstInput) firstInput.focus();
        
        // Increment the index for the next item
        partIndex++;
    }
    
    // Function to remove a part item
    function removePartItem(event) {
        const button = event.target.closest('.remove-part');
        if (button) {
            const partItem = button.closest('.part-item');
            if (partItem && !partItem.classList.contains('template')) {
                partItem.remove();
            }
        }
    }
    
    // Handle Export to Excel button
    const exportToExcelBtn = document.getElementById('exportToExcelBtn');
    if (exportToExcelBtn) {
        exportToExcelBtn.addEventListener('click', function() {
            // Always gather form data to capture any unsaved changes
            const formData = new FormData(document.querySelector('form'));
            
            // Generate a descriptive filename
            let filename = 'RMA_Draft.xlsx';
            
            // Try to get RMA number
            const rmaNumber = document.getElementById('rmaNumber')?.value;
            const sapNumber = document.getElementById('sapNotificationNumber')?.value;
            let rmaIdentifier = rmaNumber || sapNumber || 'Draft';
            
            // Try to get tool name
            let toolName = '';
            const toolSelect = document.getElementById('toolSelect');
            if (toolSelect && toolSelect.selectedIndex > 0) {
                toolName = toolSelect.options[toolSelect.selectedIndex].text;
            }
            
            // Try to get first part name/number
            let partIdentifier = '';
            const firstPartName = document.querySelector('input[name^="partLineItems"][name$=".partName"]');
            const firstPartNumber = document.querySelector('input[name^="partLineItems"][name$=".partNumber"]');
            if (firstPartNumber && firstPartNumber.value) {
                partIdentifier = firstPartNumber.value;
            } else if (firstPartName && firstPartName.value) {
                partIdentifier = firstPartName.value;
            }
            
            // Build filename with available information
            if (rmaIdentifier !== 'Draft') {
                filename = rmaIdentifier;
                if (toolName) filename += ' ' + toolName;
                if (partIdentifier) filename += ' ' + partIdentifier;
                filename += '.xlsx';
            }
            
            // Show loading state
            const exportBtn = this;
            const originalText = exportBtn.innerHTML;
            exportBtn.disabled = true;
            exportBtn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Exporting...';
            
            // Send form data to server to generate Excel
            fetch('/rma/export-draft', {
                method: 'POST',
                body: formData
            })
            .then(response => {
                if (!response.ok) {
                    throw new Error('Export failed');
                }
                return response.blob();
            })
            .then(blob => {
                // Create download link
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.style.display = 'none';
                a.href = url;
                a.download = filename;
                document.body.appendChild(a);
                a.click();
                window.URL.revokeObjectURL(url);
                
                // Show success message
                showExcelResult('Export successful! Downloaded with current form values.', 'success');
            })
            .catch(error => {
                console.error('Error exporting to Excel:', error);
                showExcelResult('Error: Failed to export to Excel. ' + error.message, 'danger');
            })
            .finally(() => {
                // Reset button state
                exportBtn.disabled = false;
                exportBtn.innerHTML = originalText;
            });
        });
    }
    
    // Handle RMA number/SAP notification combined field
    function initCombinedField() {
        const rmaField = document.getElementById('rmaNumber');
        const sapField = document.getElementById('sapNotificationNumber');
        const combinedField = document.getElementById('combinedRmaField');
        
        if (rmaField && sapField && combinedField) {
            let combinedValue = '';
            if (rmaField.value) combinedValue += rmaField.value;
            if (rmaField.value && sapField.value) combinedValue += '/';
            if (sapField.value) combinedValue += sapField.value;
            
            combinedField.value = combinedValue;
            
            // Add input listener to combined field
            combinedField.addEventListener('input', function() {
                updateRmaFields(this.value);
            });
        }
    }
    
    // Function to update hidden RMA/SAP fields based on combined input
    function updateRmaFields(combinedValue) {
        const rmaField = document.getElementById('rmaNumber');
        const sapField = document.getElementById('sapNotificationNumber');
        
        // If the combined field is empty, clear both fields
        if (!combinedValue || combinedValue.trim() === '') {
            rmaField.value = '';
            sapField.value = '';
            return;
        }
        
        const parts = combinedValue.split('/');
        
        if (parts.length > 1) {
            // If there's a slash, split the values
            rmaField.value = parts[0].trim();
            sapField.value = parts[1].trim();
        } else {
            // If no slash, assume it's just an RMA number
            rmaField.value = combinedValue.trim();
            sapField.value = '';
        }
    }
    
    // Initialize combined field on page load
    initCombinedField();
    
    // Add submit event listener to the form to ensure RMA/SAP fields are set correctly
    if (form) {
        form.addEventListener('submit', function(event) {
            const rmaField = document.getElementById('rmaNumber');
            const sapField = document.getElementById('sapNotificationNumber');
            const combinedField = document.getElementById('combinedRmaField');
            
            // Get current combined value
            const combinedValue = combinedField.value.trim();
            
            // Ensure values are synchronized before submission
            if (!combinedValue) {
                // If combined field is empty, explicitly clear both fields
                rmaField.value = '';
                sapField.value = '';
            } else {
                // Otherwise parse the value properly
                const parts = combinedValue.split('/');
                
                if (parts.length > 1) {
                    // If there's a slash, split the values
                    rmaField.value = parts[0].trim();
                    sapField.value = parts[1].trim();
                } else {
                    // If no slash, it's just an RMA number - ENSURE SAP is cleared
                    rmaField.value = combinedValue.trim();
                    sapField.value = '';
                }
            }
        });
    }
}); 