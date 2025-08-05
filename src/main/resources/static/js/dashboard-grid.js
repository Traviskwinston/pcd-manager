/**
 * Dashboard Grid Functionality
 * Manages the facility map with Konva.js
 */

// Global variables for grid management
let stage, gridLayer, toolLayer, gridGroup;
let gridSize = 20; // Size of a single grid square
let gridWidth = 100; // 100 units wide (doubled from 50)
let gridHeight = 50; // 50 units high
let zoomLevel = 1; // Current zoom level
let currentMode = 'select'; // Default mode
let isDragging = false;
let isPanning = false;
let selectedShape = null;
let resizingShape = null;
let resizeHandle = null;
let resizeStartWidth = 0;
let resizeStartHeight = 0;
let toolShapes = {}; // Store references to tool shapes by ID
let drawingShapes = {}; // Store references to drawing shapes
let hasChanges = false; // Track changes to enable save button
let mapInitialized = false; // Track if the map has been initialized
let toolPreview = null; // For tool placement preview
let drawingPreview = null; // For drawing preview
let dragOffsetX = 0;
let dragOffsetY = 0;
let isMouseDown = false; // Track if mouse button is down globally
let potentialSelectShape = null; // Track shape that might be selected on mouseup
window.toolShapes = toolShapes; // Expose for filtering
window.gridLayer = gridLayer; // Expose for filtering

// Parse JSON data from Thymeleaf if it's a string
if (typeof window.allToolsData === 'string') {
    try {
        window.allToolsData = JSON.parse(window.allToolsData);
    } catch (e) {
        console.error('Error parsing allToolsData:', e);
        window.allToolsData = [];
    }
}

if (typeof window.gridItems === 'string') {
    try {
        window.gridItems = JSON.parse(window.gridItems);
    } catch (e) {
        console.error('Error parsing gridItems:', e);
        window.gridItems = [];
    }
}

// Zoom levels
const zoomLevels = [0.7, 0.85, 1, 1.15, 1.3];
let currentZoomIndex = 2; // Index of default zoom level (1)

// Tool placement options
let selectedToolId = null;
let selectedToolSize = '4x2';
let selectedDrawingText = '';
let selectedDrawingColor = 'black';
let selectedDrawingStyle = 'hollow';

// Initialize the grid when document is loaded
document.addEventListener('DOMContentLoaded', initializeGrid);

/**
 * Initialize the Konva stage and layers
 */
function initializeGrid() {
    const gridCanvas = document.getElementById('grid-canvas');
    if (!gridCanvas) return;

    // Initialize stage
    stage = new Konva.Stage({
        container: 'grid-canvas',
        width: gridCanvas.clientWidth,
        height: gridCanvas.clientHeight,
        draggable: false // We'll handle dragging manually
    });

    // Create layers
    gridLayer = new Konva.Layer(); // For grid lines
    toolLayer = new Konva.Layer(); // For tools and drawings
    
    // Add layers to stage
    stage.add(gridLayer);
    stage.add(toolLayer);

    // Draw the grid lines
    drawGrid();
    
    // Set up event listeners
    setupEventListeners();
    
    // Set up controls
    setupControlButtons();
    
    // Add event listener to hide tooltip when hovering over grid controls
    const gridControls = document.getElementById('grid-controls');
    if (gridControls) {
        gridControls.addEventListener('mouseenter', hideToolTooltip);
    }
    
    // Responsive handling
    window.addEventListener('resize', handleResize);
    
    // Load grid items from the server
    loadGridItems();
    
    console.log('Grid initialized');
}

/**
 * Draw the grid lines
 */
function drawGrid() {
    // Clear previous grid
    if (gridGroup) {
        gridGroup.destroy();
    }
    
    gridGroup = new Konva.Group();
    
    // Always draw the entire grid instead of just visible area
    const totalWidth = gridWidth * gridSize;
    const totalHeight = gridHeight * gridSize;
    
    // Draw horizontal grid lines for entire grid
    for (let i = 0; i <= gridHeight; i++) {
        const y = i * gridSize;
        
        const line = new Konva.Line({
            points: [0, y, totalWidth, y],
            stroke: '#ddd',
            strokeWidth: i % 5 === 0 ? 1 : 0.5
        });
        
        gridGroup.add(line);
    }
    
    // Draw vertical grid lines for entire grid
    for (let i = 0; i <= gridWidth; i++) {
        const x = i * gridSize;
        
        const line = new Konva.Line({
            points: [x, 0, x, totalHeight],
            stroke: '#ddd',
            strokeWidth: i % 5 === 0 ? 1 : 0.5
        });
        
        gridGroup.add(line);
    }
    
    gridLayer.add(gridGroup);
    gridLayer.batchDraw();
}

/**
 * Set up event listeners for the grid
 */
function setupEventListeners() {
    // Mousedown event for panning and shape selection
    stage.on('mousedown', handleMouseDown);
    
    // Mousemove event for panning and tool preview
    stage.on('mousemove', handleMouseMove);
    
    // Mouseup event for ending pan or drag
    stage.on('mouseup', handleMouseUp);
    
    // Wheel event for zooming
    stage.on('wheel', handleWheel);
    
    // Touch events for mobile
    stage.on('touchstart', handleTouchStart);
    stage.on('touchmove', handleTouchMove);
    stage.on('touchend', handleTouchEnd);
    
    // Add global document event listeners to track mouse state outside canvas
    document.addEventListener('mousedown', (e) => {
        isMouseDown = true;
    });
    
    document.addEventListener('mouseup', (e) => {
        isMouseDown = false;
        // If we were panning, end panning regardless of where mouseup happened
        if (isPanning) {
            isPanning = false;
            if (currentMode === 'select') {
                stage.container().style.cursor = 'default';
            } else if (currentMode === 'edit') {
                stage.container().style.cursor = 'default';
            }
        }
        
        // End dragging
        if (isDragging) {
            isDragging = false;
        }
    });
    
    // Global mousemove to continue panning when mouse leaves grid
    document.addEventListener('mousemove', (e) => {
        // Only process if we're panning and mouse is down and not dragging
        if (isPanning && isMouseDown && !isDragging) {
            // We need to calculate the movement ourselves since we're outside the stage
            const dx = e.movementX;
            const dy = e.movementY;
            
            // Calculate new position
            const newX = stage.x() + dx;
            const newY = stage.y() + dy;
            
            // Calculate boundaries - ensure we can pan to see the entire grid
            const maxX = 0;
            const minX = -(gridWidth * gridSize * zoomLevel - stage.width());
            const maxY = 0;
            const minY = -(gridHeight * gridSize * zoomLevel - stage.height());
            
            // Apply position with boundaries
            stage.x(Math.min(maxX, Math.max(minX, newX)));
            stage.y(Math.min(maxY, Math.max(minY, newY)));
            
            // Ensure the grid is properly redrawn after panning
            stage.batchDraw();
        }
    });
    
    // Add keyboard event listener for arrow key navigation
    document.addEventListener('keydown', (e) => {
        // Only process if we're in edit mode and have a selected shape
        if (currentMode === 'edit' && selectedShape) {
            let dx = 0;
            let dy = 0;
            
            // Calculate movement based on key pressed - move by 1 grid unit
            switch (e.key) {
                case 'ArrowLeft':
                    dx = -gridSize/2;
                    e.preventDefault();
                    break;
                case 'ArrowRight':
                    dx = gridSize/2;
                    e.preventDefault();
                    break;
                case 'ArrowUp':
                    dy = -gridSize/2;
                    e.preventDefault();
                    break;
                case 'ArrowDown':
                    dy = gridSize/2;
                    e.preventDefault();
                    break;
                default:
                    return; // Exit if not an arrow key
            }
            
            // Move the shape one grid unit
            const newX = selectedShape.x() + dx;
            const newY = selectedShape.y() + dy;
            
            // Apply the new position
            selectedShape.x(newX);
            selectedShape.y(newY);
            
            // Flag as changed
            hasChanges = true;
            updateSaveButton();
            
            // Redraw
            toolLayer.batchDraw();
        }
    });
}

/**
 * Setup control buttons
 */
