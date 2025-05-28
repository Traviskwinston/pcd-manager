/**
 * RMA Excel Module
 * Extends the RMA module with Excel file handling functionality
 */
RMA.excel = {
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
            'sapNotificationNumber': data.sapNumber,
            'customerName': data.customerName,
            'customerEmail': data.customerEmail,
            'customerPhone': data.customerPhone,
            'problemDescription': data.problemDescription,
            'notes': data.notes
        };

        Object.entries(fields).forEach(([id, value]) => {
            if (value) {
                const element = document.getElementById(id);
                if (element) {
                    element.value = value;
                }
            }
        });
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
                <div class="part-item mb-3">
                    <div class="row">
                        <div class="col-md-2">
                            <label class="form-label">Part Name</label>
                            <input type="text" class="form-control" name="partLineItems[${index}].partName" 
                                   value="${part.partName || ''}" required>
                        </div>
                        <div class="col-md-3">
                            <label class="form-label">Part #</label>
                            <input type="text" class="form-control" name="partLineItems[${index}].partNumber" 
                                   value="${part.partNumber || ''}">
                        </div>
                        <div class="col-md-3">
                            <label class="form-label">Description</label>
                            <input type="text" class="form-control" name="partLineItems[${index}].productDescription" 
                                   value="${part.description || ''}">
                        </div>
                        <div class="col-md-1">
                            <label class="form-label">Qty</label>
                            <input type="number" class="form-control" name="partLineItems[${index}].quantity" 
                                   value="${part.quantity || 1}" min="1">
                        </div>
                        <div class="col-md-2">
                            <label class="form-label">Replacement</label>
                            <select class="form-select" name="partLineItems[${index}].replacementRequired">
                                <option value="true" ${part.replacementRequired ? 'selected' : ''}>Yes</option>
                                <option value="false" ${!part.replacementRequired ? 'selected' : ''}>No</option>
                            </select>
                        </div>
                        <div class="col-md-1 d-flex align-items-end">
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