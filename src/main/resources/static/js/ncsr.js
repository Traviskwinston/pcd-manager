/**
 * NCSR (Non-Conformance Scope) Management JavaScript
 * Handles NCSR modal, part list, form, and bulk upload functionality
 */

let currentToolId = null;
let ncsrParts = [];
let isEditingNcsr = false;
let ncsrDragScrollEnabled = false;

/**
 * Initialize NCSR modal for a specific tool
 */
function openNcsrModal(toolId, toolName) {
    currentToolId = toolId;
    document.getElementById('ncsrToolName').textContent = toolName;
    
    // Show modal
    const modal = new bootstrap.Modal(document.getElementById('ncsrModal'));
    modal.show();
    
    // Load NCSR parts
    loadNcsrParts(toolId);
    
    // Initialize drag-scroll
    initializeNcsrDragScroll();
}

/**
 * Load NCSR parts for a tool
 */
async function loadNcsrParts(toolId) {
    showNcsrLoadingState();
    
    try {
        const response = await fetch(`/api/ncsr/tool/${toolId}`);
        if (!response.ok) throw new Error('Failed to load NCSR parts');
        
        ncsrParts = await response.json();
        
        if (ncsrParts.length === 0) {
            showNcsrEmptyState();
        } else {
            renderNcsrParts();
        }
    } catch (error) {
        console.error('Error loading NCSR parts:', error);
        alert('Failed to load NCSR parts. Please try again.');
        showNcsrEmptyState();
    }
}

/**
 * Render NCSR parts list
 */
function renderNcsrParts() {
    const container = document.getElementById('ncsrPartsList');
    container.innerHTML = '';
    
    ncsrParts.forEach(part => {
        const partElement = createNcsrPartElement(part);
        container.appendChild(partElement);
    });
    
    showNcsrListContainer();
}

/**
 * Create HTML element for NCSR part
 */
function createNcsrPartElement(part) {
    const div = document.createElement('div');
    div.className = `ncsr-part-item ${part.installed ? 'installed' : ''}`;
    div.onclick = () => editNcsrPart(part);
    
    const installedBadge = part.installed 
        ? '<span class="badge bg-success installed-badge"><i class="bi bi-check-circle me-1"></i>Installed</span>'
        : '<span class="badge bg-warning text-dark installed-badge"><i class="bi bi-clock me-1"></i>Open</span>';
    
    const installDate = part.installDate 
        ? new Date(part.installDate).toLocaleDateString('en-US', { month: '2-digit', day: '2-digit', year: '2-digit' })
        : '-';
    
    div.innerHTML = `
        <div class="d-flex align-items-start justify-content-between mb-2">
            <div class="d-flex align-items-center gap-2">
                <input type="checkbox" class="form-check-input" 
                       ${part.installed ? 'checked' : ''} 
                       onclick="event.stopPropagation(); toggleNcsrInstalled(${part.id}, this.checked)"
                       title="Toggle Installed Status">
                <strong>${part.partLocationId || 'No Location'}</strong>
            </div>
            ${installedBadge}
        </div>
        <div class="row g-2 small">
            <div class="col-6">
                <strong>Component:</strong> ${part.component || '-'}
            </div>
            <div class="col-6">
                <strong>Mfg:</strong> ${part.discrepantPartMfg || '-'}
            </div>
            <div class="col-6">
                <strong>Part #:</strong> ${part.discrepantPartNumber || '-'}
            </div>
            <div class="col-6">
                <strong>Install Date:</strong> ${installDate}
            </div>
            <div class="col-12">
                <strong>Equipment #:</strong> ${part.equipmentNumber || 'Not specified'}
            </div>
        </div>
        <div class="mt-2 d-flex justify-content-end gap-2">
            <button class="btn btn-sm btn-outline-primary" onclick="event.stopPropagation(); editNcsrPart(${JSON.stringify(part).replace(/"/g, '&quot;')})">
                <i class="bi bi-pencil"></i> Edit
            </button>
            <button class="btn btn-sm btn-outline-danger" onclick="event.stopPropagation(); deleteNcsrPart(${part.id})">
                <i class="bi bi-trash"></i> Delete
            </button>
        </div>
    `;
    
    return div;
}

/**
 * Toggle installed status
 */
async function toggleNcsrInstalled(ncsrId, installed) {
    try {
        const response = await fetch(`/api/ncsr/${ncsrId}/toggle-installed`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });
        
        if (!response.ok) throw new Error('Failed to toggle installed status');
        
        // Reload parts to reflect changes
        await loadNcsrParts(currentToolId);
    } catch (error) {
        console.error('Error toggling installed status:', error);
        alert('Failed to update installed status. Please try again.');
        // Reload to reset state
        await loadNcsrParts(currentToolId);
    }
}