function setupControlButtons() {
    // Mode buttons - remove any existing event listeners first
    document.querySelectorAll('.grid-control-btn[data-mode]').forEach(btn => {
        // Remove existing event listeners by cloning and replacing the element
        const newBtn = btn.cloneNode(true);
        btn.parentNode.replaceChild(newBtn, btn);
        
        // Add the event listener to the new element
        newBtn.addEventListener('click', () => {
            console.log('Mode button clicked:', newBtn.dataset.mode);
            setMode(newBtn.dataset.mode);
        });
    });
    
    // Action buttons
    document.querySelectorAll('.grid-control-btn[data-action]').forEach(btn => {
        btn.addEventListener('click', () => {
            switch (btn.dataset.action) {
                case 'zoom-in':
                    zoomIn();
                    break;
                case 'zoom-out':
                    zoomOut();
                    break;
                case 'delete':
                    if (!btn.classList.contains('disabled')) {
                        confirmDelete();
                    }
                    break;
                case 'save':
                    if (!btn.classList.contains('disabled')) {
                        saveGridState();
                    }
                    break;
            }
        });
    });
    
    // Setup Add Tool Modal
    const placeToolBtn = document.getElementById('place-tool-btn');
    if (placeToolBtn) {
        placeToolBtn.addEventListener('click', () => {
            selectedToolId = document.getElementById('tool-select').value;
            selectedToolSize = document.querySelector('input[name="tool-size"]:checked').value;
            
            const toolModal = bootstrap.Modal.getInstance(document.getElementById('add-tool-modal'));
            toolModal.hide();
            
            createToolPreview();
        });
    }
    
    // Setup Draw Modal
    const placeDrawingBtn = document.getElementById('place-drawing-btn');
    if (placeDrawingBtn) {
        placeDrawingBtn.addEventListener('click', () => {
            selectedDrawingText = document.getElementById('drawing-text').value;
            selectedDrawingColor = document.querySelector('.color-btn.active').dataset.color || 'black';
            selectedDrawingStyle = document.querySelector('input[name="drawing-style"]:checked').value;
            
            const drawModal = bootstrap.Modal.getInstance(document.getElementById('draw-shape-modal'));
            drawModal.hide();
            
            createDrawingPreview();
        });
    }
    
    // Drawing color buttons
    document.querySelectorAll('.color-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.color-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
        });
    });
    
    // Edit Drawing Modal
    const updateDrawingBtn = document.getElementById('update-drawing-btn');
    if (updateDrawingBtn) {
        updateDrawingBtn.addEventListener('click', () => {
            // Use the saved shape reference
            const shape = window.currentEditingShape;
            
            if (shape && shape.attrs.type === 'drawing') {
                const text = document.getElementById('edit-drawing-text').value;
                const color = document.querySelector('.edit-color-btn.active').dataset.color || 'black';
                const isSolid = document.querySelector('input[name="edit-drawing-style"]:checked').value === 'solid';
                
                updateDrawing(shape, text, color, isSolid);
                
                // Close the modal
                const modalElement = document.getElementById('edit-text-modal');
                const editModal = bootstrap.Modal.getInstance(modalElement);
                if (editModal) {
                    editModal.hide();
                }
            }
        });
    }
    
    // Edit drawing color buttons
    document.querySelectorAll('.edit-color-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.edit-color-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
        });
    });
    
    // Delete confirmation
    const confirmDeleteBtn = document.getElementById('confirm-delete-btn');
    if (confirmDeleteBtn) {
        confirmDeleteBtn.addEventListener('click', () => {
            if (selectedShape) {
                deleteShape(selectedShape);
                
                const deleteModal = bootstrap.Modal.getInstance(document.getElementById('delete-confirm-modal'));
                deleteModal.hide();
            }
        });
    }
    
    // Tool search in modal
    const toolSearchModal = document.getElementById('tool-search-modal');
    if (toolSearchModal) {
        toolSearchModal.addEventListener('input', filterToolDropdown);
    }
}

/**
 * Handle window resize
 */
function handleResize() {
    const gridCanvas = document.getElementById('grid-canvas');
    if (!gridCanvas) return;
    
    // Update stage dimensions to match container
    stage.width(gridCanvas.clientWidth);
    stage.height(gridCanvas.clientHeight);
    
    // Force complete redraw of grid
    drawGrid();
    
    // Update all layers
    gridLayer.batchDraw();
    toolLayer.batchDraw();
    stage.batchDraw();
    
    console.log('Grid resized to match container');
}

/**
 * Set the current mode
 */
function setMode(mode) {
    // Save the current state before changing modes
    if (hasChanges) {
        saveGridState();
    }
    
    // Update current mode
    currentMode = mode;
    
    // Update UI
    document.querySelectorAll('.grid-control-btn[data-mode]').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.mode === mode);
    });
    
    // Deselect any selected shape
    deselectShape();
    
    // Remove preview if any
    if (toolPreview) {
        toolPreview.destroy();
        toolPreview = null;
    }
    
    if (drawingPreview) {
        drawingPreview.destroy();
        drawingPreview = null;
    }
    
    // Change cursor based on mode
    const gridCanvas = document.getElementById('grid-canvas');
    if (gridCanvas) {
        switch (mode) {
            case 'select':
                gridCanvas.style.cursor = 'default';
                break;
            case 'edit':
                // Set to default cursor initially, will change to pointer on hover
                gridCanvas.style.cursor = 'default';
                break;
            case 'add-tool':
                showAddToolModal();
                gridCanvas.style.cursor = 'cell';
                break;
            case 'draw':
                showDrawModal();
                gridCanvas.style.cursor = 'crosshair';
                break;
        }
    }
}

/**
 * Handle mousedown event
 */
function handleMouseDown(e) {
    // Prevent default behavior
    e.evt.preventDefault();
    
    // Set global mouse state
    isMouseDown = true;
    
    // Get mouse position
    const pos = stage.getPointerPosition();
    
    // Check if we clicked on a shape
    const shape = toolLayer.getIntersection(pos);
    const shapeGroup = (shape && shape.getParent() && (shape.getParent().attrs.id || shape.getParent().attrs.type)) ? shape.getParent() : null;

    if (currentMode === 'select' || currentMode === 'edit') {
        if (shapeGroup) { // Clicked on a shape group
            potentialSelectShape = shapeGroup; // Always set potential shape on click

            if (currentMode === 'edit') {
                if (selectedShape && selectedShape === shapeGroup) {
                    // Clicked on the ALREADY selected shape in EDIT mode.
                    // DO NOT set isDragging = true here. Let mousemove/mouseup decide.
                    // Just record the potential shape (already done above).
                    // Calculate drag offset in case it *becomes* a drag.
                    dragOffsetX = pos.x / zoomLevel - shapeGroup.x();
                    dragOffsetY = pos.y / zoomLevel - shapeGroup.y();
                } else {
                    // Clicked on a DIFFERENT shape in EDIT mode (or no shape was selected).
                    // Set potential shape (already done above).
                    // Start timeout to potentially switch to panning if held.
                    setTimeout(() => {
                        if (isMouseDown && !isPanning && potentialSelectShape && !isDragging) {
                            isPanning = true;
                            stage.container().style.cursor = 'grabbing';
                            potentialSelectShape = null; // Panning overrides selection intent
                        }
                    }, 200);
                }
            } else if (currentMode === 'select') {
                if (shapeGroup.attrs.type === 'tool') {
                    // Clicked on a tool in SELECT mode.
                    // Set potential shape (already done above).
                    // Start timeout to potentially switch to panning if held.
                    setTimeout(() => {
                        if (isMouseDown && !isPanning && potentialSelectShape && !isDragging) {
                            isPanning = true;
                            stage.container().style.cursor = 'grabbing';
                            potentialSelectShape = null; // Panning overrides selection intent
                        }
                    }, 200);
                } else if (shapeGroup.attrs.type === 'drawing') {
                    // Clicked on a drawing in SELECT mode - just start panning.
                    isPanning = true;
                    stage.container().style.cursor = 'grabbing';
                    potentialSelectShape = null;
                }
            }
        } else { // Clicked on empty space
            isPanning = true;
            stage.container().style.cursor = 'grabbing';
            potentialSelectShape = null;
        }
    } else if (currentMode === 'add-tool' && toolPreview) {
        placeTool();
    } else if (currentMode === 'draw' && drawingPreview) {
        placeDrawing();
    }
}

/**
 * Handle shape click
 */
function handleShapeClick(shapeGroup, e) {
    if (currentMode === 'select') {
        // In select mode, only select tools, not drawings
        if (shapeGroup.attrs.type === 'tool') {
            selectShape(shapeGroup);
        }
        // Ignore clicks on drawings in select mode
    }
    // In edit mode, selection now happens on mouseup, not here
}

/**
 * Handle mousemove event
 */
