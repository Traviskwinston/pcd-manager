# Data Table Component System

This reusable component system provides a consistent way to create feature-rich data tables across the application. It includes dark mode support, sorting, searching, horizontal scrolling, and clickable rows.

## 📁 Files Structure

```
src/main/resources/
├── static/
│   ├── css/components/data-table.css    # Reusable CSS styles
│   └── js/components/data-table.js      # JavaScript functionality
└── templates/fragments/data-table.html  # Thymeleaf fragments
```

## 🎯 Features

- **Responsive Design**: Tables adapt to narrow screens with horizontal scrolling
- **Dark Mode**: Full dark mode support with theme-aware colors
- **Sorting**: Click headers to sort columns ascending/descending
- **Search**: Real-time search across table data
- **Horizontal Scrolling**: Enhanced scrolling with Shift+wheel, click+drag, Shift+arrows
- **Clickable Rows**: Navigate to detail pages by clicking rows
- **Status Badges**: Consistent status styling across all tables
- **Filter Dropdowns**: Support for column-based filtering

## 🚀 Quick Start

### 1. Include Required Files

In your Thymeleaf template head section:

```html
<!-- CSS -->
<link th:href="@{/css/components/data-table.css}" rel="stylesheet">
<link th:href="@{/css/dark-mode.css}" rel="stylesheet">

<!-- JavaScript (before closing body tag) -->
<script th:src="@{/js/components/data-table.js}"></script>
<script th:src="@{/js/theme-toggle.js}"></script>
```

### 2. Basic Usage

```html
<!-- In your controller, prepare column definitions -->
<!-- List<Map<String, Object>> columns = Arrays.asList(
    Map.of("title", "Name", "field", "name", "sortable", true, "width", "20%"),
    Map.of("title", "Status", "field", "status", "sortable", true, "width", "15%"),
    Map.of("title", "Actions", "field", "actions", "sortable", false, "width", "10%")
); -->

<!-- In your template -->
<div th:replace="fragments/data-table :: data-table(
    id='my-table',
    searchPlaceholder='Search items...',
    columns=${columns},
    rows=${items},
    rowTemplate='my-page/row-template'
)"></div>
```

### 3. Create Row Template

Create a separate fragment for your table rows:

```html
<!-- templates/my-page/row-template.html -->
<tr th:fragment="row(item)" 
    class="data-table-row"
    th:data-search="${item.name + ' ' + item.status}"
    th:data-name="${item.name}"
    th:data-status="${item.status}">
    
    <!-- Hidden link for row navigation -->
    <a th:href="@{'/items/' + ${item.id}}" style="display: none;"></a>
    
    <td th:text="${item.name}">Name</td>
    <td>
        <span th:replace="fragments/data-table :: status-badge(${item.status}, ${item.statusType})"></span>
    </td>
    <td>
        <div th:replace="fragments/data-table :: action-icons(${item.actions})"></div>
    </td>
</tr>
```

### 4. Initialize JavaScript

```html
<script>
document.addEventListener('DOMContentLoaded', function() {
    const dataTable = new DataTable({
        container: '#my-table-container',
        searchInput: '#my-table-search',
        clearButton: '#my-table-clear',
        rowSelector: '.data-table-row',
        searchAttributes: ['data-search'],
        enableRowClick: true
    });
});
</script>
```

## 🛠️ Advanced Configuration

### Column Definition Options

```javascript
const columns = [
    {
        title: "Column Header",        // Display text
        field: "fieldName",           // Data field name (for sorting)
        sortable: true,               // Enable sorting
        filterable: false,            // Enable filter dropdown
        width: "20%",                 // Column width
        filterClass: "filter-header", // Additional CSS class
        filterOptions: [              // Dropdown options (if filterable)
            { value: "active", label: "Active" },
            { value: "inactive", label: "Inactive" }
        ]
    }
];
```

### JavaScript Configuration Options

```javascript
const dataTable = new DataTable({
    container: '.data-table-responsive',           // Table container selector
    searchInput: '#searchInput',                   // Search input selector
    clearButton: '#clearSearch',                   // Clear button selector
    rowSelector: '.data-table-row',                // Row selector
    linkSelector: 'a[href]',                       // Link selector for navigation
    searchAttributes: ['data-search'],             // Attributes to search in
    interactiveElements: 'a, button, .dropdown',   // Elements that shouldn't trigger row click
    enableRowClick: true,                          // Enable clickable rows
    enableHorizontalScroll: true,                  // Enable enhanced scrolling
    enableSorting: true,                           // Enable sorting
    enableSearch: true                             // Enable search
});
```

### Status Badge Types

```html
<!-- Available badge types -->
<span th:replace="fragments/data-table :: status-badge('Active', 'success')"></span>
<span th:replace="fragments/data-table :: status-badge('Pending', 'warning')"></span>
<span th:replace="fragments/data-table :: status-badge('Error', 'danger')"></span>
<span th:replace="fragments/data-table :: status-badge('Info', 'info')"></span>
<span th:replace="fragments/data-table :: status-badge('Default', 'secondary')"></span>
```

### Action Icons