/**
 * Show add NCSR form
 */
function showAddNcsrForm() {
    isEditingNcsr = false;
    document.getElementById('ncsrForm').reset();
    document.getElementById('ncsrFormId').value = '';
    document.getElementById('ncsrFormToolId').value = currentToolId;
    
    hideAllNcsrStates();
    document.getElementById('ncsrFormContainer').style.display = 'block';
}

/**
 * Edit NCSR part
 */
function editNcsrPart(part) {
    isEditingNcsr = true;
    
    // Populate form
    document.getElementById('ncsrFormId').value = part.id || '';
    document.getElementById('ncsrFormToolId').value = currentToolId;
    document.getElementById('ncsrEquipmentNumber').value = part.equipmentNumber || '';
    document.getElementById('ncsrSerialNumber').value = part.serialNumber || '';
    document.getElementById('ncsrComponent').value = part.component || '';
    document.getElementById('ncsrPartLocationId').value = part.partLocationId || '';
    document.getElementById('ncsrDiscrepantPartMfg').value = part.discrepantPartMfg || '';
    document.getElementById('ncsrDiscrepantPartNumber').value = part.discrepantPartNumber || '';
    document.getElementById('ncsrDescription').value = part.description || '';
    document.getElementById('ncsrPartQuantity').value = part.partQuantity || '';
    document.getElementById('ncsrMmNumber').value = part.mmNumber || '';
    document.getElementById('ncsrToolIdNumber').value = part.toolIdNumber || '';
    
    document.getElementById('ncsrInstalled').checked = part.installed || false;
    document.getElementById('ncsrInstallDate').value = part.installDate || '';
    
    document.getElementById('ncsrVersumEmdQuote').value = part.versumEmdQuote || '';
    document.getElementById('ncsrCustomerLocation').value = part.customerLocation || '';
    document.getElementById('ncsrCustomerPo').value = part.customerPo || '';
    document.getElementById('ncsrCustomerPoReceivedDate').value = part.customerPoReceivedDate || '';
    document.getElementById('ncsrSupplier').value = part.supplier || '';
    document.getElementById('ncsrSupplierPoOrProductionOrder').value = part.supplierPoOrProductionOrder || '';
    document.getElementById('ncsrFinishDate').value = part.finishDate || '';
    document.getElementById('ncsrEstShipDate').value = part.estShipDate || '';
    document.getElementById('ncsrEcrNumber').value = part.ecrNumber || '';
    document.getElementById('ncsrContractManufacturer').value = part.contractManufacturer || '';
    document.getElementById('ncsrTrackingNumberSupplierToFse').value = part.trackingNumberSupplierToFse || '';
    document.getElementById('ncsrNotificationToRobin').value = part.notificationToRobin || '';
    document.getElementById('ncsrWorkInstructionRequired').checked = part.workInstructionRequired || false;
    document.getElementById('ncsrWorkInstructionIdentifier').value = part.workInstructionIdentifier || '';
    document.getElementById('ncsrFseFieldServiceCompletionDate').value = part.fseFieldServiceCompletionDate || '';
    document.getElementById('ncsrToolOwner').value = part.toolOwner || '';
    document.getElementById('ncsrComments').value = part.comments || '';
    
    hideAllNcsrStates();
    document.getElementById('ncsrFormContainer').style.display = 'block';
}

/**
 * Cancel NCSR form
 */
function cancelNcsrForm() {
    if (ncsrParts.length === 0) {
        showNcsrEmptyState();
    } else {
        showNcsrListContainer();
    }
}

/**
 * Save NCSR part
 */