function handleMouseMove(e) {
    const pos = stage.getPointerPosition();
    
    // Determine if a potential selection click should become a drag (in edit mode)
    if (currentMode === 'edit' && isMouseDown && potentialSelectShape && selectedShape === potentialSelectShape && !isDragging && !isPanning) {
        const movement = Math.abs(e.evt.movementX) + Math.abs(e.evt.movementY);
        if (movement > 4) { // Threshold to start dragging
             isDragging = true; // Now we are definitely dragging
             potentialSelectShape = null; // Dragging overrides selection intent
        }
    }
    
    // Determine if a potential click should become a pan
    if (isMouseDown && potentialSelectShape && !isPanning && !isDragging && 
        e.evt.movementX !== 0 && e.evt.movementY !== 0) {
        const movement = Math.abs(e.evt.movementX) + Math.abs(e.evt.movementY);
        if (movement > 4) {  
            isPanning = true;
            stage.container().style.cursor = 'grabbing';
            potentialSelectShape = null;
        }
    }
    
    // If we're dragging, don't allow panning to start
    if (isDragging) {
        isPanning = false;
    }
    
    // Handle panning
    if (isPanning && !isDragging) {
        const dx = e.evt.movementX;
        const dy = e.evt.movementY;
        
        const newX = stage.x() + dx;
        const newY = stage.y() + dy;
        
        const maxX = 0;
        const minX = -(gridWidth * gridSize * zoomLevel - stage.width());
        const maxY = 0;
        const minY = -(gridHeight * gridSize * zoomLevel - stage.height());
        
        stage.x(Math.min(maxX, Math.max(minX, newX)));
        stage.y(Math.min(maxY, Math.max(minY, newY)));
        
        stage.batchDraw();
        return;
    }
    
    // Handle cursor changes in edit mode
    if (currentMode === 'edit') {
        const shape = toolLayer.getIntersection(pos);
        const gridCanvas = document.getElementById('grid-canvas');
        
        if (isDragging && selectedShape) {
            if (gridCanvas) gridCanvas.style.cursor = 'grabbing';
        } else if (shape && shape.getParent() && (shape.getParent().attrs.type === 'tool' || shape.getParent().attrs.type === 'drawing')) {
            if (gridCanvas) gridCanvas.style.cursor = 'pointer';
        } else {
            if (gridCanvas) gridCanvas.style.cursor = 'default';
        }
    }
    
    // Handle dragging selected shape
    if (isDragging && selectedShape) {
        // Calculate grid-aligned position, accounting for the initial drag offset stored in mousedown
        // We use pos (pointer position) directly relative to stage origin, 
        // adjust by offset, then snap to grid.
        let targetX = pos.x / zoomLevel - dragOffsetX;
        let targetY = pos.y / zoomLevel - dragOffsetY;
        
        // Snap to grid
        const snappedX = Math.round(targetX / gridSize) * gridSize;
        const snappedY = Math.round(targetY / gridSize) * gridSize;
        
        selectedShape.x(snappedX);
        selectedShape.y(snappedY);
        
        hasChanges = true;
        updateSaveButton();
        
        toolLayer.batchDraw();
    }
    
    // Update tool preview position
    if (toolPreview && currentMode === 'add-tool') {
        const transform = stage.getAbsoluteTransform().copy().invert();
        const stagePoint = transform.point(pos);
        
        let x = Math.floor(stagePoint.x / gridSize) * gridSize;
        let y = Math.floor(stagePoint.y / gridSize) * gridSize;
        
        const width = toolPreview.findOne('.mainRect').width();
        const height = toolPreview.findOne('.mainRect').height();
        
        // Adjust position based on preview size to center it around cursor if needed, or place from corner
        // Example: center alignment
        // x = x - Math.floor(width / 2) + (gridSize/2);
        // y = y - Math.floor(height / 2) + (gridSize/2);

        // Example: Top-left alignment to grid intersection under cursor
        x = Math.floor(stagePoint.x / gridSize) * gridSize;
        y = Math.floor(stagePoint.y / gridSize) * gridSize;

        toolPreview.x(x);
        toolPreview.y(y);
        
        toolLayer.batchDraw();
    }
    
    // Update drawing preview position
    if (drawingPreview && currentMode === 'draw') {
        const transform = stage.getAbsoluteTransform().copy().invert();
        const stagePoint = transform.point(pos);
        
        let x = Math.floor(stagePoint.x / gridSize) * gridSize;
        let y = Math.floor(stagePoint.y / gridSize) * gridSize;
                
        drawingPreview.x(x);
        drawingPreview.y(y);
        
        toolLayer.batchDraw();
    }
    
    // Show tooltip if hovering over a tool in select mode
    if (currentMode === 'select') {
        const shape = toolLayer.getIntersection(pos);
        
        if (shape && shape.getParent() && shape.getParent().attrs.type === 'tool') {
            showToolTooltip(shape.getParent(), pos);
        } else {
            hideToolTooltip();
        }
    }
}

/**
 * Handle mouseup event
 */
function handleMouseUp(e) {
    // Get mouse position
    const pos = stage.getPointerPosition();
    const mouseMovement = e.evt.movementX !== undefined ? 
        Math.abs(e.evt.movementX) + Math.abs(e.evt.movementY) : 0;
    
    // Check if this was a click (not a drag) on an already selected drawing in edit mode
    if (currentMode === 'edit' && selectedShape && 
        potentialSelectShape === selectedShape && 
        selectedShape.attrs.type === 'drawing' && 
        !isPanning && mouseMovement < 3 && !isDragging) {
        
        // This is a click on an already selected drawing - open edit modal
        prepareEditDrawingModal(selectedShape); // Populate modal fields first
        const modalEl = document.getElementById('edit-text-modal');
        let editModalInstance = bootstrap.Modal.getInstance(modalEl);
        if (editModalInstance) { // If an instance exists, dispose of it to reset state
            editModalInstance.dispose();
        }
        editModalInstance = new bootstrap.Modal(modalEl); // Create a fresh instance
        editModalInstance.show(); // Then show

    }
    // Regular click handling for new selection
    else if (currentMode === 'edit' && potentialSelectShape && !isPanning && mouseMovement < 3) {
        // This is a regular click (not a drag or pan) - select the shape
        selectShape(potentialSelectShape);
    }
    // Handle tool selection in select mode
    else if (currentMode === 'select' && potentialSelectShape && 
             potentialSelectShape.attrs.type === 'tool' && 
             !isPanning && mouseMovement < 3) {
        
        // Check if this tool is already selected
        const isAlreadySelected = (selectedShape === potentialSelectShape);
        
        if (isAlreadySelected) {
            // If the tool is already selected, navigate to its page
            if (potentialSelectShape.attrs.toolId) {
                window.location.href = '/tools/' + potentialSelectShape.attrs.toolId;
            }
        } else {
            // If the tool is not selected, select it
            selectShape(potentialSelectShape);
        }
    }
    
    // Reset all state flags
    isMouseDown = false;
    isPanning = false;
    isDragging = false;
    potentialSelectShape = null;
    
    // Reorder shapes if we were dragging
    if (selectedShape) {
        reorderShapesBySize();
    }
    
    // Reset cursor based on current mode
    const gridCanvas = document.getElementById('grid-canvas');
    if (gridCanvas) {
        if (currentMode === 'select' || currentMode === 'edit') {
            gridCanvas.style.cursor = 'default';
        }
    }
}

/**
 * Handle wheel event for zooming
 */
function handleWheel(e) {
    e.evt.preventDefault();
    
    if (e.evt.deltaY < 0) {
        zoomIn();
    } else {
        zoomOut();
    }
}

/**
 * Zoom in
 */
function zoomIn() {
    if (currentZoomIndex < zoomLevels.length - 1) {
        currentZoomIndex++;
        applyZoom();
    }
}

/**
 * Zoom out
 */
function zoomOut() {
    if (currentZoomIndex > 0) {
        currentZoomIndex--;
        applyZoom();
    }
}

/**
 * Apply zoom level
 */
function applyZoom() {
    zoomLevel = zoomLevels[currentZoomIndex];
    
    stage.scale({ x: zoomLevel, y: zoomLevel });
    
    // Redraw the grid with the new zoom level
    drawGrid();
    
    // Update all layers
    gridLayer.batchDraw();
    toolLayer.batchDraw();
    stage.batchDraw();
    
    console.log(`Zoom level set to: ${zoomLevel}`);
}

/**
 * Handle touch start event
 */
function handleTouchStart(e) {
    // Get touch position
    const touch = e.evt.touches[0];
    
    // Store the initial touch position
    e.evt.target.prevTouch = {
        clientX: touch.clientX,
        clientY: touch.clientY
    };
    
    const pos = stage.getPointerPosition();
    
    // Similar logic to handleMouseDown
    if (currentMode === 'select' || currentMode === 'edit') {
        const shape = toolLayer.getIntersection(pos);
        
        if (shape && shape.getParent() && (shape.getParent().attrs.id || shape.getParent().attrs.type)) {
            const shapeGroup = shape.getParent();
            
            if (currentMode === 'edit') {
                // In edit mode, check if we touched the already selected shape
                if (selectedShape && selectedShape === shapeGroup) {
                    // Only start dragging if the shape is already selected
                    isDragging = true;
                    
                    // Account for zoom level to maintain consistent position
                    dragOffsetX = pos.x - shapeGroup.x();
                    dragOffsetY = pos.y - shapeGroup.y();
                } else {
                    // Store as potential shape to select on touch end
                    potentialSelectShape = shapeGroup;
                    
                    // Start a small timeout - if touch moves significantly before timeout,
                    // we'll assume user wants to pan instead of select
                    setTimeout(() => {
                        if (isMouseDown && !isPanning && potentialSelectShape) {
                            // Still touch active, not panning, and same potential shape
                            // This is a "touch and hold" - start panning
                            isPanning = true;
                            stage.container().style.cursor = 'grabbing';
                            potentialSelectShape = null;
                        }
                    }, 200);
                }
            } else if (currentMode === 'select') {
                // In select mode, check if we touched a tool
                if (shapeGroup.attrs.type === 'tool') {
                    // Store as potential tool to select on touch end
                    potentialSelectShape = shapeGroup;
                    
                    // Start a small timeout - if touch moves significantly before timeout,
                    // we'll assume user wants to pan instead of select
                    setTimeout(() => {
                        if (isMouseDown && !isPanning && potentialSelectShape) {
                            // Still touch active, not panning, and same potential shape
                            // This is a "touch and hold" - start panning
                            isPanning = true;
                            stage.container().style.cursor = 'grabbing';
                            potentialSelectShape = null;
                        }
                    }, 200);
                } else if (shapeGroup.attrs.type === 'drawing') {
                    // For drawings in select mode, allow panning
                    isPanning = true;
                    stage.container().style.cursor = 'grabbing';
                }
            }
        } else {
            isPanning = true;
            stage.container().style.cursor = 'grabbing';
            potentialSelectShape = null;
        }
    } else if (currentMode === 'add-tool' && toolPreview) {
        placeTool();
    } else if (currentMode === 'draw' && drawingPreview) {
        placeDrawing();
    }
}

