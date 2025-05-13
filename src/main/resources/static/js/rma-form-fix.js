// This script fixes the RMA form upload functionality
(function() {
    // Initialize when document is ready
    window.addEventListener('DOMContentLoaded', function() {
        console.log("RMA Form Fix: Initializing");
        
        // Tool selection functionality
        const toolSelect = document.getElementById('toolSelect');
        if (toolSelect) {
            console.log("RMA Form Fix: Tool select found, adding change handler");
            
            // Handle tool selection change
            toolSelect.addEventListener('change', function() {
                const selectedToolId = this.value;
                console.log("Tool selected: " + selectedToolId);
                
                // Show/hide tool details based on selection
                const toolDetailsList = document.getElementById('tool-details-list');
                if (toolDetailsList) {
                    if (selectedToolId) {
                        toolDetailsList.style.display = 'block';
                    } else {
                        toolDetailsList.style.display = 'none';
                    }
                }
                
                // If no tool is selected, clear details
                if (!selectedToolId) {
                    resetToolDetails();
                    return;
                }
                
                // Fetch tool details from server
                fetch('/rma/api/tool/' + selectedToolId)
                    .then(response => {
                        if (!response.ok) {
                            throw new Error('Failed to fetch tool details');
                        }
                        return response.json();
                    })
                    .then(data => {
                        updateToolDetailsUI(data);
                    })
                    .catch(error => {
                        console.error('Error fetching tool details:', error);
                        alert('Failed to load tool details. Please try again.');
                    });
            });
            
            // If a tool is already selected, fetch its details
            if (toolSelect.value) {
                console.log("Tool is pre-selected, fetching details: " + toolSelect.value);
                toolSelect.dispatchEvent(new Event('change'));
            }
        }
        
        // Function to reset tool details
        function resetToolDetails() {
            if (document.getElementById('tool-type')) document.getElementById('tool-type').textContent = '';
            if (document.getElementById('tool-location')) document.getElementById('tool-location').textContent = '';
            if (document.getElementById('tool-serial1')) document.getElementById('tool-serial1').textContent = '';
            if (document.getElementById('tool-serial2-display')) document.getElementById('tool-serial2-display').textContent = '';
            if (document.getElementById('tool-model1')) document.getElementById('tool-model1').textContent = '';
            if (document.getElementById('tool-model2-display')) document.getElementById('tool-model2-display').textContent = '';
            if (document.getElementById('tool-chemical-gas-service')) document.getElementById('tool-chemical-gas-service').textContent = '';
            if (document.getElementById('tool-commission-date')) document.getElementById('tool-commission-date').textContent = '';
            if (document.getElementById('tool-startup-sl03-date')) document.getElementById('tool-startup-sl03-date').textContent = '';
            if (document.getElementById('tool-view-link')) document.getElementById('tool-view-link').style.display = 'none';
            
            // Hide edit buttons
            if (document.getElementById('btn-add-chemical')) document.getElementById('btn-add-chemical').classList.add('d-none');
            if (document.getElementById('btn-add-commission')) document.getElementById('btn-add-commission').classList.add('d-none');
            if (document.getElementById('btn-add-startup')) document.getElementById('btn-add-startup').classList.add('d-none');
        }
        
        // Function to update tool details UI
        function updateToolDetailsUI(data) {
            // Update tool type
            if (document.getElementById('tool-type')) {
                document.getElementById('tool-type').textContent = data.toolType || 'N/A';
            }
            
            // Update location
            if (document.getElementById('tool-location')) {
                if (data.location && data.location.displayName) {
                    document.getElementById('tool-location').textContent = data.location.displayName;
                } else {
                    document.getElementById('tool-location').textContent = 'N/A';
                }
            }
            
            // Update serial numbers
            if (document.getElementById('tool-serial1')) {
                document.getElementById('tool-serial1').textContent = data.serialNumber1 || 'N/A';
            }
            
            if (document.getElementById('tool-serial2-display')) {
                if (data.serialNumber2) {
                    document.getElementById('tool-serial2-display').textContent = ' / ' + data.serialNumber2;
                } else {
                    document.getElementById('tool-serial2-display').textContent = '';
                }
            }
            
            // Update model numbers
            if (document.getElementById('tool-model1')) {
                document.getElementById('tool-model1').textContent = data.model1 || 'N/A';
            }
            
            if (document.getElementById('tool-model2-display')) {
                if (data.model2) {
                    document.getElementById('tool-model2-display').textContent = ' / ' + data.model2;
                } else {
                    document.getElementById('tool-model2-display').textContent = '';
                }
            }
            
            // Update chemical/gas service
            if (document.getElementById('tool-chemical-gas-service')) {
                if (data.chemicalGasService) {
                    document.getElementById('tool-chemical-gas-service').textContent = data.chemicalGasService;
                    if (document.getElementById('btn-add-chemical')) {
                        document.getElementById('btn-add-chemical').classList.add('d-none');
                    }
                } else {
                    document.getElementById('tool-chemical-gas-service').textContent = 'N/A';
                    if (document.getElementById('btn-add-chemical')) {
                        document.getElementById('btn-add-chemical').classList.remove('d-none');
                    }
                }
            }
            
            // Update commission date
            if (document.getElementById('tool-commission-date')) {
                if (data.commissionDate) {
                    document.getElementById('tool-commission-date').textContent = formatDate(data.commissionDate);
                    if (document.getElementById('btn-add-commission')) {
                        document.getElementById('btn-add-commission').classList.add('d-none');
                    }
                } else {
                    document.getElementById('tool-commission-date').textContent = 'N/A';
                    if (document.getElementById('btn-add-commission')) {
                        document.getElementById('btn-add-commission').classList.remove('d-none');
                    }
                }
            }
            
            // Update startup/sl03 date
            if (document.getElementById('tool-startup-sl03-date')) {
                if (data.startUpSl03Date) {
                    document.getElementById('tool-startup-sl03-date').textContent = formatDate(data.startUpSl03Date);
                    if (document.getElementById('btn-add-startup')) {
                        document.getElementById('btn-add-startup').classList.add('d-none');
                    }
                } else {
                    document.getElementById('tool-startup-sl03-date').textContent = 'N/A';
                    if (document.getElementById('btn-add-startup')) {
                        document.getElementById('btn-add-startup').classList.remove('d-none');
                    }
                }
            }
            
            // Update view tool link
            if (document.getElementById('tool-view-link')) {
                document.getElementById('tool-view-link').href = '/tools/' + data.id;
                document.getElementById('tool-view-link').style.display = 'inline-block';
            }
            
            // Show moving parts preview if applicable
            const movingPartsPreview = document.getElementById('moving-parts-preview');
            if (movingPartsPreview) {
                if (data.id && document.getElementById('id') && document.getElementById('id').value) {
                    movingPartsPreview.style.display = 'block';
                } else {
                    movingPartsPreview.style.display = 'none';
                }
            }
        }
        
        // Helper function to format dates
        function formatDate(dateString) {
            if (!dateString) return '';
            const date = new Date(dateString);
            return date.toISOString().split('T')[0]; // Format as YYYY-MM-DD
        }
        
        // Fix Excel upload
        const excelFileInput = document.getElementById('excelFileInput');
        const uploadExcelBtn = document.getElementById('uploadExcelBtn');
        const excelDropArea = document.getElementById('excel-drop-area');
        
        if (excelDropArea && excelFileInput && uploadExcelBtn) {
            console.log("RMA Form Fix: Excel elements found");
            
            // Make sure the drop area activates the file input
            excelDropArea.addEventListener('click', function() {
                excelFileInput.click();
            });
            
            // Handle drag and drop
            excelDropArea.addEventListener('dragover', function(e) {
                e.preventDefault();
                e.stopPropagation();
                this.classList.add('border-success');
            });
            
            excelDropArea.addEventListener('dragleave', function(e) {
                e.preventDefault();
                e.stopPropagation();
                this.classList.remove('border-success');
            });
            
            excelDropArea.addEventListener('drop', function(e) {
                e.preventDefault();
                e.stopPropagation();
                this.classList.remove('border-success');
                
                if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
                    // Use DataTransfer API to set files on input
                    const dt = new DataTransfer();
                    dt.items.add(e.dataTransfer.files[0]);
                    excelFileInput.files = dt.files;
                    
                    // Trigger upload
                    uploadExcelBtn.click();
                }
            });
        }
        
        // Fix regular file upload
        const dropArea = document.getElementById('drop-area');
        const fileInput = document.getElementById('fileInput');
        
        if (dropArea && fileInput) {
            console.log("RMA Form Fix: File upload elements found");
            
            // Make sure the drop area activates the file input
            dropArea.addEventListener('click', function() {
                fileInput.click();
            });
            
            // Handle drag and drop
            dropArea.addEventListener('dragover', function(e) {
                e.preventDefault();
                e.stopPropagation();
                this.classList.add('border-primary');
            });
            
            dropArea.addEventListener('dragleave', function(e) {
                e.preventDefault();
                e.stopPropagation();
                this.classList.remove('border-primary');
            });
            
            dropArea.addEventListener('drop', function(e) {
                e.preventDefault();
                e.stopPropagation();
                this.classList.remove('border-primary');
                
                if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
                    // Use DataTransfer API to set files on input
                    fileInput.files = e.dataTransfer.files;
                    
                    // Trigger change event to update UI
                    const event = new Event('change', { bubbles: true });
                    fileInput.dispatchEvent(event);
                }
            });
        }

        // Initialize all remove part buttons in the form
        const removePartButtons = document.querySelectorAll('.remove-part');
        removePartButtons.forEach(button => {
            button.addEventListener('click', function() {
                const partItem = this.closest('.part-item');
                if (partItem) {
                    partItem.remove();
                }
            });
        });

        // Check if we need to add an initial part
        const partsContainer = document.getElementById('parts-container');
        if (partsContainer) {
            const partItems = partsContainer.querySelectorAll('.part-item:not(.template)');
            if (partItems.length === 0) {
                // No parts are showing - manually create one
                console.log("RMA Form Fix: Adding initial part row");
                window.addNewPart();
            }
        }
        
        // Set default technician if not already set
        const technicianSelect = document.getElementById('technician');
        if (technicianSelect && technicianSelect.value === '') {
            console.log("RMA Form Fix: Setting default technician");
            
            // Try to find the current user's name from login info
            const currentUserName = getCurrentUserName();
            if (currentUserName) {
                // Look for the option with this name
                const options = technicianSelect.options;
                for (let i = 0; i < options.length; i++) {
                    if (options[i].text === currentUserName || 
                        options[i].text.includes(currentUserName) || 
                        currentUserName.includes(options[i].text)) {
                        technicianSelect.selectedIndex = i;
                        console.log("RMA Form Fix: Set technician to " + options[i].text);
                        break;
                    }
                }
            } else {
                // If we can't find the current user name, default to first non-empty option
                const options = technicianSelect.options;
                for (let i = 0; i < options.length; i++) {
                    if (options[i].value !== '') {
                        technicianSelect.selectedIndex = i;
                        console.log("RMA Form Fix: Set technician to first available: " + options[i].text);
                        break;
                    }
                }
            }
        }
        
        // Function to try to determine current user name
        function getCurrentUserName() {
            // In this application, we need to find the user's name from available page elements

            // First try to find the user info in the profile dropdown
            const profileLink = document.querySelector('a[href="/profile"]');
            if (profileLink) {
                // Assume the username might be nearby in the dropdown
                const parent = profileLink.closest('.dropdown');
                if (parent) {
                    const userText = parent.querySelector('.user-display-name, .username, .user-name');
                    if (userText && userText.textContent.trim()) {
                        return userText.textContent.trim();
                    }
                }
            }
            
            // Try to find a username in a welcome message or header
            const welcomeElements = document.querySelectorAll('.welcome-message, .user-greeting, .user-header');
            for (const element of welcomeElements) {
                const text = element.textContent.trim();
                if (text.includes('Welcome') || text.includes('Hello')) {
                    // Extract name from welcome message (after "Welcome" or "Hello")
                    const match = text.match(/(?:Welcome|Hello),?\s+([^!.]+)/i);
                    if (match && match[1]) {
                        return match[1].trim();
                    }
                }
            }

            // Try checking for any element with common user-related class names
            const userElements = document.querySelectorAll('.user-name, .username, .user-info, .user-display, .current-user');
            for (const element of userElements) {
                if (element.textContent && element.textContent.trim()) {
                    return element.textContent.trim();
                }
            }
            
            // Try to extract from page <title> if it includes the username
            const title = document.title;
            if (title && title.includes(' - ')) {
                const parts = title.split(' - ');
                if (parts.length > 1 && parts[parts.length - 1].trim()) {
                    return parts[parts.length - 1].trim();
                }
            }
            
            // Check for HTML5 data attributes on body or other main elements
            const dataUserElements = document.querySelectorAll('[data-user], [data-username], [data-user-name]');
            for (const element of dataUserElements) {
                const userData = element.getAttribute('data-user') || 
                               element.getAttribute('data-username') || 
                               element.getAttribute('data-user-name');
                if (userData) {
                    return userData;
                }
            }
            
            // Last resort: try to find a select option that's not "Unassigned" and looks like a name
            const techSelect = document.getElementById('technician');
            if (techSelect) {
                for (let i = 0; i < techSelect.options.length; i++) {
                    const option = techSelect.options[i];
                    if (option.value && option.text && option.text !== '-- Unassigned --') {
                        // Find the first option that has the text structure of "Firstname Lastname" 
                        // (two words with the first letter capitalized)
                        const namePattern = /^[A-Z][a-z]+ [A-Z][a-z]+$/;
                        if (namePattern.test(option.text.trim())) {
                            return option.text.trim();
                        }
                    }
                }
            }
            
            // Can't determine current user
            return null;
        }
    });
})();