```javascript
// In your controller, prepare actions
const actions = [
    {
        type: "link",
        url: "/items/" + item.getId() + "/edit",
        icon: "fas fa-edit",
        title: "Edit",
        class: "text-primary"
    },
    {
        type: "button",
        icon: "fas fa-trash",
        title: "Delete",
        class: "text-danger",
        onClick: "deleteItem(" + item.getId() + ")"
    }
];
```

## 🎨 Styling Customization

### CSS Custom Properties

You can override the default colors by defining CSS custom properties:

```css
:root {
    --data-table-border-color: #dee2e6;
    --data-table-hover-bg: rgba(var(--bs-primary-rgb), 0.1);
    --data-table-sort-color: #0d6efd;
}

[data-bs-theme="dark"] {
    --data-table-border-color: var(--bs-border-color);
    --data-table-hover-bg: rgba(255, 255, 255, 0.075);
    --data-table-sort-color: #5ba3f5;
}
```

### Custom Column Classes

Add custom classes to specific columns:

```html
<td class="allow-wrap">Long text that should wrap</td>
<td class="text-center">Centered content</td>
<td class="text-end">Right-aligned content</td>
```

## 📝 Complete Example: Tools Page

Here's how to apply this to the Tools page:

### 1. Update ToolsController

```java
@GetMapping("/tools")
public String listTools(Model model) {
    List<Tool> tools = toolService.findAll();
    
    // Prepare column definitions
    List<Map<String, Object>> columns = Arrays.asList(
        Map.of("title", "Name", "field", "name", "sortable", true, "width", "25%"),
        Map.of("title", "Type", "field", "type", "sortable", true, "width", "15%"),
        Map.of("title", "Location", "field", "location", "sortable", true, "width", "20%"),
        Map.of("title", "Status", "field", "status", "sortable", true, "filterable", true, "width", "15%"),
        Map.of("title", "Last Updated", "field", "lastUpdated", "sortable", true, "width", "15%"),
        Map.of("title", "Actions", "field", "actions", "sortable", false, "width", "10%")
    );
    
    model.addAttribute("columns", columns);
    model.addAttribute("tools", tools);
    return "tools/list";
}
```

### 2. Create tools/list.html

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" 
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/main}">
<head>
    <title>Tools</title>
    <link th:href="@{/css/components/data-table.css}" rel="stylesheet">
</head>
<body>
    <div layout:fragment="content">
        <div class="container-fluid">
            <div class="d-flex justify-content-between align-items-center mb-4">
                <h2>Tools Management</h2>
                <a th:href="@{/tools/add}" class="btn btn-primary">
                    <i class="fas fa-plus"></i> Add Tool
                </a>
            </div>
            
            <!-- Data Table Component -->
            <div th:replace="fragments/data-table :: data-table(
                id='tools-table',
                searchPlaceholder='Search tools by name, type, location...',
                columns=${columns},
                rows=${tools},
                rowTemplate='tools/row-template'
            )"></div>
        </div>
    </div>
    
    <th:block layout:fragment="scripts">
        <script th:src="@{/js/components/data-table.js}"></script>
        <script>
        document.addEventListener('DOMContentLoaded', function() {
            const toolsTable = new DataTable({
                container: '#tools-table-container',
                searchInput: '#tools-table-search',
                clearButton: '#tools-table-clear',
                rowSelector: '.data-table-row',
                searchAttributes: ['data-search'],
                enableRowClick: true
            });
        });
        </script>
    </th:block>
</body>
</html>
```

### 3. Create tools/row-template.html

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
    <tr th:fragment="row(tool)" 
        class="data-table-row"
        th:data-search="${tool.name + ' ' + tool.type + ' ' + tool.location?.name}"
        th:data-name="${tool.name}"
        th:data-type="${tool.type}"
        th:data-status="${tool.status}">
        
        <!-- Hidden link for row navigation -->
        <a th:href="@{'/tools/' + ${tool.id}}" style="display: none;"></a>
        
        <td th:text="${tool.name}">Tool Name</td>
        <td th:text="${tool.type}">Type</td>
        <td th:text="${tool.location?.name ?: 'No Location'}">Location</td>
        <td>
            <span th:replace="fragments/data-table :: status-badge(
                ${tool.status}, 
                ${tool.status == 'Available' ? 'success' : (tool.status == 'In Use' ? 'warning' : 'secondary')}
            )"></span>
        </td>
        <td th:text="${#temporals.format(tool.lastUpdated, 'yyyy-MM-dd')}">Date</td>
        <td>
            <div th:replace="fragments/data-table :: action-icons(${tool.actions})"></div>
        </td>
    </tr>
</body>
</html>
```

## 🔧 Troubleshooting

### Common Issues

1. **Sorting not working**: Ensure data attributes match column field names
2. **Row clicks not working**: Check that the hidden link is present in each row
3. **Search not finding results**: Verify `data-search` attributes contain searchable text
4. **Dropdowns not working**: Make sure Bootstrap JS is loaded and dropdowns are initialized

### Debug Mode

Enable debug logging by adding to your JavaScript:

```javascript
const dataTable = new DataTable({
    // ... other options
    debug: true  // Enables console logging
});
```

This component system provides a solid foundation for creating consistent, feature-rich data tables throughout your application while maintaining clean, maintainable code. 