/**
 * Handle touch move event
 */
function handleTouchMove(e) {
    e.evt.preventDefault();
    
    // Get touch position
    const touch = e.evt.touches[0];
    const pos = stage.getPointerPosition();
    
    // Calculate delta from previous position
    if (!e.evt.target.prevTouch) {
        e.evt.target.prevTouch = {
            clientX: touch.clientX,
            clientY: touch.clientY
        };
    }
    
    const dx = touch.clientX - e.evt.target.prevTouch.clientX;
    const dy = touch.clientY - e.evt.target.prevTouch.clientY;
    
    // If touch moves significantly and we have a potential shape to select,
    // assume user wants to pan instead
    if (potentialSelectShape && !isPanning) {
        const movement = Math.abs(dx) + Math.abs(dy);
        if (movement > 10) {  // Higher threshold for touch
            isPanning = true;
            stage.container().style.cursor = 'grabbing';
            potentialSelectShape = null;
        }
    }
    
    // Similar logic to handleMouseMove
    if (isPanning) {
        // Calculate new position
        const newX = stage.x() + dx;
        const newY = stage.y() + dy;
        
        // Calculate boundaries
        const maxX = 0;
        const minX = -(gridWidth * gridSize * zoomLevel - stage.width());
        const maxY = 0;
        const minY = -(gridHeight * gridSize * zoomLevel - stage.height());
        
        // Apply position with boundaries
        stage.x(Math.min(maxX, Math.max(minX, newX)));
        stage.y(Math.min(maxY, Math.max(minY, newY)));
        
        // Update for next event
        e.evt.target.prevTouch = {
            clientX: touch.clientX,
            clientY: touch.clientY
        };
        
        // Ensure grid is redrawn
        stage.batchDraw();
    } else if (isDragging && selectedShape) {
        // Calculate grid-aligned position, accounting for the initial drag offset
        const x = Math.floor((pos.x / zoomLevel - dragOffsetX) / gridSize) * gridSize;
        const y = Math.floor((pos.y / zoomLevel - dragOffsetY) / gridSize) * gridSize;
        
        // Apply position
        selectedShape.x(x);
        selectedShape.y(y);
        
        // Flag as changed
        hasChanges = true;
        updateSaveButton();
        
        // Update for next event
        e.evt.target.prevTouch = {
            clientX: touch.clientX,
            clientY: touch.clientY
        };
        
        toolLayer.batchDraw();
    }
}

/**
 * Handle touch end event
 */
function handleTouchEnd(e) {
    // Similar to handleMouseUp
    
    // Check if we should select the potential shape (in edit mode)
    if (currentMode === 'edit' && potentialSelectShape && !isPanning) {
        // Check if we're tapping on an already selected drawing
        if (selectedShape && selectedShape === potentialSelectShape && 
            selectedShape.attrs.type === 'drawing') {
            // If we tapped on an already selected drawing, open the edit dialog
            showEditDrawingModal(selectedShape);
        } else {
            // Otherwise just select the shape
            selectShape(potentialSelectShape);
            
            // Check for double-tap to edit text (for drawings)
            if (potentialSelectShape.attrs.type === 'drawing') {
                const now = new Date().getTime();
                
                // Check if this is a second tap within 300ms
                if (potentialSelectShape.lastTapTime && now - potentialSelectShape.lastTapTime < 300) {
                    showEditDrawingModal(potentialSelectShape);
                }
                
                // Update last tap time
                potentialSelectShape.lastTapTime = now;
            }
        }
    }
    
    // Single tap in select mode selects the shape (only for tools)
    if (currentMode === 'select' && potentialSelectShape && !isPanning) {
        if (potentialSelectShape.attrs.type === 'tool') {
            // Check if this tool is already selected
            const isAlreadySelected = (selectedShape === potentialSelectShape);
            
            if (isAlreadySelected) {
                // If the tool is already selected, navigate to its page
                if (potentialSelectShape.attrs.toolId) {
                    window.location.href = '/tools/' + potentialSelectShape.attrs.toolId;
                }
            } else {
                // If the tool is not selected, select it
                selectShape(potentialSelectShape);
            }
        }
        // Ignore drawing selections in select mode
    }
    
    isPanning = false;
    isDragging = false;
    potentialSelectShape = null;
    
    // Reset cursor based on current mode
    if (currentMode === 'select') {
        stage.container().style.cursor = 'default';
    } else if (currentMode === 'edit') {
        stage.container().style.cursor = 'default';
    }
    
    // Clear stored touch position
    if (e.evt.target) {
        e.evt.target.prevTouch = null;
    }
}

/**
 * Deselect shape
 */
function deselectShape() {
    if (selectedShape) {
        // Remove selection visual indicator
        if (selectedShape.attrs.type === 'tool') {
            const rect = selectedShape.findOne('.mainRect');
            rect.stroke('#333');
            rect.strokeWidth(1);
            
            // Remove glow effect
            rect.shadowColor('black');
            rect.shadowBlur(5);
            rect.shadowOffset({ x: 2, y: 2 });
            rect.shadowOpacity(0.3);
        } else if (selectedShape.attrs.type === 'drawing') {
            // Reset to original color
            const color = selectedShape.attrs.color || 'black';
            const rect = selectedShape.findOne('.mainRect');
            rect.stroke(getStrokeColor(color));
            rect.strokeWidth(1);
            
            // Remove glow effect
            rect.shadowColor(null);
            rect.shadowBlur(0);
            rect.shadowOffset({ x: 0, y: 0 });
            rect.shadowOpacity(0);
        }
        
        selectedShape = null;
        
        // Disable delete button
        document.getElementById('delete-btn').classList.add('disabled');
        
        toolLayer.batchDraw();
    }
}

/**
 * Select shape
 */
function selectShape(shape) {
    deselectShape();
    
    selectedShape = shape;
    
    // Add selection visual indicator with glow effect
    if (shape.attrs.type === 'tool') {
        const rect = shape.findOne('.mainRect');
        rect.stroke('#007bff');
        rect.strokeWidth(2);
        
        // Add highlight glow effect
        rect.shadowColor('#007bff');
        rect.shadowBlur(10);
        rect.shadowOffset({ x: 0, y: 0 });
        rect.shadowOpacity(0.7);
    } else if (shape.attrs.type === 'drawing') {
        const rect = shape.findOne('.mainRect');
        rect.stroke('#007bff');
        rect.strokeWidth(2);
        
        // Add highlight glow effect
        rect.shadowColor('#007bff');
        rect.shadowBlur(10);
        rect.shadowOffset({ x: 0, y: 0 });
        rect.shadowOpacity(0.7);
    }
    
    // Enable delete button
    document.getElementById('delete-btn').classList.remove('disabled');
    
    toolLayer.batchDraw();
}

/**
 * Get stroke color based on color name
 */
function getStrokeColor(color) {
    switch (color) {
        case 'black':
            return '#333';
        case 'blue':
            return '#0d6efd';
        case 'green':
            return '#198754';
        case 'red':
            return '#dc3545';
        case 'yellow':
            return '#ffc107';
        default:
            return '#333';
    }
}

/**
 * Show add tool modal
 */
function showAddToolModal() {
    // Populate available tools in the dropdown
    populateToolDropdown();
    
    // Reset selection
    selectedToolId = null;
    document.getElementById('size-4x2').checked = true;
    
    // Show modal
    const addToolModal = new bootstrap.Modal(document.getElementById('add-tool-modal'));
    addToolModal.show();
}

/**
 * Show draw modal
 */
