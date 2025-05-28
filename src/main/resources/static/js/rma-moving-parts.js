/**
 * RMA Moving Parts Module
 * Extends the RMA module with moving parts functionality
 */
RMA.movingParts = {
    /**
     * Initialize moving parts functionality
     */
    init() {
        this.movingParts = [];
        this.initializeButtons();
        this.renderTable();
        console.log('Moving parts initialized');
    },

    /**
     * Initialize moving parts buttons
     */
    initializeButtons() {
        const addBtn = document.getElementById('addMovingPartBtn');
        if (addBtn) {
            addBtn.addEventListener('click', () => this.addPart());
        }
    },

    /**
     * Add a new moving part
     */
    addPart() {
        const partName = document.getElementById('movingPartName')?.value;
        const partNumber = document.getElementById('movingPartNumber')?.value;
        const quantity = document.getElementById('movingPartQuantity')?.value || 1;
        const description = document.getElementById('movingPartDescription')?.value;

        if (!partName) {
            RMA.ui.showMessage('Please enter a part name.', 'warning');
            return;
        }

        this.movingParts.push({
            partName,
            partNumber,
            quantity: parseInt(quantity),
            description
        });

        // Clear input fields
        ['movingPartName', 'movingPartNumber', 'movingPartQuantity', 'movingPartDescription'].forEach(id => {
            const element = document.getElementById(id);
            if (element) element.value = '';
        });

        // Update display
        this.renderTable();
        this.createHiddenInputs();
    },

    /**
     * Remove a moving part
     * @param {number} index - Index of part to remove
     */
    removePart(index) {
        this.movingParts.splice(index, 1);
        this.renderTable();
        this.createHiddenInputs();
    },

    /**
     * Render the moving parts table
     */
    renderTable() {
        const table = document.getElementById('movingPartsTable');
        if (!table) return;

        const tbody = table.querySelector('tbody');
        if (!tbody) return;

        tbody.innerHTML = '';

        if (this.movingParts.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="5" class="text-center text-muted">
                        <em>No moving parts added yet</em>
                    </td>
                </tr>
            `;
            return;
        }

        this.movingParts.forEach((part, index) => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${part.partName}</td>
                <td>${part.partNumber || 'N/A'}</td>
                <td>${part.quantity}</td>
                <td>${part.description || 'N/A'}</td>
                <td>
                    <button type="button" class="btn btn-sm btn-outline-danger" onclick="RMA.movingParts.removePart(${index})">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            `;
            tbody.appendChild(row);
        });
    },

    /**
     * Create hidden inputs for form submission
     */
    createHiddenInputs() {
        const container = document.getElementById('movingPartsContainer');
        if (!container) return;

        // Remove existing hidden inputs
        container.querySelectorAll('input[type="hidden"]').forEach(input => input.remove());

        // Create new hidden inputs
        this.movingParts.forEach((part, index) => {
            Object.entries(part).forEach(([key, value]) => {
                const input = document.createElement('input');
                input.type = 'hidden';
                input.name = `movingParts[${index}].${key}`;
                input.value = value;
                container.appendChild(input);
            });
        });
    }
}; 