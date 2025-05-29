/**
 * RMA Excel Parser Module
 * Handles parsing and processing of Excel files for RMA forms
 */
RMA.excelParser = {
    /**
     * Parse Excel file data
     * @param {string} data - Binary string data from Excel file
     * @returns {Object} Parsed RMA data
     */
    parse(data) {
        try {
            // For now, return a mock data structure
            // TODO: Implement actual Excel parsing logic
            return {
                rmaNumber: '',
                sapNotificationNumber: '',
                customerName: '',
                customerEmail: '',
                customerPhone: '',
                problemDescription: '',
                notes: '',
                parts: [],
                dates: {},
                toolInfo: {}
            };
        } catch (error) {
            console.error('Error parsing Excel file:', error);
            throw error;
        }
    },

    /**
     * Process form data from Excel
     * @param {Object} data - Parsed Excel data
     */
    processFormData(data) {
        return populateFormWithExcelData(data);
    }
};

// Excel data parsing functionality for the RMA form

// Function to display Excel upload result
function showExcelResult(message, type) {
    const excelUploadResult = document.getElementById('excelUploadResult');
    if (!excelUploadResult) return;
    
    excelUploadResult.textContent = message;
    excelUploadResult.style.display = 'block';
    excelUploadResult.className = 'alert alert-' + type + ' mt-1 mb-0 py-1 px-2';
    
    if (type === 'success') {
        setTimeout(() => {
            excelUploadResult.style.display = 'none';
        }, 8000);
    }
}