function showDrawModal() {
    // Reset fields
    document.getElementById('drawing-text').value = '';
    document.getElementById('drawing-width').value = '4';
    document.getElementById('drawing-height').value = '3';
    document.querySelectorAll('.color-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelector('.color-btn[data-color="black"]').classList.add('active');
    document.getElementById('style-hollow').checked = true;
    
    // Show modal
    const drawModal = new bootstrap.Modal(document.getElementById('draw-shape-modal'));
    drawModal.show();
}

/**
 * Show Edit Drawing Modal
 */
function showEditDrawingModal(shape) {
    if (shape.attrs.type !== 'drawing') return;

    // Populate modal fields with shape data
    prepareEditDrawingModal(shape); 

    const modalEl = document.getElementById('edit-text-modal');
    let editModal = bootstrap.Modal.getInstance(modalEl);

    // Dispose of any existing modal instance to ensure a fresh state
    if (editModal) {
        editModal.dispose();
    }
    
    editModal = new bootstrap.Modal(modalEl); // Create a new instance
    editModal.show();
}

/**
 * Update save button
 */
function updateSaveButton() {
    const saveBtn = document.getElementById('save-btn');
    if (saveBtn) {
        if (hasChanges) {
            saveBtn.classList.remove('disabled');
        } else {
            saveBtn.classList.add('disabled');
        }
    }
}

/**
 * Load grid items from the server
 */
function loadGridItems() {
    try {
        // Check if window.gridItems exists and is properly parsed
        if (!window.gridItems || !Array.isArray(window.gridItems)) {
            console.warn('Grid items data not available or not an array');
            // Retry parsing if it's a string
            if (typeof window.gridItems === 'string') {
                try {
                    window.gridItems = JSON.parse(window.gridItems);
                } catch (e) {
                    console.error('Error parsing gridItems:', e);
                    window.gridItems = [];
                }
            } else {
                window.gridItems = [];
            }
        }
        
        const items = window.gridItems;
        
        items.forEach(item => {
            if (item.type === 'TOOL') {
                createToolShape(item);
            } else if (item.type === 'DRAWING') {
                createDrawingShape(item);
            }
        });
        
        // Reorder shapes by size after loading all items
        reorderShapesBySize();
        
        // Trigger a custom event to signal the grid is ready
        document.dispatchEvent(new CustomEvent('gridReady'));
        
        mapInitialized = true;
    } catch (e) {
        console.error('Error loading grid items:', e);
    }
}

/**
 * Confirm delete
 */
function confirmDelete() {
    if (!selectedShape) return;
    
    // Show the delete confirmation modal
    const deleteModal = new bootstrap.Modal(document.getElementById('delete-confirm-modal'));
    deleteModal.show();
}

/**
 * Save grid state
 */
function saveGridState() {
    if (!hasChanges) return;
    
    try {
        // Collect all grid items
        const items = [];
        
        // Add tools
        for (const id in toolShapes) {
            if (toolShapes.hasOwnProperty(id)) {
                const shape = toolShapes[id];
                items.push({
                    id: shape.attrs.id,
                    x: Math.floor(shape.x() / gridSize),
                    y: Math.floor(shape.y() / gridSize)
                });
            }
        }
        
        // Add drawings
        for (const id in drawingShapes) {
            if (drawingShapes.hasOwnProperty(id)) {
                const shape = drawingShapes[id];
                items.push({
                    id: shape.attrs.id,
                    x: Math.floor(shape.x() / gridSize),
                    y: Math.floor(shape.y() / gridSize),
                    width: Math.floor(shape.findOne('.mainRect').width() / gridSize),
                    height: Math.floor(shape.findOne('.mainRect').height() / gridSize)
                });
            }
        }
        
        // Send to server
        fetch('/api/map/save', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(items)
        })
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to save map state');
            }
            
            // Reset changes flag
            hasChanges = false;
            updateSaveButton();
            console.log('Map state saved successfully');
        })
        .catch(error => {
            console.error('Error saving map state:', error);
        });
    } catch (e) {
        console.error('Error preparing map state for saving:', e);
    }
}

// Flag to prevent multiple simultaneous calls
let isPopulatingToolDropdown = false;

/**
 * Populate tool dropdown with available tools
 * Always fetch from API to ensure proper filtering (location-based, excluding tools already on map)
 */
function populateToolDropdown() {
    // Prevent multiple simultaneous calls
    if (isPopulatingToolDropdown) {
        console.log('populateToolDropdown already in progress, skipping...');
        return;
    }
    
    const toolSelect = document.getElementById('tool-select');
    if (!toolSelect) return;
    
    // Set flag to prevent duplicate calls
    isPopulatingToolDropdown = true;
    
    // Clear existing options
    toolSelect.innerHTML = '';
    
    console.log('Starting populateToolDropdown...');
    
    // Always fetch from API to get properly filtered tools (by location, excluding tools already on map)
    // window.allToolsData includes all tools from location but doesn't filter out tools already on the map
    fetch('/api/map/available-tools')
        .then(response => response.json())
        .then(data => {
            const tools = data.tools || data; // Handle both new and old response format
            console.log(`Loading ${tools.length} available tools for map`);
            
            // Create expanded tool list with Feed variants for slurry tools
            const expandedTools = [];
            
            tools.forEach((tool, index) => {
                // Check placement status for this tool
                const regularPlaced = tool.regularPlaced || false;
                const feedPlaced = tool.feedPlaced || false;
                
                // Add the original tool only if it's not already placed
                if (!regularPlaced) {
                    expandedTools.push({
                        id: tool.id,
                        name: tool.name,
                        model: tool.model,
                        serial: tool.serial,
                        type: tool.type,
                        status: tool.status,
                        isFeed: false
                    });
                }
                
                // Check if this is a slurry tool (ends with 'D') and feed variant not placed
                if (tool.name && tool.name.toUpperCase().endsWith('D') && !feedPlaced) {
                    // Create Feed variant (replace 'D' with 'F')
                    const feedName = tool.name.slice(0, -1) + 'F';
                    expandedTools.push({
                        id: `${tool.id}_FEED`, // Special ID to indicate this is a feed variant
                        name: feedName,
                        model: tool.model,
                        serial: tool.serial,
                        type: tool.type,
                        status: tool.status,
                        isFeed: true,
                        originalToolId: tool.id // Keep reference to original tool
                    });
                    console.log(`Added Feed variant: ${feedName} for slurry tool: ${tool.name} (regular placed: ${regularPlaced}, feed placed: ${feedPlaced})`);
                }
            });
            
            // Populate dropdown with expanded tool list
            expandedTools.forEach((tool, index) => {
                const option = document.createElement('option');
                option.value = tool.id;
                option.text = `${tool.name} - ${tool.model || ''}${tool.isFeed ? ' (Feed)' : ''}`;
                option.setAttribute('data-type', tool.type || '');
                option.setAttribute('data-model', tool.model || '');
                option.setAttribute('data-serial', tool.serial || '');
                option.setAttribute('data-status', tool.status || '');
                option.setAttribute('data-is-feed', tool.isFeed || false);
                option.setAttribute('data-original-tool-id', tool.originalToolId || tool.id);
                toolSelect.appendChild(option);
            });
            
            // Set default selection if available
            if (expandedTools.length > 0) {
                selectedToolId = expandedTools[0].id;
                toolSelect.value = selectedToolId;
            }
            
            console.log(`Successfully populated dropdown with ${expandedTools.length} available variants`);
        })
        .catch(error => {
            console.error('Error loading available tools:', error);
            // Show user-friendly message in dropdown if API fails
            const option = document.createElement('option');
            option.value = '';
            option.text = 'Error loading tools - please refresh page';
            option.disabled = true;
            toolSelect.appendChild(option);
        })
        .finally(() => {
            // Reset flag regardless of success or failure
            isPopulatingToolDropdown = false;
        });
}

/**
 * Filter tool dropdown based on search
 */
function filterToolDropdown() {
    const searchTerm = document.getElementById('tool-search-modal').value.toLowerCase();
    const options = document.getElementById('tool-select').options;
    
    for (let i = 0; i < options.length; i++) {
        const option = options[i];
        const text = option.text.toLowerCase();
        const model = option.getAttribute('data-model').toLowerCase();
        const serial = option.getAttribute('data-serial').toLowerCase();
        
        if (text.includes(searchTerm) || model.includes(searchTerm) || serial.includes(searchTerm)) {
            option.style.display = '';
        } else {
            option.style.display = 'none';
        }
    }
}

/**
 * Create tool preview for placement
 */
