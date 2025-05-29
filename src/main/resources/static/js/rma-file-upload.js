/**
 * RMA File Upload Module
 * Extends the RMA module with file upload functionality
 */
RMA.fileUpload = {
    /**
     * Initialize file upload functionality
     */
    init() {
        // Disabled - using simple file inputs instead
        // this.selectedFiles = [];
        // this.initializeDropAreas();
        // this.initializeFileInputs();
        
        // Only initialize Excel upload
        const excelInput = document.getElementById('excelFileInput');
        const uploadExcelBtn = document.getElementById('uploadExcelBtn');
        if (excelInput && uploadExcelBtn) {
            this.initializeExcelUpload(excelInput, uploadExcelBtn);
        }
    },

    /**
     * Initialize drag and drop areas
     */
    initializeDropAreas() {
        // Regular file drop area
        const dropArea = document.getElementById('drop-area');
        if (dropArea) {
            this.setupDropArea(dropArea, 'fileInput');
        }

        // Excel drop area
        const excelDropArea = document.getElementById('excel-drop-area');
        if (excelDropArea) {
            this.setupDropArea(excelDropArea, 'excelFileInput');
        }
    },

    /**
     * Initialize file input elements
     */
    initializeFileInputs() {
        // Regular file input
        const fileInput = document.getElementById('fileInput');
        if (fileInput) {
            fileInput.addEventListener('change', (e) => {
                this.handleFileSelect(e.target.files);
            });
        }

        // Excel file input
        const excelInput = document.getElementById('excelFileInput');
        const uploadExcelBtn = document.getElementById('uploadExcelBtn');
        if (excelInput && uploadExcelBtn) {
            this.initializeExcelUpload(excelInput, uploadExcelBtn);
        }
    },

    /**
     * Setup a drop area for files
     * @param {HTMLElement} dropArea - The drop area element
     * @param {string} inputId - ID of the associated file input
     */
    setupDropArea(dropArea, inputId) {
        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
            dropArea.addEventListener(eventName, (e) => {
                e.preventDefault();
                e.stopPropagation();
            }, false);
        });

        ['dragenter', 'dragover'].forEach(eventName => {
            dropArea.addEventListener(eventName, () => {
                dropArea.classList.add('highlight', 'bg-success-subtle');
            }, false);
        });

        ['dragleave', 'drop'].forEach(eventName => {
            dropArea.addEventListener(eventName, () => {
                dropArea.classList.remove('highlight', 'bg-success-subtle');
            }, false);
        });

        // Handle dropped files
        dropArea.addEventListener('drop', (e) => {
            const files = e.dataTransfer.files;
            const input = document.getElementById(inputId);
            
            if (input && files.length > 0) {
                if (inputId === 'excelFileInput') {
                    // Handle Excel file drop
                    input.files = files;
                    input.dispatchEvent(new Event('change'));
                } else {
                    // Handle regular file drop
                    this.handleFileSelect(files);
                }
            }
        });

        // Make drop area clickable
        dropArea.addEventListener('click', () => {
            document.getElementById(inputId)?.click();
        });
    },

    /**
     * Handle file selection
     * @param {FileList} files - Selected files
     */
    handleFileSelect(files) {
        if (!files || files.length === 0) return;

        // Clear existing preview lists and counters
        const imageList = document.getElementById('image-list');
        const docList = document.getElementById('document-list');
        const imageCountSpan = document.getElementById('image-count');
        const docCountSpan = document.getElementById('document-count');
        if (imageList) imageList.innerHTML = '';
        if (docList) docList.innerHTML = '';
        if (imageCountSpan) imageCountSpan.textContent = '0';
        if (docCountSpan) docCountSpan.textContent = '0';

        let imageCount = 0;
        let docCount = 0;

        // Update UI with selected files
        this.selectedFiles = Array.from(files);
        this.selectedFiles.forEach((file, index) => {
            const isImage = file.type.startsWith('image/');

            // Create list item
            const li = document.createElement('li');
            li.className = 'd-flex justify-content-between align-items-center';
            li.innerHTML = `
                <span class="text-truncate" style="max-width: 200px;">${file.name}</span>
                <button type="button" class="btn btn-link text-danger p-0 ms-2" style="font-size: 0.8rem;" onclick="RMA.fileUpload.removeFile(${index})">
                    <i class="bi bi-x"></i>
                </button>
            `;

            if (isImage) {
                imageList?.appendChild(li);
                imageCount++;
            } else {
                docList?.appendChild(li);
                docCount++;
            }
        });

        if (imageCountSpan) imageCountSpan.textContent = String(imageCount);
        if (docCountSpan) docCountSpan.textContent = String(docCount);

        // Update tracker value for simple flagging (optional)
        const tracker = document.getElementById('fileUploadsTracker');
        if (tracker) tracker.value = this.selectedFiles.length ? 'files-selected' : '';
    },

    /**
     * Remove a file from selection
     * @param {number} index - Index of file to remove
     */
    removeFile(index) {
        if (!this.selectedFiles) return;

        this.selectedFiles.splice(index, 1);
        this.handleFileSelect(this.selectedFiles);
    },

    /**
     * Initialize Excel file upload
     * @param {HTMLElement} input - Excel file input element
     * @param {HTMLElement} button - Upload button element
     */
    initializeExcelUpload(input, button) {
        // Update filename when selected
        input.addEventListener('change', () => {
            if (input.files && input.files[0]) {
                const fileName = input.files[0].name;
                const dropArea = document.getElementById('excel-drop-area');
                if (dropArea) {
                    const small = dropArea.querySelector('small');
                    if (small) {
                        small.innerHTML = `Selected: <span class="text-success">${fileName}</span>`;
                    }
                }
            }
        });

        // Handle Excel upload
        button.addEventListener('click', () => {
            if (!input.files || !input.files[0]) {
                RMA.ui.showMessage('Please select an Excel file first.', 'warning');
                return;
            }

            // Show loading state
            button.disabled = true;
            button.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Uploading...';

            const formData = new FormData();
            formData.append('file', input.files[0]);

            fetch('/rma/parse-excel', {
                method: 'POST',
                body: formData
            })
            .then(response => response.json())
            .then(data => {
                button.disabled = false;
                button.innerHTML = '<i class="bi bi-upload me-1"></i> Upload & Parse Excel';

                if (!data.error) {
                    RMA.ui.showMessage('Excel data extracted successfully! Form fields have been populated.', 'success');
                    RMA.excel.populateForm(data.data || data);
                } else {
                    throw new Error(data.error || 'Failed to extract data from the Excel file.');
                }
            })
            .catch(error => {
                console.error('Error uploading Excel:', error);
                button.disabled = false;
                button.innerHTML = '<i class="bi bi-upload me-1"></i> Upload & Parse Excel';
                RMA.ui.showMessage(error.message, 'error');
            });
        });
    },

    /**
     * Append files to form data for submission
     * @param {FormData} formData - Form data object
     */
    appendFilesToFormData(formData) {
        // Add regular files
        if (this.selectedFiles) {
            this.selectedFiles.forEach(file => {
                if (file.type.startsWith('image/')) {
                    formData.append('imageUploads', file, file.name);
                } else {
                    formData.append('documentUploads', file, file.name);
                }
            });
        }

        // Add Excel file if present
        const excelInput = document.getElementById('excelFileInput');
        if (excelInput?.files[0]) {
            formData.append('documentUploads', excelInput.files[0], excelInput.files[0].name);
        }
    }
}; 