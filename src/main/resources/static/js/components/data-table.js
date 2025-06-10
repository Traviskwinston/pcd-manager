/**
 * Reusable Data Table Component JavaScript
 * Provides sorting, searching, horizontal scrolling, and row click functionality
 * 
 * Usage:
 * const dataTable = new DataTable({
 *   container: '.data-table-responsive',
 *   searchInput: '#searchInput',
 *   clearButton: '#clearSearch',
 *   rowSelector: '.data-table-row',
 *   linkSelector: 'a[href]',
 *   sortColumns: ['name', 'date', 'status'], // Define sortable columns
 *   interactiveElements: 'a, button, input, .dropdown, [data-bs-toggle], .interactive-icon'
 * });
 */

class DataTable {
    constructor(options = {}) {
        // Default configuration
        this.config = {
            container: '.data-table-responsive',
            searchInput: '#searchInput',
            clearButton: '#clearSearch',
            rowSelector: '.data-table-row',
            linkSelector: 'a[href]',
            sortColumns: [],
            interactiveElements: 'a, button, input, .dropdown, [data-bs-toggle], .interactive-icon',
            searchAttributes: ['data-search'], // Attributes to search in
            enableRowClick: true,
            enableHorizontalScroll: true,
            enableSorting: true,
            enableSearch: true,
            ...options
        };

        // State
        this.currentSort = null;
        this.sortDirection = 'asc';
        this.currentSearch = '';

        // Initialize
        this.init();
    }

    init() {
        this.container = document.querySelector(this.config.container);
        if (!this.container) return;

        if (this.config.enableSearch) this.initSearch();
        if (this.config.enableSorting) this.initSorting();
        if (this.config.enableHorizontalScroll) this.initHorizontalScroll();
        if (this.config.enableRowClick) this.initRowClick();
        this.initDropdownFix();
    }

    // Search functionality
    initSearch() {
        const searchInput = document.querySelector(this.config.searchInput);
        const clearButton = document.querySelector(this.config.clearButton);

        if (searchInput) {
            searchInput.addEventListener('input', (e) => {
                this.currentSearch = e.target.value.toLowerCase();
                this.filterTable();
            });
        }

        if (clearButton) {
            clearButton.addEventListener('click', () => {
                if (searchInput) searchInput.value = '';
                this.currentSearch = '';
                this.filterTable();
            });
        }
    }

    // Sorting functionality
    initSorting() {
        document.querySelectorAll('.sortable-header').forEach(header => {
            header.addEventListener('click', (e) => {
                // Ignore clicks on dropdowns or their children
                if (e.target.closest('[data-bs-toggle="dropdown"]') || 
                    e.target.closest('.dropdown') || 
                    e.target.closest('.dropdown-menu')) {
                    return;
                }
                
                const sortType = header.getAttribute('data-sort');
                this.handleSort(sortType, header);
            });
        });
    }

    // Enhanced horizontal scrolling
    initHorizontalScroll() {
        // Shift + mouse wheel for horizontal scrolling
        this.container.addEventListener('wheel', (e) => {
            if (e.shiftKey) {
                e.preventDefault();
                this.container.scrollLeft += e.deltaY;
            }
        });

        // Click and drag to scroll horizontally
        let isDown = false;
        let startX;
        let scrollLeft;
        let hasDragged = false;

        this.container.addEventListener('mousedown', (e) => {
            if (e.target.closest(this.config.interactiveElements)) {
                return;
            }

            isDown = true;
            hasDragged = false;
            this.container.classList.add('dragging');
            startX = e.pageX - this.container.offsetLeft;
            scrollLeft = this.container.scrollLeft;
        });

        this.container.addEventListener('mouseleave', () => {
            isDown = false;
            this.container.classList.remove('dragging');
            setTimeout(() => { hasDragged = false; }, 100);
        });

        this.container.addEventListener('mouseup', () => {
            isDown = false;
            this.container.classList.remove('dragging');
            setTimeout(() => { hasDragged = false; }, 100);
        });

        this.container.addEventListener('mousemove', (e) => {
            if (!isDown) return;
            e.preventDefault();
            const x = e.pageX - this.container.offsetLeft;
            const walk = (x - startX) * 2;

            if (Math.abs(walk) > 5) {
                hasDragged = true;
            }

            this.container.scrollLeft = scrollLeft - walk;
        });

        // Keyboard arrow keys for horizontal scrolling
        document.addEventListener('keydown', (e) => {
            if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

            if (e.key === 'ArrowLeft' && e.shiftKey) {
                e.preventDefault();
                this.container.scrollLeft -= 50;
            } else if (e.key === 'ArrowRight' && e.shiftKey) {
                e.preventDefault();
                this.container.scrollLeft += 50;
            }
        });

        // Store hasDragged state for row click detection
        this.hasDragged = () => hasDragged;
    }