function createToolPreview() {
    if (!selectedToolId) return;
    
    // Remove existing preview
    if (toolPreview) {
        toolPreview.destroy();
    }
    
    // Get tool dimensions based on selected size
    let width, height;
    switch (selectedToolSize) {
        case '4x2':
            width = 4 * gridSize;
            height = 2 * gridSize;
            break;
        case '3x3':
            width = 3 * gridSize;
            height = 3 * gridSize;
            break;
        case '2x4':
            width = 2 * gridSize;
            height = 4 * gridSize;
            break;
        default:
            width = 4 * gridSize;
            height = 2 * gridSize;
    }
    
    // Get tool information
    const toolSelect = document.getElementById('tool-select');
    const selectedOption = toolSelect.options[toolSelect.selectedIndex];
    
    const toolName = selectedOption.text.split(' - ')[0];
    const toolType = selectedOption.getAttribute('data-type');
    
    // Get current mouse position
    const pos = stage.getPointerPosition();
    let x = 0, y = 0;
    if (pos) {
        // Convert screen position to stage position
        const transform = stage.getAbsoluteTransform().copy().invert();
        const stagePoint = transform.point(pos);
        
        // Calculate grid-aligned position
        x = Math.floor(stagePoint.x / gridSize) * gridSize;
        y = Math.floor(stagePoint.y / gridSize) * gridSize;
        
        // Center the tool on the cursor
        x = x - Math.floor(width / 2) + gridSize;
        y = y - Math.floor(height / 2) + gridSize;
    }
    
    // Create tool preview group
    toolPreview = new Konva.Group({
        x: x,
        y: y,
        opacity: 0.7,
        draggable: false,
        class: 'tool-preview',
        type: 'tool-preview'
    });
    
    // Determine fill color based on tool type
    // Get theme-aware colors
    const colors = getThemeAwareColors(toolType);
    
    // Create main rectangle
    const rect = new Konva.Rect({
        width: width,
        height: height,
        fill: colors.fill,
        stroke: colors.stroke,
        strokeWidth: 1,
        cornerRadius: 2,
        shadowColor: colors.shadowColor,
        shadowBlur: 5,
        shadowOffset: { x: 2, y: 2 },
        shadowOpacity: 0.3,
        name: 'mainRect'
    });
    
    // Format tool name for display with line breaks
    let displayName = toolName;
    if (displayName) {
        const match = displayName.match(/^([A-Za-z]+)(\d+[A-Za-z]*)$/);
        if (match) {
            const letterPrefix = match[1]; // e.g., "RAK"
            const numberSuffix = match[2]; // e.g., "151F"
            displayName = letterPrefix + '\n' + numberSuffix;
        }
    }
    
    // Create text label
    const text = new Konva.Text({
        text: displayName,
        fontSize: 14,
        fontFamily: 'Arial',
        fill: colors.textFill,
        align: 'center',
        verticalAlign: 'middle',
        width: width,
        height: height,
        lineHeight: 1.2, // Adjust line spacing for multi-line text
        name: 'text'
    });
    
    toolPreview.add(rect);
    toolPreview.add(text);
    
    toolLayer.add(toolPreview);
    toolLayer.batchDraw();
}

/**
 * Create drawing preview
 */
function createDrawingPreview() {
    // Remove existing preview
    if (drawingPreview) {
        drawingPreview.destroy();
    }
    
    // Get dimensions from inputs
    const widthUnits = parseInt(document.getElementById('drawing-width').value) || 4;
    const heightUnits = parseInt(document.getElementById('drawing-height').value) || 3;
    const width = widthUnits * gridSize;
    const height = heightUnits * gridSize;
    
    // Get current mouse position
    const pos = stage.getPointerPosition();
    let x = 0, y = 0;
    if (pos) {
        // Convert screen position to stage position
        const transform = stage.getAbsoluteTransform().copy().invert();
        const stagePoint = transform.point(pos);
        
        // Position from the top right of the cursor
        x = Math.floor(stagePoint.x / gridSize) * gridSize;
        y = Math.floor(stagePoint.y / gridSize) * gridSize;
    }
    
    // Create drawing preview group
    drawingPreview = new Konva.Group({
        x: x,
        y: y,
        opacity: 0.7,
        draggable: false,
        class: 'drawing-preview',
        type: 'drawing-preview'
    });
    
    // Determine colors based on selected color and style
    let strokeColor, fillColor;
    switch (selectedDrawingColor) {
        case 'black':
            strokeColor = '#333';
            fillColor = selectedDrawingStyle === 'solid' ? '#aaa' : 'transparent';
            break;
        case 'blue':
            strokeColor = '#0d6efd';
            fillColor = selectedDrawingStyle === 'solid' ? '#cfe2ff' : 'transparent';
            break;
        case 'green':
            strokeColor = '#198754';
            fillColor = selectedDrawingStyle === 'solid' ? '#d1e7dd' : 'transparent';
            break;
        case 'red':
            strokeColor = '#dc3545';
            fillColor = selectedDrawingStyle === 'solid' ? '#f8d7da' : 'transparent';
            break;
        case 'yellow':
            strokeColor = '#ffc107';
            fillColor = selectedDrawingStyle === 'solid' ? '#fff3cd' : 'transparent';
            break;
        default:
            strokeColor = '#333';
            fillColor = selectedDrawingStyle === 'solid' ? '#aaa' : 'transparent';
    }
    
    // Create main rectangle
    const rect = new Konva.Rect({
        width: width,
        height: height,
        fill: fillColor,
        stroke: strokeColor,
        strokeWidth: 2,
        cornerRadius: 0,
        name: 'mainRect'
    });
    
    // Create text label
    const text = new Konva.Text({
        text: selectedDrawingText || '',
        fontSize: 14,
        fontFamily: 'Arial',
        fill: strokeColor,
        align: 'center',
        verticalAlign: 'middle',
        width: width,
        height: height,
        name: 'text'
    });
    
    drawingPreview.add(rect);
    drawingPreview.add(text);
    
    toolLayer.add(drawingPreview);
    toolLayer.batchDraw();
}

/**
 * Place a tool on the grid
 */
function placeTool() {
    if (!toolPreview || !selectedToolId) return;
    
    // Get dimensions and position
    const x = toolPreview.x();
    const y = toolPreview.y();
    let width, height;
    
    switch (selectedToolSize) {
        case '4x2':
            width = 4 * gridSize;
            height = 2 * gridSize;
            break;
        case '3x3':
            width = 3 * gridSize;
            height = 3 * gridSize;
            break;
        case '2x4':
            width = 2 * gridSize;
            height = 4 * gridSize;
            break;
        default:
            width = 4 * gridSize;
            height = 2 * gridSize;
    }
    
    // Send to server
    fetch('/api/map/tool', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            toolId: selectedToolId,
            x: Math.floor(x / gridSize),
            y: Math.floor(y / gridSize),
            width: Math.floor(width / gridSize),
            height: Math.floor(height / gridSize)
        })
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('Failed to place tool');
        }
        return response.json();
    })
    .then(data => {
        // Create permanent tool shape
        createToolShape(data);
        
        // Reset tool preview
        toolPreview.destroy();
        toolPreview = null;
        
        // Reset to select mode
        setMode('select');
        
        // Refresh the tool dropdown to remove the placed tool/feed variant
        populateToolDropdown();
    })
    .catch(error => {
        console.error('Error placing tool:', error);
        alert('Failed to place tool. It may already be on the map.');
        
        // Reset tool preview
        toolPreview.destroy();
        toolPreview = null;
        
        // Reset to select mode
        setMode('select');
    });
}

/**
 * Place a drawing on the grid
 */
function placeDrawing() {
    if (!drawingPreview) return;
    
    // Get dimensions from inputs
    const widthUnits = parseInt(document.getElementById('drawing-width').value) || 4;
    const heightUnits = parseInt(document.getElementById('drawing-height').value) || 3;
    
    // Get dimensions and position
    const x = drawingPreview.x();
    const y = drawingPreview.y();
    const width = widthUnits * gridSize;
    const height = heightUnits * gridSize;
    
    // Send to server
    fetch('/api/map/drawing', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            x: Math.floor(x / gridSize),
            y: Math.floor(y / gridSize),
            width: widthUnits,
            height: heightUnits,
            text: selectedDrawingText || '',
            color: selectedDrawingColor,
            isSolid: selectedDrawingStyle === 'solid'
        })
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('Failed to place drawing');
        }
        return response.json();
    })
    .then(data => {
        // Create permanent drawing shape
        createDrawingShape(data);
        
        // Reset drawing preview
        drawingPreview.destroy();
        drawingPreview = null;
        
        // Reset to select mode
        setMode('select');
    })
    .catch(error => {
        console.error('Error placing drawing:', error);
        
        // Reset drawing preview
        drawingPreview.destroy();
        drawingPreview = null;
        
        // Reset to select mode
        setMode('select');
    });
}

/**
 * Update a drawing
 */