document.addEventListener('DOMContentLoaded', function() {
    const ncsrForm = document.getElementById('ncsrForm');
    if (ncsrForm) {
        ncsrForm.addEventListener('submit', async function(e) {
            e.preventDefault();
            
            const formData = new FormData(this);
            const data = {
                toolId: currentToolId,
                equipmentNumber: formData.get('equipmentNumber'),
                serialNumber: formData.get('serialNumber'),
                component: formData.get('component'),
                partLocationId: formData.get('partLocationId'),
                discrepantPartMfg: formData.get('discrepantPartMfg'),
                discrepantPartNumber: formData.get('discrepantPartNumber'),
                description: formData.get('description'),
                partQuantity: formData.get('partQuantity') ? parseInt(formData.get('partQuantity')) : null,
                mmNumber: formData.get('mmNumber'),
                toolIdNumber: formData.get('toolIdNumber'),
                installed: document.getElementById('ncsrInstalled').checked,
                installDate: formData.get('installDate') || null,
                versumEmdQuote: formData.get('versumEmdQuote'),
                customerLocation: formData.get('customerLocation'),
                customerPo: formData.get('customerPo'),
                customerPoReceivedDate: formData.get('customerPoReceivedDate') || null,
                supplier: formData.get('supplier'),
                supplierPoOrProductionOrder: formData.get('supplierPoOrProductionOrder'),
                finishDate: formData.get('finishDate') || null,
                estShipDate: formData.get('estShipDate') || null,
                ecrNumber: formData.get('ecrNumber'),
                contractManufacturer: formData.get('contractManufacturer'),
                trackingNumberSupplierToFse: formData.get('trackingNumberSupplierToFse'),
                notificationToRobin: formData.get('notificationToRobin'),
                workInstructionRequired: document.getElementById('ncsrWorkInstructionRequired').checked,
                workInstructionIdentifier: formData.get('workInstructionIdentifier'),
                fseFieldServiceCompletionDate: formData.get('fseFieldServiceCompletionDate') || null,
                toolOwner: formData.get('toolOwner'),
                comments: formData.get('comments')
            };
            
            try {
                const ncsrId = document.getElementById('ncsrFormId').value;
                const url = ncsrId ? `/api/ncsr/${ncsrId}` : '/api/ncsr';
                const method = ncsrId ? 'PUT' : 'POST';
                
                const response = await fetch(url, {
                    method: method,
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(data)
                });
                
                if (!response.ok) throw new Error('Failed to save NCSR part');
                
                // Reload parts and show list
                await loadNcsrParts(currentToolId);
                
                // Update tool detail page if visible
                updateToolNcsrCount();
            } catch (error) {
                console.error('Error saving NCSR part:', error);
                alert('Failed to save NCSR part. Please try again.');
            }
        });
    }
});

/**
 * Delete NCSR part
 */
async function deleteNcsrPart(ncsrId) {
    if (!confirm('Are you sure you want to delete this NCSR part? This action cannot be undone.')) {
        return;
    }
    
    try {
        const response = await fetch(`/api/ncsr/${ncsrId}`, {
            method: 'DELETE'
        });
        
        if (!response.ok) throw new Error('Failed to delete NCSR part');
        
        // Reload parts
        await loadNcsrParts(currentToolId);
        
        // Update tool detail page if visible
        updateToolNcsrCount();
    } catch (error) {
        console.error('Error deleting NCSR part:', error);
        alert('Failed to delete NCSR part. Please try again.');
    }
}

/**
 * Update NCSR count on tool detail page
 */
function updateToolNcsrCount() {
    const countElement = document.getElementById('toolNcsrCount');
    if (countElement && ncsrParts) {
        countElement.textContent = ncsrParts.length;
    }
}

/**
 * State management helpers
 */
function showNcsrLoadingState() {
    hideAllNcsrStates();
    document.getElementById('ncsrLoadingState').style.display = 'block';
}

function showNcsrEmptyState() {
    hideAllNcsrStates();
    document.getElementById('ncsrEmptyState').style.display = 'block';
}

function showNcsrListContainer() {
    hideAllNcsrStates();
    document.getElementById('ncsrListContainer').style.display = 'block';
}

function hideAllNcsrStates() {
    document.getElementById('ncsrLoadingState').style.display = 'none';
    document.getElementById('ncsrEmptyState').style.display = 'none';
    document.getElementById('ncsrListContainer').style.display = 'none';
    document.getElementById('ncsrFormContainer').style.display = 'none';
}

/**
 * Initialize drag-to-scroll for NCSR list
 */
function initializeNcsrDragScroll() {
    const container = document.getElementById('ncsrPartsList');
    if (!container) return;
    
    let isDown = false;
    let startY;
    let scrollTop;
    
    container.addEventListener('mousedown', (e) => {
        // Only enable drag if clicking on the container itself, not on buttons
        if (e.target.tagName === 'BUTTON' || e.target.tagName === 'INPUT') return;
        
        isDown = true;
        ncsrDragScrollEnabled = false;
        container.style.cursor = 'grabbing';
        startY = e.pageY - container.offsetTop;
        scrollTop = container.scrollTop;
    });
    
    container.addEventListener('mouseleave', () => {
        isDown = false;
        container.style.cursor = 'grab';
    });
    
    container.addEventListener('mouseup', () => {
        isDown = false;
        container.style.cursor = 'grab';
        
        // Small delay to prevent click event when dragging
        setTimeout(() => {
            ncsrDragScrollEnabled = false;
        }, 100);
    });
    
    container.addEventListener('mousemove', (e) => {
        if (!isDown) return;
        e.preventDefault();
        ncsrDragScrollEnabled = true;
        
        const y = e.pageY - container.offsetTop;
        const walk = (y - startY) * 2;
        container.scrollTop = scrollTop - walk;
    });
}

