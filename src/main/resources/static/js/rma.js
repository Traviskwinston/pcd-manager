/**
 * Main RMA Module
 * Handles core RMA functionality and initialization
 */
const RMA = {
    /**
     * Initialize the RMA module
     */
    init() {
        this.form.init();
        this.fileUpload.init();
        this.excel.init();
        this.tools.init();
        this.initializeTechnician();
    },

    /**
     * Initialize technician selection
     */
    initializeTechnician() {
        const technicianSelect = document.getElementById('technician');
        const currentUserNameField = document.getElementById('currentUserName');
        
        if (technicianSelect && technicianSelect.value === '') {
            let found = false;
            
            // First try to use current user name from server
            if (currentUserNameField && currentUserNameField.value) {
                const currentUserName = currentUserNameField.value;
                
                // Try to find a matching technician
                const options = technicianSelect.options;
                for (let i = 0; i < options.length; i++) {
                    if (options[i].value === currentUserName || 
                        options[i].text === currentUserName ||
                        options[i].text.includes(currentUserName) || 
                        currentUserName.includes(options[i].text)) {
                        technicianSelect.selectedIndex = i;
                        found = true;
                        break;
                    }
                }
            }
            
            // Fallback to first non-empty option if no match found
            if (!found) {
                const options = technicianSelect.options;
                for (let i = 0; i < options.length; i++) {
                    if (options[i].value !== '') {
                        technicianSelect.selectedIndex = i;
                        break;
                    }
                }
            }
        }
    },

    /**
     * Form handling functionality
     */
    form: {
        init() {
            const form = document.querySelector('form');
            if (!form) return;

            // Initialize conditional fields
            this.setupConditionalFields();

            // Initialize Add Part button
            const addPartBtn = document.getElementById('addPartBtn');
            if (addPartBtn) {
                addPartBtn.addEventListener('click', () => RMA.form.addPartLineItem());
            }
            
            // Initialize remove part buttons (using event delegation)
            const partsContainer = document.getElementById('parts-container');
            if (partsContainer) {
                partsContainer.addEventListener('click', (e) => {
                    const removeBtn = e.target.closest('.remove-part');
                    if (removeBtn) {
                        // Don't prevent form submission
                        RMA.form.removePartLineItem(removeBtn);
                    }
                });
            }
        },

        /**
         * Setup conditional form fields
         */
        setupConditionalFields() {
            // Downtime field logic
            const interruptionFlowRadios = document.querySelectorAll('input[name="interruptionToFlow"]');
            const interruptionProdRadios = document.querySelectorAll('input[name="interruptionToProduction"]');
            const downtimeContainer = document.getElementById('downtimeContainer');

            // Exposed to Process Gas fields logic
            const exposedRadios = document.querySelectorAll('input[name="exposedToProcessGasOrChemicals"]');
            const exposedFieldsContainer = document.getElementById('exposedFieldsContainer');

            if (interruptionFlowRadios && interruptionProdRadios) {
                interruptionFlowRadios.forEach(radio => {
                    radio.addEventListener('change', () => this.updateDowntimeVisibility());
                });

                interruptionProdRadios.forEach(radio => {
                    radio.addEventListener('change', () => this.updateDowntimeVisibility());
                });
            }

            if (exposedRadios) {
                exposedRadios.forEach(radio => {
                    radio.addEventListener('change', () => this.updateExposedFieldsVisibility());
                });
            }

            // Initial setup
            this.updateDowntimeVisibility();
            this.updateExposedFieldsVisibility();
        },

        /**
         * Update downtime field visibility
         */
        updateDowntimeVisibility() {
            const flowYes = document.getElementById('interruptionToFlowYes');
            const prodYes = document.getElementById('interruptionToProductionYes');
            const downtimeContainer = document.getElementById('downtimeContainer');

            if (downtimeContainer) {
                downtimeContainer.style.display = 
                    (flowYes?.checked || prodYes?.checked) ? 'block' : 'none';
            }
        },

        /**
         * Update exposed fields visibility
         */
        updateExposedFieldsVisibility() {
            const exposedYes = document.getElementById('exposedYes');
            const exposedFieldsContainer = document.getElementById('exposedFieldsContainer');

            if (exposedFieldsContainer) {
                exposedFieldsContainer.style.display = 
                    exposedYes?.checked ? 'block' : 'none';
            }
        },

        /**
         * Add a new part line item row
         */
        addPartLineItem() {
            const container = document.getElementById('parts-container');
            if (!container) return;

            // Determine next index based on current items
            const items = container.querySelectorAll('.part-item');
            let index = items.length;
            // If last item is template d-none, ignore
            items.forEach(item => {
                if (item.classList.contains('template')) index--; 
            });

            const partHtml = `
                <div class="part-item">
                    <div class="row mb-2">
                        <div class="col-md-2">
                            <input type="text" class="form-control form-control-sm" name="partLineItems[${index}].partName" placeholder="Part Name" />
                        </div>
                        <div class="col-md-3">
                            <input type="text" class="form-control form-control-sm" name="partLineItems[${index}].partNumber" placeholder="Part Number" />
                        </div>
                        <div class="col-md-3">
                            <input type="text" class="form-control form-control-sm" name="partLineItems[${index}].productDescription" placeholder="Item Description" style="width: 100%; box-sizing: border-box;" />
                        </div>
                        <div class="col-md-1">
                            <input type="number" class="form-control form-control-sm" name="partLineItems[${index}].quantity" value="1" min="1" placeholder="Qty" style="min-width: 60px;" />
                        </div>
                        <div class="col-md-2">
                            <div class="form-check form-switch">
                                <input class="form-check-input" type="checkbox" name="partLineItems[${index}].replacementRequired">
                            </div>
                        </div>
                        <div class="col-md-1">
                            <button type="button" class="btn btn-sm btn-outline-danger remove-part" title="Remove Part">
                                <i class="bi bi-trash"></i>
                            </button>
                        </div>
                    </div>
                </div>`;

            container.insertAdjacentHTML('beforeend', partHtml);
        },

        /**
         * Remove a part line item row
         * @param {HTMLElement} btn - The remove button clicked
         */
        removePartLineItem(btn) {
            const partItem = btn.closest('.part-item');
            if (partItem) {
                partItem.remove();
                this.reindexPartLineItems();
            }
        },

        /**
         * Reindex part line items to maintain consistent names
         */
        reindexPartLineItems() {
            const container = document.getElementById('parts-container');
            if (!container) return;
            container.querySelectorAll('.part-item').forEach((item, idx) => {
                item.querySelectorAll('input, select').forEach(input => {
                    const name = input.getAttribute('name');
                    if (name) {
                        input.setAttribute('name', name.replace(/partLineItems\[\d+\]/, `partLineItems[${idx}]`));
                    }
                });
            });
        }
    },

    /**
     * Tool management functionality
     */
    tools: {
        init() {
            const toolSelect = document.getElementById('toolSelect');
            if (!toolSelect) return;

            toolSelect.addEventListener('change', () => this.handleToolSelection());

            // If tool is pre-selected, fetch its details
            if (toolSelect.value) {
                this.handleToolSelection();
            }
        },

        /**
         * Handle tool selection change
         */
        handleToolSelection() {
            const toolSelect = document.getElementById('toolSelect');
            const selectedToolId = toolSelect.value;

            // Show/hide tool details
            const toolDetailsList = document.getElementById('tool-details-list');
            if (toolDetailsList) {
                toolDetailsList.style.display = selectedToolId ? 'block' : 'none';
            }

            if (!selectedToolId) {
                this.resetToolDetails();
                return;
            }

            // Fetch tool details
            fetch(`/rma/api/tool/${selectedToolId}`)
                .then(response => {
                    if (!response.ok) throw new Error('Failed to fetch tool details');
                    return response.json();
                })
                .then(data => this.updateToolDetails(data))
                .catch(error => {
                    console.error('Error fetching tool details:', error);
                    RMA.ui.showMessage('Failed to load tool details. Please try again.', 'error');
                });
        },

        /**
         * Update tool details in UI
         * @param {Object} data - Tool data
         */
        updateToolDetails(data) {
            
            const elements = {
                type: document.getElementById('tool-type'),
                location: document.getElementById('tool-location'),
                serial1: document.getElementById('tool-serial1'),
                serial2: document.getElementById('tool-serial2-display'),
                model1: document.getElementById('tool-model1'),
                model2: document.getElementById('tool-model2-display'),
                chemicalGasService: document.getElementById('tool-chemical-gas-service'),
                commissionDate: document.getElementById('tool-commission-date'),
                startupSl03Date: document.getElementById('tool-startup-sl03-date'),
                viewLink: document.getElementById('tool-view-link')
            };

            // Update each element if it exists
            if (elements.type) elements.type.textContent = data.toolType || 'N/A';
            if (elements.location) elements.location.textContent = data.location?.displayName || 'N/A';
            
            // Handle serial number display with proper formatting
            if (elements.serial1) {
                if (data.serialNumber1) {
                    elements.serial1.textContent = data.serialNumber1;
                    if (elements.serial2) {
                        elements.serial2.textContent = data.serialNumber2 ? '/' + data.serialNumber2 : '';
                    }
                } else {
                    elements.serial1.textContent = 'N/A';
                    if (elements.serial2) elements.serial2.textContent = '';
                }
            }
            // Handle model display with proper formatting
            if (elements.model1) {
                if (data.model1) {
                    elements.model1.textContent = data.model1;
                    if (elements.model2) {
                        elements.model2.textContent = data.model2 ? '/' + data.model2 : '';
                    }
                } else {
                    elements.model1.textContent = 'N/A';
                    if (elements.model2) elements.model2.textContent = '';
                }
            }
            
            // Handle Chemical/Gas Service display - show systemName for GasGuard tools
            if (elements.chemicalGasService) {
                if (data.toolType === 'AMATGASGUARD') {
                    // For GasGuard tools, show System value but keep Chemical/Gas Service label
                    elements.chemicalGasService.textContent = data.systemName || 'N/A';
                } else {
                    // For other tools, show Chemical/Gas Service field
                    elements.chemicalGasService.textContent = data.chemicalGasService || 'N/A';
                }
            }
            
            if (elements.commissionDate) elements.commissionDate.textContent = data.commissionDate || 'N/A';
            if (elements.startupSl03Date) elements.startupSl03Date.textContent = data.startUpSl03Date || 'N/A';
            
            // Update view link
            if (elements.viewLink) {
                elements.viewLink.style.display = 'inline-block';
                elements.viewLink.href = `/tools/${data.id}`;
            }
        },

        /**
         * Reset tool details in UI
         */
        resetToolDetails() {
            const elements = [
                'tool-type', 'tool-location', 'tool-serial1', 'tool-serial2-display',
                'tool-model1', 'tool-model2-display', 'tool-chemical-gas-service',
                'tool-commission-date', 'tool-startup-sl03-date'
            ];

            elements.forEach(id => {
                const element = document.getElementById(id);
                if (element) element.textContent = '';
            });

            const viewLink = document.getElementById('tool-view-link');
            if (viewLink) viewLink.style.display = 'none';
        }
    },

    /**
     * UI utility functions
     */
    ui: {
        /**
         * Show a message to the user
         * @param {string} message - Message to display
         * @param {string} type - Message type (success, error, info, warning)
         */
        showMessage(message, type = 'info') {
            const alertDiv = document.createElement('div');
            alertDiv.className = `alert alert-${type} alert-dismissible fade show`;
            alertDiv.role = 'alert';
            
            alertDiv.innerHTML = `
                ${message}
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            `;

            const container = document.querySelector('.container');
            if (container) {
                container.insertBefore(alertDiv, container.firstChild);
            }

            // Auto-dismiss success messages
            if (type === 'success') {
                setTimeout(() => {
                    alertDiv.remove();
                }, 5000);
            }
        }
    }
}; 