function updateDrawing(shape, text, color, isSolid) {
    if (!shape || shape.attrs.type !== 'drawing') return;
    
    console.log('Updating drawing:', shape.attrs.id);
    
    const drawingId = shape.attrs.id;
    
    // Get current dimensions and position
    const x = Math.floor(shape.x() / gridSize);
    const y = Math.floor(shape.y() / gridSize);
    
    // Get new width and height from the modal
    const widthUnits = parseInt(document.getElementById('edit-drawing-width').value) || 4;
    const heightUnits = parseInt(document.getElementById('edit-drawing-height').value) || 3;
    
    console.log('New dimensions:', widthUnits, 'x', heightUnits);
    console.log('New text:', text);
    console.log('New color:', color);
    console.log('Solid:', isSolid);
    
    // Send to server
    fetch(`/api/map/drawing/${drawingId}`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            x: x,
            y: y,
            width: widthUnits,
            height: heightUnits,
            text: text || '',
            color: color,
            isSolid: isSolid
        })
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('Failed to update drawing');
        }
        return response.json();
    })
    .then(data => {
        console.log('Drawing updated successfully:', data);
        
        // Update shape appearance
        const rect = shape.findOne('.mainRect');
        const textNode = shape.findOne('.text');
        
        // Update dimensions
        rect.width(widthUnits * gridSize);
        rect.height(heightUnits * gridSize);
        textNode.width(widthUnits * gridSize);
        textNode.height(heightUnits * gridSize);
        
        let strokeColor, fillColor;
        switch (color) {
            case 'black':
                strokeColor = '#333';
                fillColor = isSolid ? '#aaa' : 'transparent';
                break;
            case 'blue':
                strokeColor = '#0d6efd';
                fillColor = isSolid ? '#cfe2ff' : 'transparent';
                break;
            case 'green':
                strokeColor = '#198754';
                fillColor = isSolid ? '#d1e7dd' : 'transparent';
                break;
            case 'red':
                strokeColor = '#dc3545';
                fillColor = isSolid ? '#f8d7da' : 'transparent';
                break;
            case 'yellow':
                strokeColor = '#ffc107';
                fillColor = isSolid ? '#fff3cd' : 'transparent';
                break;
            default:
                strokeColor = '#333';
                fillColor = isSolid ? '#aaa' : 'transparent';
        }
        
        rect.stroke(strokeColor);
        rect.fill(fillColor);
        textNode.fill(strokeColor);
        textNode.text(text || '');
        
        // Update shape attributes
        shape.attrs.text = text || '';
        shape.attrs.color = color;
        shape.attrs.isSolid = isSolid;
        
        // Mark as changed
        hasChanges = true;
        updateSaveButton();
        
        // Reorder shapes after size change
        reorderShapesBySize();
    })
    .catch(error => {
        console.error('Error updating drawing:', error);
    });
}

/**
 * Delete a shape
 */
function deleteShape(shape) {
    if (!shape) return;
    
    const id = shape.attrs.id;
    
    // Send delete request to server
    fetch(`/api/map/${id}`, {
        method: 'DELETE'
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('Failed to delete item');
        }
        
        // Remove from local collections
        if (shape.attrs.type === 'tool') {
            delete toolShapes[shape.attrs.toolId];
        } else if (shape.attrs.type === 'drawing') {
            delete drawingShapes[id];
        }
        
        // Remove from stage
        shape.destroy();
        
        // Reset selection
        selectedShape = null;
        document.getElementById('delete-btn').classList.add('disabled');
        
        // Mark as changed
        hasChanges = true;
        updateSaveButton();
        
        toolLayer.batchDraw();
    })
    .catch(error => {
        console.error('Error deleting item:', error);
    });
}

/**
 * Show tooltip for a tool
 */
function showToolTooltip(shape, position) {
    const tooltip = document.getElementById('tool-tooltip');
    if (!tooltip) return;
    
    // Get tool data
    const toolId = shape.attrs.toolId;
    
    // More robust way to get tool data
    let toolData = null;
    try {
        // Ensure allToolsData is an array before using find
        if (Array.isArray(window.allToolsData)) {
            toolData = window.allToolsData.find(t => t.id == toolId);
        }
    } catch (e) {
        console.error('Error finding tool data for tooltip:', e);
    }
    
    if (!toolData) return;
    
    // Format tooltip content
    let content = `
        <strong>${toolData.name}</strong><br>
        Model: ${toolData.model || 'N/A'}<br>
        Serial: ${toolData.serial || 'N/A'}<br>
        Type: ${toolData.type || 'N/A'}<br>
        Status: ${toolData.status || 'N/A'}
    `;
    
    tooltip.innerHTML = content;
    tooltip.style.display = 'block';
    tooltip.style.left = (position.x + 10) + 'px';
    tooltip.style.top = (position.y + 10) + 'px';
}

/**
 * Hide tool tooltip
 */
function hideToolTooltip() {
    const tooltip = document.getElementById('tool-tooltip');
    if (tooltip) {
        tooltip.style.display = 'none';
    }
}

/**
 * Reorder shapes by size - smaller shapes appear on top
 */
function reorderShapesBySize() {
    // Get all shapes
    const shapes = toolLayer.getChildren();
    
    // Calculate the area of each shape
    const shapesWithArea = shapes.map(shape => {
        const rect = shape.findOne('.mainRect');
        if (!rect) return { shape, area: 0 };
        
        const area = rect.width() * rect.height();
        return { shape, area };
    });
    
    // Sort by area (largest first)
    shapesWithArea.sort((a, b) => b.area - a.area);
    
    // Reorder shapes in the layer (larger at back, smaller at front)
    shapesWithArea.forEach((item, index) => {
        item.shape.zIndex(index);
    });
    
    // Redraw the layer
    toolLayer.batchDraw();
}

/**
 * Get theme-aware colors for tools based on current theme and tool type
 */
function getThemeAwareColors(toolType) {
    const isDarkMode = document.documentElement.getAttribute('data-bs-theme') === 'dark';
    
    if (isDarkMode) {
        // Dark mode colors
        switch (toolType) {
            case 'CHEMBLEND':
                return {
                    fill: '#4a1a5c',        // Dark purple background
                    stroke: '#5e226e',      // Dark purple outline
                    textFill: '#c084d1',    // Light purple text
                    shadowColor: 'rgba(0,0,0,0.5)'
                };
            case 'SLURRY':
                return {
                    fill: '#0d4f47',        // Dark teal background
                    stroke: '#1a5d55',      // Dark teal outline
                    textFill: '#4fd1c7',    // Light teal text
                    shadowColor: 'rgba(0,0,0,0.5)'
                };
            default:
                return {
                    fill: '#1a3a5c',        // Dark blue background
                    stroke: '#2a4a6c',      // Dark blue outline
                    textFill: '#87ceeb',    // Light blue text
                    shadowColor: 'rgba(0,0,0,0.5)'
                };
        }
    } else {
        // Light mode colors (original)
        switch (toolType) {
            case 'CHEMBLEND':
                return {
                    fill: '#8e44ad',        // Purple
                    stroke: '#333',
                    textFill: 'black',
                    shadowColor: 'black'
                };
            case 'SLURRY':
                return {
                    fill: '#16a085',        // Teal
                    stroke: '#333',
                    textFill: 'black',
                    shadowColor: 'black'
                };
            default:
                return {
                    fill: '#3498db',        // Default blue
                    stroke: '#333',
                    textFill: 'black',
                    shadowColor: 'black'
                };
        }
    }
}

/**
 * Create a tool shape from data
 */
function createToolShape(data) {
    // Calculate grid positions
    const x = data.x * gridSize;
    const y = data.y * gridSize;
    const width = data.width * gridSize;
    const height = data.height * gridSize;
    
    // Get tool details
    const toolId = data.tool ? data.tool.id : data.toolId;
    
    // More robust way to get tool data
    let toolData = null;
    try {
        // Ensure allToolsData is an array before using find
        if (Array.isArray(window.allToolsData)) {
            toolData = window.allToolsData.find(t => t.id == toolId);
        } else {
            console.warn('allToolsData is not an array, using data directly');
        }
    } catch (e) {
        console.error('Error finding tool data:', e);
    }
    
    // Fallback if tool data not found
    if (!toolData) {
        console.warn(`Tool data not found for ID: ${toolId}, using fallback data`);
        // Use data directly if available, or create minimal default data
        if (data.tool) {
            toolData = {
                id: data.tool.id,
                name: data.tool.name || 'Unknown Tool',
                type: data.tool.toolType || '',
                model: data.tool.model1 || '',
                serial: data.tool.serialNumber1 || '',
                status: data.tool.status || ''
            };
        } else {
            toolData = {
                id: toolId,
                name: 'Tool ' + toolId,
                type: '',
                model: '',
                serial: '',
                status: ''
            };
        }
    }
    
    // Create group for the tool
    const group = new Konva.Group({
        x: x,
        y: y,
        id: data.id,
        toolId: toolId,
        type: 'tool',
        toolType: toolData.type,
        draggable: false
    });
    
    // Determine fill color based on tool type
    // Get theme-aware colors
    const colors = getThemeAwareColors(toolData.type);
    let cssClass = '';
    
    if (toolData.type === 'CHEMBLEND') {
        cssClass = 'purple-tool';
    } else if (toolData.type === 'SLURRY') {
        cssClass = 'teal-tool';
    }
    
    // Create main rectangle
    const rect = new Konva.Rect({
        width: width,
        height: height,
        fill: colors.fill,
        stroke: colors.stroke,
        strokeWidth: 1,
        cornerRadius: 2,
        shadowColor: colors.shadowColor,
        shadowBlur: 5,
        shadowOffset: { x: 2, y: 2 },
        shadowOpacity: 0.3,
        name: 'mainRect',
        class: 'tool-shape ' + cssClass
    });
    
    // Format tool name for display
    let displayName = toolData.name;
    
    // Check if this is a feed tool (indicated by the 'text' field being 'FEED')
    const isFeedTool = data.text === 'FEED';
    if (isFeedTool && displayName.toUpperCase().endsWith('D')) {
        // Convert tool name from 'D' ending to 'F' ending for feed tools
        displayName = displayName.slice(0, -1) + 'F';
        console.log(`Displaying feed tool: ${displayName} (original: ${toolData.name})`);
    }
    
    // Add line break between letter prefix and number+letter suffix
    // Example: "RAK151F" becomes "RAK\n151F"
    if (displayName) {
        const match = displayName.match(/^([A-Za-z]+)(\d+[A-Za-z]*)$/);
        if (match) {
            const letterPrefix = match[1]; // e.g., "RAK"
            const numberSuffix = match[2]; // e.g., "151F"
            displayName = letterPrefix + '\n' + numberSuffix;
        }
    }
    
    // Create text label
    const text = new Konva.Text({
        text: displayName,
        fontSize: 14,
        fontFamily: 'Arial',
        fill: colors.textFill,
        align: 'center',
        verticalAlign: 'middle',
        width: width,
        height: height,
        lineHeight: 1.2, // Adjust line spacing for multi-line text
        name: 'text'
    });
    
    group.add(rect);
    group.add(text);
    
    // Store the mainRect for easy access during highlighting
    group.mainRect = rect;
    
    toolLayer.add(group);
    
    // Store reference for highlighting
    toolShapes[toolId] = group;
    
    // Reorder shapes by size
    reorderShapesBySize();
}