// ============================================================================
// BULK UPLOAD FUNCTIONALITY
// ============================================================================

let ncsrUploadData = null;
let ncsrImportResult = null;
let ncsrColumnMappings = {};
let ncsrSelectedFile = null; // Store the actual file object

/**
 * Handle NCSR file selection
 */
function handleNcsrFileSelect(event) {
    const file = event.target.files[0];
    if (!file) {
        console.log('No file selected');
        return;
    }
    
    // Store the file for later use
    ncsrSelectedFile = file;
    
    console.log('File selected:', file.name, file.type, file.size);
    
    // Show loading indicator
    const step1 = document.getElementById('ncsrUploadStep1');
    step1.innerHTML = `
        <div class="text-center py-5">
            <div class="spinner-border text-primary mb-3" role="status">
                <span class="visually-hidden">Loading...</span>
            </div>
            <p>Processing Excel file...</p>
        </div>
    `;
    
    const formData = new FormData();
    formData.append('file', file);
    
    console.log('Sending file to /api/ncsr/preview-import...');
    
    fetch('/api/ncsr/preview-import', {
        method: 'POST',
        body: formData
    })
    .then(response => {
        console.log('Response status:', response.status);
        if (!response.ok) {
            return response.text().then(text => {
                throw new Error(`Server error: ${response.status} - ${text}`);
            });
        }
        return response.json();
    })
    .then(data => {
        console.log('Preview data received:', data);
        ncsrUploadData = data;
        ncsrColumnMappings = data.detectedColumns || {};
        displayNcsrColumnMapping();
        showNcsrUploadStep(2);
    })
    .catch(error => {
        console.error('Error previewing NCSR file:', error);
        alert('Failed to process Excel file:\n' + error.message + '\n\nPlease check the browser console for details.');
        resetNcsrUpload();
    });
}

/**
 * Display column mapping
 */
function displayNcsrColumnMapping() {
    const mappingContainer = document.getElementById('ncsrColumnMapping');
    const sampleContainer = document.getElementById('ncsrSampleRowPreview');
    const totalRowCountEl = document.getElementById('ncsrTotalRowCount');
    
    // Display total row count
    if (totalRowCountEl && ncsrUploadData.totalRows) {
        totalRowCountEl.textContent = ncsrUploadData.totalRows;
    }
    
    // Display sample row
    if (ncsrUploadData.sampleRow) {
        let sampleHtml = '<table class="table table-sm"><tbody>';
        Object.entries(ncsrUploadData.sampleRow).forEach(([colIndex, value]) => {
            let headerName = ncsrUploadData.headerRow[colIndex] || `Column ${colIndex}`;
            
            // Check if this column is mapped to equipmentNumber
            const detectedField = ncsrColumnMappings[colIndex] || '';
            if (detectedField === 'equipmentNumber') {
                headerName = `⭐ ${headerName}`;
            }
            
            sampleHtml += `<tr><td class="fw-bold">${headerName}:</td><td>${value}</td></tr>`;
        });
        sampleHtml += '</tbody></table>';
        sampleContainer.innerHTML = sampleHtml;
    }
    
    // Display column mappings
    let mappingHtml = '';
    ncsrUploadData.headerRow.forEach((header, index) => {
        const detectedField = ncsrColumnMappings[index] || '';
        const fieldLabel = getNcsrFieldLabel(detectedField);
        const isDetected = detectedField !== '';
        
        mappingHtml += `
            <div class="ncsr-column-mapping-item">
                <div class="d-flex justify-content-between align-items-center">
                    <strong>${header}</strong>
                    <span class="badge ${isDetected ? 'bg-success' : 'bg-warning'}">${isDetected ? fieldLabel : 'Not Detected'}</span>
                </div>
            </div>
        `;
    });
    
    mappingContainer.innerHTML = mappingHtml;
}

/**
 * Get field label for display
 */
