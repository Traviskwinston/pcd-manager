/**
 * RMA Excel Module
 * Extends the RMA module with Excel file handling functionality
 */
RMA.excel = {
    /**
     * Initialize Excel upload functionality
     */
    init() {
        const uploadExcelBtn = document.getElementById('uploadExcelBtn');
        const excelFileInput = document.getElementById('excelFileInput');
        
        if (uploadExcelBtn && excelFileInput) {
            // Just open file dialog, nothing else
            uploadExcelBtn.addEventListener('click', () => {
                excelFileInput.click();
            });
            
            // Only handle after file is selected
            excelFileInput.addEventListener('change', (event) => {
                const file = event.target.files[0];
                if (file) {
                    this.processExcelFile(file);
                }
            });
        }
    },

    /**
     * Process the uploaded Excel file
     * @param {File} file - The Excel file to process
     */
    processExcelFile(file) {
        // Create form data
        const formData = new FormData();
        formData.append('file', file);
        
        // Send to server for parsing
        fetch('/rma/parse-excel', {
            method: 'POST',
            body: formData
        })
        .then(response => response.json())
        .then(data => {
            if (data) {
                this.populateForm(data);
                this.showExcelResult('Excel file processed successfully!', 'success');
            }
        })
        .catch(error => {
            console.error('Error processing Excel file:', error);
            this.showExcelResult('Error processing Excel file', 'danger');
        });
    },

    /**
     * Show Excel upload result message
     * @param {string} message - The message to display
     * @param {string} type - The message type (success, danger, warning, info)
     */
    showExcelResult(message, type) {
        const resultDiv = document.getElementById('excelUploadResult');
        if (resultDiv) {
            resultDiv.textContent = message;
            resultDiv.className = `alert alert-${type} mt-2 mb-0`;
            resultDiv.style.display = 'block';

            if (type === 'success') {
                setTimeout(() => {
                    resultDiv.style.display = 'none';
                }, 5000);
            }
        }
    },

    /**
     * Populate form with data from Excel
     * @param {Object} data - Data extracted from Excel file
     */
    populateForm(data) {
        if (!data) return;

        // Store Excel document info for form submission
        window.lastParsedExcelDocument = data;
        document.getElementById('excelFileIncluded')?.setAttribute('value', 'true');

        // Populate basic fields
        this.populateBasicFields(data);

        // Populate part line items
        if (data.parts && Array.isArray(data.parts)) {
            this.populatePartLineItems(data.parts);
        }

        // Populate dates
        this.populateDates(data);

        // Populate tool information
        if (data.toolInfo) {
            this.populateToolInfo(data.toolInfo);
        }

        console.log('Form populated with Excel data');
    },

    /**
     * Populate basic RMA fields
     * @param {Object} data - Excel data
     */
    populateBasicFields(data) {
        const fields = {
            'rmaNumber': data.rmaNumber,
            'serviceOrder': data.serviceOrder,
            'customerName': data.customerName,
            'customerEmail': data.customerEmail,
            'customerPhone': data.customerPhone,
            'reasonForRequest': data.reasonForRequest,
            'dssProductLine': data.dssProductLine,
            'systemDescription': data.systemDescription,
            'notes': data.comments,
            'whatHappened': data.whatHappened,
            'whyAndHowItHappened': data.whyAndHowItHappened,
            'howContained': data.howContained,
            'whoContained': data.whoContained,
            'problemDiscoverer': data.problemDiscoverer,
            'attn': data.attn,
            'companyShipToName': data.companyShipToName,
            'companyShipToAddress': data.companyShipToAddress,
            'city': data.city,
            'state': data.state,
            'zipCode': data.zipCode,
            'instructionsForExposedComponent': data.instructionsForExposedComponent,
            'equipmentInfo': data.equipmentInfo
        };

        Object.entries(fields).forEach(([id, value]) => {
            if (value !== undefined && value !== null) {
                const element = document.getElementById(id);
                if (element) {
                    element.value = value;
                } else {
                    console.warn(`Element with ID '${id}' not found for Excel data population.`);
                }
            }
        });

        const selectFields = {
            'reasonForRequest': data.reasonForRequest,
            'dssProductLine': data.dssProductLine,
            'systemDescription': data.systemDescription
        };

        Object.entries(selectFields).forEach(([id, value]) => {
            if (value) {
                const selectElement = document.getElementById(id);
                if (selectElement && selectElement.tagName === 'SELECT') {
                    let found = false;
                    for (let i = 0; i < selectElement.options.length; i++) {
                        if (selectElement.options[i].value === value || selectElement.options[i].text === value) {
                            selectElement.selectedIndex = i;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        console.warn(`Option for value '${value}' not found in select element '${id}'.`);
                    }
                }
            }
        });

        if (data.exposedToProcessGasOrChemicals !== undefined) {
            const exposedYes = document.getElementById('exposedYes');
            const exposedNo = document.getElementById('exposedNo');
            if (exposedYes && exposedNo) {
                exposedYes.checked = data.exposedToProcessGasOrChemicals === true;
                exposedNo.checked = data.exposedToProcessGasOrChemicals === false;
            }
        }
        if (data.purged !== undefined) {
            const purgedYes = document.getElementById('purgedYes');
            const purgedNo = document.getElementById('purgedNo');
            if (purgedYes && purgedNo) {
                purgedYes.checked = data.purged === true;
                purgedNo.checked = data.purged === false;
            }
        }
    },

    /**
     * Populate part line items
     * @param {Array} parts - Array of part data
     */
    populatePartLineItems(parts) {
        const container = document.getElementById('parts-container');
        if (!container) return;

        // Clear existing parts
        container.innerHTML = '';

        // Add each part
        parts.forEach((part, index) => {
            const partHtml = `
                <div class="part-item mb-2">
                    <div class="row">
                        <div class="col-md-2">
                            <input type="text" class="form-control form-control-sm" name="partLineItems[${index}].partName" 
                                   value="${part.partName || ''}" placeholder="Part Name">
                        </div>
                        <div class="col-md-3">
                            <input type="text" class="form-control form-control-sm" name="partLineItems[${index}].partNumber" 
                                   value="${part.partNumber || ''}" placeholder="Part Number">
                        </div>
                        <div class="col-md-3">
                            <input type="text" class="form-control form-control-sm" name="partLineItems[${index}].productDescription" 
                                   value="${part.description || ''}" placeholder="Description">
                        </div>
                        <div class="col-md-1">
                            <input type="number" class="form-control form-control-sm" name="partLineItems[${index}].quantity" 
                                   value="${part.quantity || 1}" min="1" placeholder="Qty">
                        </div>
                        <div class="col-md-2">
                            <select class="form-select form-select-sm" name="partLineItems[${index}].replacementRequired">
                                <option value="true" ${part.replacementRequired ? 'selected' : ''}>Yes</option>
                                <option value="false" ${!part.replacementRequired ? 'selected' : ''}>No</option>
                            </select>
                        </div>
                        <div class="col-md-1">
                            <button type="button" class="btn btn-sm btn-outline-danger remove-part-btn">
                                <i class="bi bi-trash"></i>
                            </button>
                        </div>
                    </div>
                </div>
            `;
            container.insertAdjacentHTML('beforeend', partHtml);
        });

        // Add event listeners to remove buttons
        container.querySelectorAll('.remove-part-btn').forEach(button => {
            button.addEventListener('click', (e) => {
                e.target.closest('.part-item').remove();
                this.reindexPartItems();
            });
        });
    },

    /**
     * Reindex part items after removal
     */
    reindexPartItems() {
        const container = document.getElementById('parts-container');
        if (!container) return;

        container.querySelectorAll('.part-item').forEach((item, index) => {
            item.querySelectorAll('input, select').forEach(input => {
                const name = input.getAttribute('name');
                if (name) {
                    input.setAttribute('name', name.replace(/\[\d+\]/, `[${index}]`));
                }
            });
        });
    },

    /**
     * Populate dates from Excel data
     * @param {Object} data - Excel data containing dates
     */
    populateDates(data) {
        const dateFields = {
            'writtenDate': data.writtenDate,
            'rmaNumberProvidedDate': data.rmaNumberProvidedDate,
            'shippingMemoEmailedDate': data.shippingMemoEmailedDate,
            'partsReceivedDate': data.partsReceivedDate,
            'installedPartsDate': data.installedPartsDate,
            'failedPartsPackedDate': data.failedPartsPackedDate,
            'failedPartsShippedDate': data.failedPartsShippedDate
        };

        Object.entries(dateFields).forEach(([id, value]) => {
            if (value) {
                const element = document.getElementById(id);
                if (element) {
                    // Convert date to YYYY-MM-DD format
                    const date = new Date(value);
                    const formattedDate = date.toISOString().split('T')[0];
                    element.value = formattedDate;
                }
            }
        });
    },

    /**
     * Populate tool information
     * @param {Object} toolInfo - Tool information from Excel
     */
    populateToolInfo(toolInfo) {
        const toolSelect = document.getElementById('toolSelect');
        if (!toolSelect) return;

        // Try to find matching tool
        const options = Array.from(toolSelect.options);
        const matchingOption = options.find(option => {
            const optionText = option.text.toLowerCase();
            return (
                (toolInfo.name && optionText.includes(toolInfo.name.toLowerCase())) ||
                (toolInfo.serialNumber && optionText.includes(toolInfo.serialNumber.toLowerCase()))
            );
        });

        if (matchingOption) {
            toolSelect.value = matchingOption.value;
            toolSelect.dispatchEvent(new Event('change'));
        }
    }
}; 