/**
 * Create a drawing shape from data
 */
function createDrawingShape(data) {
    // Calculate grid positions
    const x = data.x * gridSize;
    const y = data.y * gridSize;
    const width = data.width * gridSize;
    const height = data.height * gridSize;
    
    // Create group for the drawing
    const group = new Konva.Group({
        x: x,
        y: y,
        id: data.id,
        type: 'drawing',
        text: data.text,
        color: data.color || 'black',
        isSolid: data.isSolid,
        draggable: false
    });
    
    // Determine colors based on selected color and style
    let strokeColor, fillColor;
    switch (data.color) {
        case 'black':
            strokeColor = '#333';
            fillColor = data.isSolid ? '#aaa' : 'transparent';
            break;
        case 'blue':
            strokeColor = '#0d6efd';
            fillColor = data.isSolid ? '#cfe2ff' : 'transparent';
            break;
        case 'green':
            strokeColor = '#198754';
            fillColor = data.isSolid ? '#d1e7dd' : 'transparent';
            break;
        case 'red':
            strokeColor = '#dc3545';
            fillColor = data.isSolid ? '#f8d7da' : 'transparent';
            break;
        case 'yellow':
            strokeColor = '#ffc107';
            fillColor = data.isSolid ? '#fff3cd' : 'transparent';
            break;
        default:
            strokeColor = '#333';
            fillColor = data.isSolid ? '#aaa' : 'transparent';
    }
    
    // Create main rectangle
    const rect = new Konva.Rect({
        width: width,
        height: height,
        fill: fillColor,
        stroke: strokeColor,
        strokeWidth: 2,
        cornerRadius: 0,
        shadowColor: null,
        shadowBlur: 0,
        shadowOffset: { x: 0, y: 0 },
        shadowOpacity: 0,
        name: 'mainRect'
    });
    
    // Create text label
    const text = new Konva.Text({
        text: data.text || '',
        fontSize: 14,
        fontFamily: 'Arial',
        fill: strokeColor,
        align: 'center',
        verticalAlign: 'middle',
        width: width,
        height: height,
        name: 'text'
    });
    
    group.add(rect);
    group.add(text);
    
    toolLayer.add(group);
    
    // Store reference
    drawingShapes[data.id] = group;
    
    // Reorder shapes by size
    reorderShapesBySize();
}

/**
 * Prepare the edit drawing modal with the shape's values
 */
function prepareEditDrawingModal(shape) {
    if (!shape || shape.attrs.type !== 'drawing') return;
    
    // Set current values
    document.getElementById('edit-drawing-text').value = shape.attrs.text || '';
    
    // Set size values - convert from pixels to grid units
    const rect = shape.findOne('.mainRect');
    const widthUnits = Math.round(rect.width() / gridSize);
    const heightUnits = Math.round(rect.height() / gridSize);
    document.getElementById('edit-drawing-width').value = widthUnits;
    document.getElementById('edit-drawing-height').value = heightUnits;
    
    // Set color
    document.querySelectorAll('.edit-color-btn').forEach(btn => btn.classList.remove('active'));
    const colorBtn = document.querySelector(`.edit-color-btn[data-color="${shape.attrs.color || 'black'}"]`);
    if (colorBtn) colorBtn.classList.add('active');
    
    // Set style
    const isSolid = shape.attrs.isSolid;
    if (isSolid) {
        document.getElementById('edit-style-solid').checked = true;
    } else {
        document.getElementById('edit-style-hollow').checked = true;
    }
    
    // Store the shape reference for the update button
    window.currentEditingShape = shape;
}

/**
 * Refresh tool colors when theme changes
 */
function refreshToolColors() {
    // Update all tool shapes
    Object.values(toolShapes).forEach(group => {
        const toolData = {
            type: group.attrs.toolType || 'DEFAULT'
        };
        const colors = getThemeAwareColors(toolData.type);
        
        const rect = group.findOne('.mainRect');
        const text = group.findOne('.text');
        
        if (rect) {
            rect.fill(colors.fill);
            rect.stroke(colors.stroke);
            rect.shadowColor(colors.shadowColor);
        }
        
        if (text) {
            text.fill(colors.textFill);
        }
    });
    
    // Update tool preview if it exists
    if (toolPreview) {
        const toolType = toolPreview.attrs.toolType || 'DEFAULT';
        const colors = getThemeAwareColors(toolType);
        
        const rect = toolPreview.findOne('.mainRect');
        const text = toolPreview.findOne('.text');
        
        if (rect) {
            rect.fill(colors.fill);
            rect.stroke(colors.stroke);
            rect.shadowColor(colors.shadowColor);
        }
        
        if (text) {
            text.fill(colors.textFill);
        }
    }
    
    // Redraw the layer
    if (toolLayer) {
        toolLayer.batchDraw();
    }
}

/**
 * Initialize the document
 */
document.addEventListener('DOMContentLoaded', function() {
    // Initialize grid and controls
    initializeGrid();
    
    // Add direct event listener for all modals to ensure proper closing
    document.querySelectorAll('.modal').forEach(modalElement => {
        modalElement.addEventListener('hidden.bs.modal', function() {
            const modalBackdrop = document.querySelector('.modal-backdrop');
            if (modalBackdrop) {
                modalBackdrop.remove();
            }
        });
    });
    
    // Listen for theme changes to refresh tool colors
    const observer = new MutationObserver(function(mutations) {
        mutations.forEach(function(mutation) {
            if (mutation.type === 'attributes' && mutation.attributeName === 'data-bs-theme') {
                refreshToolColors();
            }
        });
    });
    
    observer.observe(document.documentElement, {
        attributes: true,
        attributeFilter: ['data-bs-theme']
    });
});

// Function to filter the list and update grid highlights
function applyToolFiltersAndSearch() {
    const searchTerm = toolSearchInput.value.toLowerCase();
    const selectedType = filterTypeSelect.value;
    const selectedStatus = filterStatusSelect.value;
    let visibleListCount = 0;
    visibleToolIds.clear(); // Reset the visible tools set
    
    // Check if any filters are active - empty strings count as no filter
    const hasActiveFilters = 
        (searchTerm && searchTerm.length > 0) || 
        (selectedType && selectedType.length > 0) || 
        (selectedStatus && selectedStatus.length > 0);

    // --- Filter HTML List --- 
    toolItems.forEach(item => {
        const toolType = item.dataset.type || '';
        const toolStatus = item.dataset.status || '';
        const toolName = item.querySelector('a')?.textContent?.toLowerCase() || '';
        const toolSerial = item.dataset.serial?.toLowerCase() || '';
        const toolModel = item.dataset.model?.toLowerCase() || '';
        const toolSecondaryName = item.dataset.secondaryName?.toLowerCase() || ''; // Get secondary name
        const toolId = item.dataset.toolId;
        
        const typeMatch = !selectedType || toolType === selectedType;
        const statusMatch = !selectedStatus || toolStatus === selectedStatus;
        const searchMatch = !searchTerm || 
                            toolName.includes(searchTerm) || 
                            toolSecondaryName.includes(searchTerm) || // Include secondary name in search
                            toolSerial.includes(searchTerm) || 
                            toolModel.includes(searchTerm);

        if (typeMatch && statusMatch && searchMatch) {
            item.style.display = 'block'; 
            item.classList.remove('tool-hidden');
            visibleListCount++;
            // Only add to visible set if we have active filters
            if (toolId && hasActiveFilters) visibleToolIds.add(toolId);
        } else {
            item.style.display = 'none';
            item.classList.add('tool-hidden');
        }
    });
    
    // Show/hide the "No tools found" message in the list
    if (noToolsMessage) {
        noToolsMessage.style.display = visibleListCount === 0 ? 'block' : 'none';
    }
    
    console.log('Visible tool IDs:', Array.from(visibleToolIds));
    console.log('Total tools:', totalToolCount, 'Showing:', visibleToolIds.size, 'Filtered:', hasActiveFilters);
    
    // Update grid highlights
    updateGridShapesHighlight();
} 