function getNcsrFieldLabel(fieldName) {
    const labels = {
        'equipmentNumber': 'Equipment #',
        'serialNumber': 'Serial #',
        'component': 'Component',
        'partLocationId': 'Part Location/I.D.',
        'discrepantPartMfg': 'Discrepant Part Mfg',
        'discrepantPartNumber': 'Discrepant Part Number',
        'description': 'Description',
        'partQuantity': 'Part Quantity',
        'mmNumber': 'MM#',
        'toolIdNumber': 'Tool ID#',
        'versumEmdQuote': 'Versum/EMD Quote',
        'customerLocation': 'Customer Location',
        'customerPo': 'Customer PO#',
        'customerPoReceivedDate': 'Customer PO Received Date',
        'supplier': 'Supplier',
        'supplierPoOrProductionOrder': 'Supplier PO#/Production Order',
        'finishDate': 'Finish Date',
        'estShipDate': 'Est Ship Date',
        'ecrNumber': 'ECR#',
        'contractManufacturer': 'Contract Manufacturer',
        'trackingNumberSupplierToFse': 'Tracking # Supplier to FSE',
        'notificationToRobin': 'Notification to Robin',
        'workInstructionRequired': 'Work Instruction Required?',
        'workInstructionIdentifier': 'Work Instruction Identifier',
        'fseFieldServiceCompletionDate': 'FSE Completion Date',
        'toolOwner': 'Tool Owner',
        'status': 'Open/Closed',
        'comments': 'Comments'
    };
    return labels[fieldName] || fieldName;
}

/**
 * Process NCSR import
 */
function processNcsrImport() {
    if (!ncsrSelectedFile) {
        alert('No file selected. Please go back and select a file.');
        return;
    }
    
    console.log('Processing import with file:', ncsrSelectedFile.name);
    console.log('Column mappings:', ncsrColumnMappings);
    
    const formData = new FormData();
    formData.append('file', ncsrSelectedFile);
    formData.append('columnMapping', JSON.stringify(ncsrColumnMappings));
    
    // Show loading indicator
    const step2 = document.getElementById('ncsrUploadStep2');
    const originalContent = step2.innerHTML;
    step2.innerHTML = `
        <div class="text-center py-5">
            <div class="spinner-border text-primary mb-3" role="status">
                <span class="visually-hidden">Loading...</span>
            </div>
            <p>Importing NCSR data and matching with tools...</p>
        </div>
    `;
    
    fetch('/api/ncsr/import', {
        method: 'POST',
        body: formData
    })
    .then(response => {
        console.log('Import response status:', response.status);
        if (!response.ok) {
            return response.text().then(text => {
                throw new Error(`Server error: ${response.status} - ${text}`);
            });
        }
        return response.json();
    })
    .then(data => {
        console.log('Import result received:', data);
        ncsrImportResult = data;
        displayNcsrImportResults();
        showNcsrUploadStep(3);
    })
    .catch(error => {
        console.error('Error importing NCSR data:', error);
        alert('Failed to import NCSR data:\n' + error.message + '\n\nPlease check the browser console for details.');
        // Restore original content on error
        step2.innerHTML = originalContent;
    });
}

/**
 * Display import results
 */