    // Clickable row functionality
    initRowClick() {
        const rows = document.querySelectorAll(this.config.rowSelector);
        rows.forEach(row => {
            row.addEventListener('click', (e) => {
                // Don't navigate if we just finished dragging
                if (this.hasDragged && this.hasDragged()) {
                    return;
                }

                // Don't navigate if clicking on interactive elements
                if (e.target.closest(this.config.interactiveElements)) {
                    return;
                }

                // Get the link from the row
                const link = row.querySelector(this.config.linkSelector);
                if (link) {
                    const href = link.getAttribute('href');
                    if (href) {
                        window.location.href = href;
                    }
                }
            });
        });
    }

    // Fix dropdown functionality in responsive tables
    initDropdownFix() {
        const dropdowns = document.querySelectorAll('[data-bs-toggle="dropdown"]');
        dropdowns.forEach(dropdown => {
            dropdown.addEventListener('click', (e) => {
                e.stopPropagation();
                e.preventDefault();
                const dropdownInstance = bootstrap.Dropdown.getInstance(dropdown) || new bootstrap.Dropdown(dropdown);
                dropdownInstance.toggle();
            });
        });
    }

    // Handle sorting
    handleSort(sortType, header) {
        // Remove sort classes from all headers
        document.querySelectorAll('.sortable-header').forEach(h => {
            h.classList.remove('sort-asc', 'sort-desc');
        });

        // Determine sort direction
        if (this.currentSort === sortType) {
            this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
        } else {
            this.sortDirection = 'asc';
        }

        this.currentSort = sortType;
        header.classList.add(this.sortDirection === 'asc' ? 'sort-asc' : 'sort-desc');

        this.sortTable(sortType, this.sortDirection);
    }

    // Sort table rows
    sortTable(sortType, direction) {
        const tbody = this.container.querySelector('tbody');
        const rows = Array.from(document.querySelectorAll(this.config.rowSelector));

        rows.sort((a, b) => {
            let aVal = a.getAttribute(`data-${sortType}`) || '';
            let bVal = b.getAttribute(`data-${sortType}`) || '';

            // Handle date sorting
            if (sortType.includes('date')) {
                aVal = aVal ? new Date(aVal) : new Date('9999-12-31');
                bVal = bVal ? new Date(bVal) : new Date('9999-12-31');
                return direction === 'asc' ? aVal - bVal : bVal - aVal;
            }

            // String sorting
            return direction === 'asc' ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal);
        });

        // Re-append sorted rows
        if (tbody) {
            tbody.innerHTML = '';
            rows.forEach(row => tbody.appendChild(row));
        }
    }

    // Filter table based on search
    filterTable() {
        const rows = document.querySelectorAll(this.config.rowSelector);

        rows.forEach(row => {
            let matchesSearch = !this.currentSearch;

            if (this.currentSearch) {
                // Search in specified data attributes
                for (const attr of this.config.searchAttributes) {
                    const value = row.getAttribute(attr);
                    if (value && value.toLowerCase().includes(this.currentSearch)) {
                        matchesSearch = true;
                        break;
                    }
                }

                // If no data attributes specified, search in text content
                if (!matchesSearch && this.config.searchAttributes.length === 0) {
                    const textContent = row.textContent.toLowerCase();
                    matchesSearch = textContent.includes(this.currentSearch);
                }
            }

            row.style.display = matchesSearch ? '' : 'none';
        });

        // Trigger custom event for search results
        const event = new CustomEvent('dataTableFiltered', {
            detail: { searchTerm: this.currentSearch, visibleRows: document.querySelectorAll(`${this.config.rowSelector}:not([style*="display: none"])`) }
        });
        document.dispatchEvent(event);
    }

    // Public API methods
    refresh() {
        this.init();
    }

    setSearchTerm(term) {
        this.currentSearch = term.toLowerCase();
        const searchInput = document.querySelector(this.config.searchInput);
        if (searchInput) searchInput.value = term;
        this.filterTable();
    }

    clearSearch() {
        this.setSearchTerm('');
    }

    destroy() {
        // Remove event listeners and clean up
        this.container.classList.remove('dragging');
        // Additional cleanup could be added here
    }
}

// Export for use as module or global
if (typeof module !== 'undefined' && module.exports) {
    module.exports = DataTable;
} else {
    window.DataTable = DataTable;
} 