// This file contains the fixed JavaScript code for the RMA form
document.addEventListener('DOMContentLoaded', function() {
    // Tool selection element
    const toolSelect = document.getElementById('toolSelect');
    
    // Initialize form elements - called at the end of this file
    function initCombinedField() {
        const rmaField = document.getElementById('rmaNumber');
        const sapField = document.getElementById('sapNotificationNumber');
        const combinedField = document.getElementById('combinedRmaField');
        
        if (!combinedField || !rmaField || !sapField) return;
        
        // Set up the initial value - if RMA or SAP have values, combine them
        if (rmaField.value || sapField.value) {
            let combined = '';
            if (rmaField.value) combined += rmaField.value;
            if (rmaField.value && sapField.value) combined += '/';
            if (sapField.value) combined += sapField.value;
            combinedField.value = combined;
        }
        
        // Add change event listener to the combined field
        combinedField.addEventListener('change', function() {
            const combinedValue = this.value.trim();
            
            if (!combinedValue) {
                // If combined field is empty, explicitly clear both fields
                rmaField.value = '';
                sapField.value = '';
            } else {
                // If there's a value, try to parse it
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
        });
    }
    
    // Initialize combined field on page load
    initCombinedField();
    
    // Add submit event listener to the form to ensure RMA/SAP fields are set correctly
    const form = document.querySelector('form');
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
    
    // Parts Management
    const partsContainer = document.getElementById('parts-container');
    if (partsContainer) {
        // Look for the existing HTML button with id="addPartBtn"
        const existingButton = document.getElementById('addPartBtn');
        
        // Only add a button if one doesn't exist with either ID or class
        if (!existingButton && !document.querySelector('.add-part-btn')) {
            const buttonRow = document.createElement('div');
            buttonRow.className = 'text-end mt-2';
            
            const addButton = document.createElement('button');
            addButton.type = 'button';
            addButton.className = 'btn btn-sm btn-primary add-part-btn';
            addButton.innerHTML = '<i class="bi bi-plus-circle me-1"></i> Add Part';
            addButton.addEventListener('click', addNewPart);
            
            buttonRow.appendChild(addButton);
            partsContainer.parentNode.appendChild(buttonRow);
        }
        
        // Make sure the existing button has a click event handler
        if (existingButton) {
            // Remove the inline onclick attribute if it exists and add proper event listener
            if (existingButton.hasAttribute('onclick')) {
                const onclickValue = existingButton.getAttribute('onclick');
                existingButton.removeAttribute('onclick');
                existingButton.addEventListener('click', addNewPart);
            }
        }
        
        // Add event listeners to existing remove buttons
        const removeButtons = document.querySelectorAll('.remove-part');
        removeButtons.forEach(btn => {
            btn.addEventListener('click', function() {
                const partItem = this.closest('.part-item');
                if (partItem) {
                    partItem.remove();
                }
            });
        });
        
        // Initialize the template if needed
        let template = partsContainer.querySelector('.part-item.template');
        if (!template) {
            template = document.createElement('div');
            template.className = 'part-item template d-none';
            template.dataset.index = '__INDEX__';
            partsContainer.appendChild(template);
        }
    }
    
    // Function to add a new part
    function addNewPart() {
        const template = document.querySelector('.part-item.template');
        const container = document.getElementById('parts-container');
        
        if (!template || !container) return;
        
        // Get the next index
        const items = container.querySelectorAll('.part-item:not(.template)');
        const nextIndex = items.length;
        
        // Clone the template
        const newItem = document.createElement('div');
        newItem.className = 'part-item';
        
        // Set the HTML content
        newItem.innerHTML = `
            <div class="row mb-2">
                <div class="col-md-2">
                    <input type="text" class="form-control form-control-sm" name="partLineItems[${nextIndex}].partName" placeholder="Part Name">
                </div>
                <div class="col-md-3">
                    <input type="text" class="form-control form-control-sm" name="partLineItems[${nextIndex}].partNumber" placeholder="Part Number">
                </div>
                <div class="col-md-3">
                    <input type="text" class="form-control form-control-sm" name="partLineItems[${nextIndex}].productDescription" placeholder="Description" style="width: 100%; box-sizing: border-box;">
                </div>
                <div class="col-md-1">
                    <input type="number" class="form-control form-control-sm" name="partLineItems[${nextIndex}].quantity" value="1" min="1" placeholder="Qty" style="min-width: 60px;">
                </div>
                <div class="col-md-2">
                    <div class="form-check form-switch">
                        <input class="form-check-input" type="checkbox" name="partLineItems[${nextIndex}].replacementRequired">
                    </div>
                </div>
                <div class="col-md-1">
                    <button type="button" class="btn btn-sm btn-outline-danger remove-part" title="Remove Part">
                        <i class="bi bi-trash"></i>
                    </button>
                </div>
            </div>
        `;
        
        // Add click handler for the remove button
        const removeBtn = newItem.querySelector('.remove-part');
        if (removeBtn) {
            removeBtn.addEventListener('click', function() {
                newItem.remove();
            });
        }
        
        // Add to the container
        container.appendChild(newItem);
    }
    
    // Make the function available globally
    window.addNewPart = addNewPart;
}); 