function displayNcsrImportResults() {
    const stats = ncsrImportResult.stats || {created: 0, ignored: 0, warnings: 0};
    const ncsrs = ncsrImportResult.ncsrs || [];
    const warnings = ncsrImportResult.warnings || [];
    
    // Count matched vs unmatched
    const matchedCount = ncsrs.filter(n => n.hasMatch).length;
    const unmatchedCount = warnings.length;
    
    // Initialize counts - matched items will be auto-saved, unmatched start as warnings
    const matchedCountEl = document.getElementById('ncsrMatchedCount');
    const createCountEl = document.getElementById('ncsrCreateCount');
    const ignoredCountEl = document.getElementById('ncsrIgnoredCount');
    const warningCountEl = document.getElementById('ncsrWarningCount');
    
    if (matchedCountEl) matchedCountEl.textContent = matchedCount; // Show matched items
    if (createCountEl) createCountEl.textContent = 0; // User will select which to create
    if (ignoredCountEl) ignoredCountEl.textContent = 0; // User will select which to ignore
    if (warningCountEl) warningCountEl.textContent = unmatchedCount; // All unmatched start as warnings
    
    const itemsList = document.getElementById('ncsrItemsList');
    let html = '';
    
    // Show matched tools first
    if (matchedCount > 0) {
        html += `<div class="mb-3"><h6 class="text-success"><i class="bi bi-check-circle me-2"></i>Matched Tools (${matchedCount})</h6></div>`;
        
        ncsrs.filter(n => n.hasMatch).forEach((ncsr, index) => {
            let displayTitle = ncsr.partLocationId || 'Unknown Location';
            
            html += `
                <div class="ncsr-import-item matched" style="border-left: 4px solid #28a745; background-color: #d4edda;">
                    <div class="d-flex justify-content-between align-items-start">
                        <div class="flex-grow-1">
                            <div class="mb-1">
                                <strong>${displayTitle}</strong>
                                <span class="badge bg-success text-white ms-2">Matched to ${ncsr.toolName || 'Tool'}</span>
                            </div>
                            <div class="small">
                                <div class="mb-1">
                                    <strong>Equipment #:</strong> 
                                    <strong>${ncsr.equipmentNumber || 'Not specified'}</strong>
                                </div>
                                ${ncsr.toolIdNumber ? `<div>Tool ID#: ${ncsr.toolIdNumber}</div>` : ''}
                                ${ncsr.mmNumber ? `<div>MM# / Model #: ${ncsr.mmNumber}</div>` : ''}
                                ${ncsr.component ? `<div>Component: ${ncsr.component}</div>` : ''}
                            </div>
                        </div>
                    </div>
                </div>
            `;
        });
    }
    
    // Show warnings (unmatched items)
    if (unmatchedCount > 0) {
        html += `<div class="mb-3 mt-4"><h6 class="text-warning"><i class="bi bi-exclamation-triangle me-2"></i>Needs Attention (${unmatchedCount})</h6></div>`;
        
        warnings.forEach((warning, index) => {
            const ncsr = warning.ncsr || {};
            const type = warning.type || 'NO_MATCH';
            const canCreateTool = warning.canCreateTool || false;
            
            let displayTitle = ncsr.partLocationId || 'Unknown Location';
            
            html += `
                <div class="ncsr-import-item warning" id="ncsrItem${index}" data-action="warning">
                    <div class="d-flex justify-content-between align-items-start mb-2">
                        <div class="flex-grow-1">
                            <div class="mb-1">
                                <strong>${displayTitle}</strong>
                                <span class="badge bg-warning text-dark ms-2">${type === 'NO_EQUIPMENT' ? 'No Equipment#' : 'No Match'}</span>
                            </div>
                            <div class="small">
                                <div class="mb-1">
                                    <strong>Equipment #:</strong> 
                                    <strong>${ncsr.equipmentNumber || 'Not specified'}</strong>
                                </div>
                                ${ncsr.toolIdNumber ? `<div>Tool ID#: ${ncsr.toolIdNumber}</div>` : ''}
                                ${ncsr.mmNumber ? `<div>MM# / Model #: ${ncsr.mmNumber}</div>` : ''}
                                ${ncsr.component ? `<div>Component: ${ncsr.component}</div>` : ''}
                                ${ncsr.discrepantPartNumber ? `<div>Part #: ${ncsr.discrepantPartNumber}</div>` : ''}
                                ${ncsr.discrepantPartMfg ? `<div>Part Mfg: ${ncsr.discrepantPartMfg}</div>` : ''}
                            </div>
                        </div>
                        <div class="btn-group btn-group-sm ms-3">
                            ${canCreateTool ? `
                                <button class="btn btn-outline-success" onclick="ncsrItemAction(${index}, 'create')">
                                    <i class="bi bi-plus-circle me-1"></i>Create Tool
                                </button>
                            ` : ''}
                            <button class="btn btn-outline-danger" onclick="ncsrItemAction(${index}, 'ignore')">
                                <i class="bi bi-x-circle me-1"></i>Ignore
                            </button>
                        </div>
                    </div>
                </div>
            `;
        });
    }
    
    if (html === '') {
        html = '<p class="text-center text-muted">No items to display</p>';
    }
    
    itemsList.innerHTML = html;
    
    // Initialize drag-scroll
    initNcsrImportDragScroll();
}

/**
 * Handle item action
 */
function ncsrItemAction(index, action) {
    const item = document.getElementById(`ncsrItem${index}`);
    if (!item) return;
    
    // Get previous action state
    const previousAction = item.getAttribute('data-action');
    
    if (action === 'create') {
        item.classList.remove('warning', 'ignore');
        item.classList.add('create');
        item.setAttribute('data-action', 'create');
        
        // Update badge
        const badge = item.querySelector('.badge');
        if (badge) {
            badge.className = 'badge bg-success text-white ms-2';
            badge.textContent = 'Creating Tool';
        }
        
        // Update visual styling
        item.style.opacity = '1';
        item.style.borderLeft = '4px solid #198754';
        
        // Remove strikethrough if it was previously ignored
        const contentDiv = item.querySelector('.flex-grow-1');
        if (contentDiv) {
            contentDiv.style.textDecoration = 'none';
        }
        
        // Remove strikethrough from entire item as well
        item.style.textDecoration = 'none';
        
    } else if (action === 'ignore') {
        item.classList.remove('warning', 'create');
        item.classList.add('ignore');
        item.setAttribute('data-action', 'ignore');
        
        // Update badge
        const badge = item.querySelector('.badge');
        if (badge) {
            badge.className = 'badge bg-danger text-white ms-2';
            badge.textContent = 'Ignored';
        }
        
        // Update visual styling - only apply strikethrough to the content area, not buttons
        item.style.opacity = '0.5';
        item.style.borderLeft = '4px solid #dc3545';
        
        // Apply strikethrough only to the text content div
        const contentDiv = item.querySelector('.flex-grow-1');
        if (contentDiv) {
            contentDiv.style.textDecoration = 'line-through';
        }
    }
    
    // Update counts based on action changes
    updateNcsrCounts(previousAction, action);
}

/**
 * Update NCSR counts when actions change
 */
function updateNcsrCounts(previousAction, newAction) {
    const createCountEl = document.getElementById('ncsrCreateCount');
    const warningCountEl = document.getElementById('ncsrWarningCount');
    const ignoredCountEl = document.getElementById('ncsrIgnoredCount');
    
    // Null check - if elements don't exist yet, return early
    if (!createCountEl || !warningCountEl || !ignoredCountEl) {
        console.warn('Count elements not found in DOM');
        return;
    }
    
    let createCount = parseInt(createCountEl.textContent);
    let warningCount = parseInt(warningCountEl.textContent);
    let ignoredCount = parseInt(ignoredCountEl.textContent);
    
    // Adjust counts based on state transition
    if (previousAction === 'warning' && newAction === 'ignore') {
        // Moving from warning to ignored
        warningCount--;
        ignoredCount++;
    } else if (previousAction === 'warning' && newAction === 'create') {
        // Moving from warning to create
        warningCount--;
        createCount++;
    } else if (previousAction === 'ignore' && newAction === 'warning') {
        // Moving back from ignored to warning
        warningCount++;
        ignoredCount--;
    } else if (previousAction === 'ignore' && newAction === 'create') {
        // Moving from ignored to create
        ignoredCount--;
        createCount++;
    } else if (previousAction === 'create' && newAction === 'ignore') {
        // Moving from create to ignored
        createCount--;
        ignoredCount++;
    } else if (previousAction === 'create' && newAction === 'warning') {
        // Moving from create back to warning
        createCount--;
        warningCount++;
    }
    
    // Update display
    createCountEl.textContent = Math.max(0, createCount);
    warningCountEl.textContent = Math.max(0, warningCount);
    ignoredCountEl.textContent = Math.max(0, ignoredCount);
}

/**
 * Bulk action
 */
function bulkActionNcsr(action) {
    const items = document.querySelectorAll('.ncsr-import-item.warning');
    items.forEach((item) => {
        // Extract the actual index from the item's ID (ncsrItem{index})
        const itemId = item.id;
        const index = parseInt(itemId.replace('ncsrItem', ''));
        
        if (!isNaN(index)) {
            if (action === 'create' && item.querySelector('.btn-outline-success')) {
                ncsrItemAction(index, 'create');
            } else if (action === 'ignore') {
                ncsrItemAction(index, 'ignore');
            }
        }
    });
}

/**
 * Finalize import - actually save to database
 */
function finalizeNcsrImport() {
    console.log('Finalizing import...');
    
    // Collect all NCSR data and their actions
    const ncsrs = ncsrImportResult.ncsrs || [];
    const warnings = ncsrImportResult.warnings || [];
    
    // Build a complete list of all NCSRs with their actions
    const allNcsrsWithActions = [];
    
    // First, add all successfully matched NCSRs (they should be saved)
    ncsrs.forEach((ncsr) => {
        if (ncsr.hasMatch) {
            allNcsrsWithActions.push({ 
                action: 'save', 
                ncsr: ncsr 
            });
        }
    });
    
    // Then, check warning items for user decisions
    warnings.forEach((warning, index) => {
        const item = document.getElementById(`ncsrItem${index}`);
        if (item) {
            const action = item.getAttribute('data-action');
            if (action === 'ignore') {
                allNcsrsWithActions.push({ 
                    action: 'ignore', 
                    ncsr: warning.ncsr 
                });
            } else if (action === 'create') {
                allNcsrsWithActions.push({ 
                    action: 'create', 
                    ncsr: warning.ncsr 
                });
            } else {
                // Default warning items to save (unlinked)
                allNcsrsWithActions.push({ 
                    action: 'save', 
                    ncsr: warning.ncsr 
                });
            }
        }
    });
    
    const payload = {
        items: allNcsrsWithActions
    };
    
    console.log('Sending finalize request:', payload);
    
    fetch('/api/ncsr/finalize-import', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload)
    })
    .then(response => {
        if (!response.ok) {
            return response.text().then(text => {
                throw new Error(`Server error: ${response.status} - ${text}`);
            });
        }
        return response.json();
    })
    .then(data => {
        console.log('Finalize result:', data);
        
        // Show success modal
        showNcsrImportSuccessModal(data.saved, data.ignored);
    })
    .catch(error => {
        console.error('Error finalizing import:', error);
        
        // Show error modal
        showNcsrImportErrorModal(error.message);
    });
}

/**
 * Show success modal after import
 */
function showNcsrImportSuccessModal(savedCount, ignoredCount) {
    // Close the upload modal
    const uploadModal = bootstrap.Modal.getInstance(document.getElementById('uploadNcsrModal'));
    if (uploadModal) {
        uploadModal.hide();
    }
    
    // Update success modal content
    document.getElementById('ncsrSuccessSavedCount').textContent = savedCount;
    document.getElementById('ncsrSuccessIgnoredCount').textContent = ignoredCount;
    
    // Show success modal
    const successModal = new bootstrap.Modal(document.getElementById('ncsrImportSuccessModal'));
    successModal.show();
    
    // Reset upload state
    resetNcsrUpload();
}

/**
 * Show error modal after import failure
 */
function showNcsrImportErrorModal(errorMessage) {
    // Update error modal content
    document.getElementById('ncsrErrorMessage').textContent = errorMessage;
    
    // Show error modal
    const errorModal = new bootstrap.Modal(document.getElementById('ncsrImportErrorModal'));
    errorModal.show();
}

/**
 * Reload page after successful import
 */
function reloadAfterNcsrImport() {
    window.location.reload();
}

/**
 * Reset upload
 */
function resetNcsrUpload() {
    ncsrUploadData = null;
    ncsrImportResult = null;
    ncsrColumnMappings = {};
    ncsrSelectedFile = null; // Clear the stored file
    
    const fileInput = document.getElementById('ncsrExcelFile');
    if (fileInput) {
        fileInput.value = '';
    }
    
    // Restore step 1 content
    const step1 = document.getElementById('ncsrUploadStep1');
    if (step1) {
        step1.innerHTML = `
            <div class="alert alert-info">
                <h6><i class="bi bi-info-circle me-2"></i>How it works:</h6>
                <ol class="mb-0">
                    <li>Upload your NCSR Excel file</li>
                    <li>System auto-detects columns (Equipment #, Component, Part Location, etc.)</li>
                    <li>Review and confirm column mapping</li>
                    <li>System matches Equipment# with Tool Serial Numbers</li>
                    <li>Choose actions for unmatched items (Create Tool, Ignore, or Manual Assignment)</li>
                </ol>
            </div>
            
            <div class="text-center p-5 border border-dashed rounded" style="border-width: 2px !important;">
                <input type="file" id="ncsrExcelFile" accept=".xlsx,.xls" style="display: none;" onchange="handleNcsrFileSelect(event)">
                <i class="bi bi-file-earmark-spreadsheet fs-1 mb-3 text-primary"></i>
                <h5>Select NCSR Excel File</h5>
                <p class="text-muted mb-3">Click below to upload your NCSR list (.xlsx or .xls)</p>
                <button class="btn btn-primary" onclick="document.getElementById('ncsrExcelFile').click()">
                    <i class="bi bi-upload me-2"></i>Choose Excel File
                </button>
            </div>
        `;
    }
    
    showNcsrUploadStep(1);
}

/**
 * Show upload step
 */
function showNcsrUploadStep(step) {
    const step1 = document.getElementById('ncsrUploadStep1');
    const step2 = document.getElementById('ncsrUploadStep2');
    const step3 = document.getElementById('ncsrUploadStep3');
    
    if (step1) step1.style.display = step === 1 ? 'block' : 'none';
    if (step2) step2.style.display = step === 2 ? 'block' : 'none';
    if (step3) step3.style.display = step === 3 ? 'block' : 'none';
}

/**
 * Back to upload
 */
function backToNcsrUpload() {
    showNcsrUploadStep(1);
}

/**
 * Back to mapping
 */
function backToNcsrMapping() {
    showNcsrUploadStep(2);
}

/**
 * Initialize drag scroll for import list
 */
function initNcsrImportDragScroll() {
    const container = document.getElementById('ncsrItemsList');
    if (!container) return;
    
    let isDown = false;
    let startY;
    let scrollTop;
    
    container.addEventListener('mousedown', (e) => {
        if (e.target.tagName === 'BUTTON') return;
        isDown = true;
        container.style.cursor = 'grabbing';
        startY = e.pageY - container.offsetTop;
        scrollTop = container.scrollTop;
    });
    
    container.addEventListener('mouseleave', () => {
        isDown = false;
        container.style.cursor = 'grab';
    });
    
    container.addEventListener('mouseup', () => {
        isDown = false;
        container.style.cursor = 'grab';
    });
    
    container.addEventListener('mousemove', (e) => {
        if (!isDown) return;
        e.preventDefault();
        const y = e.pageY - container.offsetTop;
        const walk = (y - startY) * 2;
        container.scrollTop = scrollTop - walk;
    });
}