function populateFormWithExcelData(data) {
    console.log("Populating form with Excel data", data);
    
    // Helper function to set field value if it exists
    function setFieldValue(fieldId, value) {
        const field = document.getElementById(fieldId);
        if (field && value !== undefined && value !== null) {
            if (field.type === 'checkbox' || field.type === 'radio') {
                field.checked = (value === true || value === 'true' || value === 'yes' || value === 'Yes');
            } else if (field.type === 'select-one') {
                // For select elements, try to find a matching option
                const options = field.options;
                for (let i = 0; i < options.length; i++) {
                    if (options[i].value === value || options[i].text === value) {
                        field.selectedIndex = i;
                        break;
                    }
                }
            } else {
                field.value = value;
            }
        }
    }
    
    // Map the data fields to form field IDs
    if (data) {
        // RMA Numbers/ID
        setFieldValue('rmaNumber', data.rmaNumber);
        setFieldValue('sapNotificationNumber', data.sapNotificationNumber);
        if (data.rmaNumber || data.sapNotificationNumber) {
            let combined = '';
            if (data.rmaNumber) combined += data.rmaNumber;
            if (data.rmaNumber && data.sapNotificationNumber) combined += '/';
            if (data.sapNotificationNumber) combined += data.sapNotificationNumber;
            setFieldValue('combinedRmaField', combined);
        }
        
        // Basic Information
        setFieldValue('serviceOrder', data.serviceOrder);
        setFieldValue('status', data.status);
        setFieldValue('priority', data.priority);
        
        // Tool Information - need to trigger change event
        if (data.toolId) {
            const toolSelect = document.getElementById('toolSelect');
            if (toolSelect) {
                // Important: first set the value
                toolSelect.value = data.toolId;
                
                // Then manually call the updateToolDetails function if it exists globally
                if (typeof updateToolDetails === 'function') {
                    updateToolDetails(data.toolId);
                } else {
                    // Fallback: dispatch a change event
                    const event = new Event('change', { bubbles: true });
                    toolSelect.dispatchEvent(event);
                }
                
                // If the TO field of moving parts exists, set it to match the tool
                const toToolIdSelect = document.getElementById('toToolId');
                if (toToolIdSelect) {
                    toToolIdSelect.value = data.toolId;
                }
            }
        }
        
        // Location and Technician
        setFieldValue('location', data.locationId);
        setFieldValue('technician', data.technician);
        
        // Dates
        setFieldValue('writtenDate', data.writtenDate);
        setFieldValue('rmaNumberProvidedDate', data.rmaNumberProvidedDate);
        setFieldValue('shippingMemoEmailedDate', data.shippingMemoEmailedDate);
        setFieldValue('receivedPartsDate', data.partsReceivedDate);
        setFieldValue('installedPartsDate', data.installedPartsDate);
        setFieldValue('failedPartsPackedDate', data.failedPartsPackedDate);
        setFieldValue('failedPartsShippedDate', data.failedPartsShippedDate);
        setFieldValue('problemDiscoveryDate', data.problemDiscoveryDate);
        
        // Customer Information
        setFieldValue('customerName', data.customerName);
        setFieldValue('companyShipToName', data.companyShipToName);
        setFieldValue('companyShipToAddress', data.companyShipToAddress);
        setFieldValue('city', data.city);
        setFieldValue('state', data.state);
        setFieldValue('zipCode', data.zipCode);
        setFieldValue('attn', data.attn);
        setFieldValue('customerContact', data.customerContact);
        setFieldValue('customerPhone', data.customerPhone);
        setFieldValue('customerEmail', data.customerEmail);
        
        // Selection Fields
        setFieldValue('reasonForRequest', data.reasonForRequest);
        setFieldValue('dssProductLine', data.dssProductLine);
        setFieldValue('systemDescription', data.systemDescription);
        
        // Process Impact Information
        setFieldValue('interruptionToFlowYes', data.interruptionToFlow === true);
        setFieldValue('interruptionToFlowNo', data.interruptionToFlow === false);
        setFieldValue('interruptionToProductionYes', data.interruptionToProduction === true);
        setFieldValue('interruptionToProductionNo', data.interruptionToProduction === false);
        setFieldValue('downtimeHours', data.downtimeHours);
        setFieldValue('exposedYes', data.exposedToProcessGasOrChemicals === true);
        setFieldValue('exposedNo', data.exposedToProcessGasOrChemicals === false);
        setFieldValue('purgedYes', data.purged === true);
        setFieldValue('purgedNo', data.purged === false);
        setFieldValue('instructionsForExposedComponent', data.instructionsForExposedComponent);
        
        // Problem Information
        setFieldValue('problemDiscoverer', data.problemDiscoverer);
        setFieldValue('whatHappened', data.whatHappened);
        setFieldValue('whyAndHowItHappened', data.whyAndHowItHappened);
        setFieldValue('howContained', data.howContained);
        setFieldValue('whoContained', data.whoContained);
        
        // Comments
        setFieldValue('comments', data.comments);
        
        // If part line items exist, populate them
        if (data.partLineItems && data.partLineItems.length > 0) {
            // Use partLineItems from data
            populatePartLineItems(data.partLineItems);
        } else if (data.parts && data.parts.length > 0) {
            // Use parts from data (the server returns parts instead of partLineItems)
            populatePartLineItems(data.parts);
        }
        
        // Show success message
        showExcelResult('Form populated with Excel data successfully!', 'success');
        
        // Trigger conditionals to show/hide fields based on selections
        if (typeof setupConditionalFields === 'function') {
            setupConditionalFields();
        }
    } else {
        showExcelResult('No data received from Excel parsing.', 'warning');
    }
    
    // Helper function to populate part line items
    function populatePartLineItems(parts) {
        // Clear existing parts
        const partsContainer = document.getElementById('parts-container');
        if (partsContainer) {
            const existingItems = partsContainer.querySelectorAll('.part-item:not(.template)');
            existingItems.forEach(item => item.remove());
            
            // Get the template
            const template = partsContainer.querySelector('.part-item.template');
            
            // Add each part
            parts.forEach((part, index) => {
                if (!template) {
                    // If no template exists, create part row directly
                    const newItem = document.createElement('div');
                    newItem.className = 'part-item';
                    
                    // Set the HTML content
                    newItem.innerHTML = `
                        <div class="row mb-2">
                            <div class="col-md-2">
                                <input type="text" class="form-control form-control-sm" name="partLineItems[${index}].partName" value="${part.partName || ''}" placeholder="Part Name">
                            </div>
                            <div class="col-md-3">
                                <input type="text" class="form-control form-control-sm" name="partLineItems[${index}].partNumber" value="${part.partNumber || ''}" placeholder="Part Number">
                            </div>
                            <div class="col-md-3">
                                <input type="text" class="form-control form-control-sm" name="partLineItems[${index}].productDescription" value="${part.productDescription || ''}" placeholder="Description" style="width: 100%; box-sizing: border-box;">
                            </div>
                            <div class="col-md-1">
                                <input type="number" class="form-control form-control-sm" name="partLineItems[${index}].quantity" value="${part.quantity || 1}" min="1" placeholder="Qty" style="min-width: 60px;">
                            </div>
                            <div class="col-md-2">
                                <div class="form-check form-switch">
                                    <input class="form-check-input" type="checkbox" name="partLineItems[${index}].replacementRequired" ${part.replacementRequired ? 'checked' : ''}>
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
                    partsContainer.appendChild(newItem);
                } else {
                    // Clone the template
                    const newItem = template.cloneNode(true);
                    newItem.classList.remove('template', 'd-none');
                    
                    // Set the HTML content with the correct index
                    newItem.innerHTML = `
                        <div class="row mb-2">
                            <div class="col-md-2">
                                <input type="text" class="form-control form-control-sm" name="partLineItems[${index}].partName" value="${part.partName || ''}" placeholder="Part Name">
                            </div>
                            <div class="col-md-3">
                                <input type="text" class="form-control form-control-sm" name="partLineItems[${index}].partNumber" value="${part.partNumber || ''}" placeholder="Part Number">
                            </div>
                            <div class="col-md-3">
                                <input type="text" class="form-control form-control-sm" name="partLineItems[${index}].productDescription" value="${part.productDescription || ''}" placeholder="Description" style="width: 100%; box-sizing: border-box;">
                            </div>
                            <div class="col-md-1">
                                <input type="number" class="form-control form-control-sm" name="partLineItems[${index}].quantity" value="${part.quantity || 1}" min="1" placeholder="Qty" style="min-width: 60px;">
                            </div>
                            <div class="col-md-2">
                                <div class="form-check form-switch">
                                    <input class="form-check-input" type="checkbox" name="partLineItems[${index}].replacementRequired" ${part.replacementRequired ? 'checked' : ''}>
                                </div>
                            </div>
                            <div class="col-md-1">
                                <button type="button" class="btn btn-sm btn-outline-danger remove-part" title="Remove Part">
                                    <i class="bi bi-trash"></i>
                                </button>
                            </div>
                        </div>
                    `;
                    
                    // Add event listener to the new remove button
                    const removeBtn = newItem.querySelector('.remove-part');
                    if (removeBtn) {
                        removeBtn.addEventListener('click', function() {
                            newItem.remove();
                        });
                    }
                    
                    // Add the new item to the container
                    partsContainer.appendChild(newItem);
                }
            });
            
            // Update the global part index to continue from the last part
            if (window.addNewPart) {
                window.partIndex = parts.length;
            }
        }
    }
} 