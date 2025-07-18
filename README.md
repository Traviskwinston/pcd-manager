# PCD Manager

A comprehensive manufacturing management system for tracking Return Merchandise Authorization (RMA), tools, parts, and facility operations. Built with Spring Boot and modern web technologies.

## Table of Contents

- [Quick Overview](#quick-overview)
- [Detailed Documentation](#detailed-documentation)
  - [System Architecture](#system-architecture)
  - [Core Features](#core-features)
  - [Page Walkthrough](#page-walkthrough)
  - [Technology Stack](#technology-stack)
  - [Code Structure](#code-structure)
  - [Frontend Architecture](#frontend-architecture)
  - [Backend Architecture](#backend-architecture)
- [Setup Instructions](#setup-instructions)
- [Configuration](#configuration)
- [Development](#development)

---

## Quick Overview

**PCD Manager** is an enterprise-grade manufacturing management system designed for tracking and managing complex industrial operations. 

### Core Modules (2-minute overview)
- **🔧 RMA Management**: Complete lifecycle tracking of return merchandise authorization requests
- **🛠️ Tool Management**: Real-time tool tracking with status monitoring and checklist management
- **📊 Dashboard**: Interactive facility mapping with real-time status indicators
- **👥 User Management**: Role-based access control with location-based permissions
- **📋 Passdown System**: Shift-to-shift communication and documentation
- **📈 Track/Trend Analysis**: Problem tracking and trend analysis for continuous improvement
- **🏭 Location Management**: Multi-facility support with hierarchical organization
- **📁 Document Management**: Centralized file storage with automatic organization

### Tech Stack Summary
- **Backend**: Spring Boot 3.3.1, Spring Security, JPA/Hibernate
- **Frontend**: Thymeleaf, Bootstrap 5, JavaScript ES6+
- **Database**: MySQL/H2 with connection pooling
- **Build**: Maven 3.x, Java 17

---

## Detailed Documentation

## System Architecture

PCD Manager follows a modern MVC architecture with clean separation of concerns:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Presentation  │    │    Business     │    │   Data Access   │
│     Layer       │    │     Layer       │    │     Layer       │
│                 │    │                 │    │                 │
│ • Controllers   │───▶│ • Services      │───▶│ • Repositories  │
│ • Templates     │    │ • Validation    │    │ • Entities      │
│ • Static Assets │    │ • Security      │    │ • Database      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Core Features

### 1. RMA Management System
- **Complete Lifecycle Tracking**: From request creation to resolution
- **Document Management**: Automatic file organization with metadata
- **Status Workflows**: Configurable approval and tracking workflows
- **Customer Integration**: Customer information and communication tracking
- **Parts Integration**: Direct linking to parts inventory and movements

### 2. Tool Management
- **Real-time Status Tracking**: Live updates on tool locations and status
- **Checklist Management**: Customizable checklists for maintenance and quality
- **Assignment System**: Tool assignment to technicians and projects
- **History Tracking**: Complete audit trail of tool movements and changes
- **Integration Points**: Connected to RMA, parts, and project systems

### 3. Interactive Dashboard
- **Facility Mapping**: Visual facility layout with drag-and-drop functionality
- **Real-time Updates**: Live status indicators and notifications
- **Multi-view Support**: Different views for different roles and requirements
- **Quick Actions**: Direct access to common operations from the dashboard

### 4. Advanced User Management
- **Role-based Security**: Granular permissions with role inheritance
- **Location-based Access**: Users can be restricted to specific facilities
- **Profile Management**: Comprehensive user profiles with preferences
- **Session Management**: Secure session handling with timeout protection

## Page Walkthrough

### Dashboard (`/dashboard`)
The central hub featuring:
- **Interactive Facility Map**: Visual representation of the facility with tool locations
- **Status Widgets**: Real-time counters for RMAs, tools, and pending tasks
- **Quick Actions Panel**: Fast access to create new RMAs, tools, or passdowns
- **Recent Activity Feed**: Latest updates across all modules
- **Performance Metrics**: Key performance indicators and trends

### RMA Management (`/rma`)
Comprehensive RMA handling:
- **RMA List View**: Advanced filtering and search capabilities
- **Detailed RMA View**: Complete information with 3x3 grid layout
- **Create/Edit Forms**: Intuitive forms with validation and auto-completion
- **Document Upload**: Drag-and-drop file uploads with preview
- **Status Tracking**: Visual status indicators and workflow progression

### Tool Management (`/tools`)
Advanced tool tracking:
- **Tool Inventory**: Complete tool listing with status and location
- **Detailed Tool View**: Comprehensive tool information with related data
- **Checklist Management**: Interactive checklists with date tracking
- **Assignment Management**: Tool assignment to users and projects
- **Movement History**: Complete audit trail of tool movements

### User Management (`/users`)
User administration:
- **User Directory**: Complete user listing with role and status information
- **Profile Management**: User profiles with preferences and settings
- **Role Assignment**: Granular role and permission management
- **Location Assignment**: Location-based access control

### Additional Modules
- **Passdown System** (`/passdown`): Shift communication and documentation
- **Track/Trend Analysis** (`/tracktrend`): Problem tracking and trend analysis
- **Location Management** (`/locations`): Facility and location administration
- **Document Management** (`/documents`): Centralized file management

## Technology Stack

### Backend Technologies
- **Spring Boot 3.3.1**: Core framework with auto-configuration
- **Spring Security**: Authentication and authorization
- **Spring Data JPA**: Database abstraction and ORM
- **Hibernate**: Object-relational mapping
- **Maven**: Build automation and dependency management
- **Java 17**: Programming language with modern features

### Frontend Technologies
- **Thymeleaf**: Server-side template engine
- **Bootstrap 5**: CSS framework for responsive design
- **JavaScript ES6+**: Modern JavaScript with modules
- **HTML5**: Semantic markup with accessibility features
- **CSS3**: Modern styling with grid and flexbox

### Database
- **MySQL**: Production database with full ACID compliance
- **H2**: In-memory database for development and testing
- **Connection Pooling**: Optimized database connections
- **Migration Support**: Version-controlled database schema

## Code Structure

### Backend Architecture

#### Controllers (`/src/main/java/com/pcd/manager/controller/`)
- **RESTful Design**: Following REST principles for API endpoints
- **MVC Pattern**: Clean separation of concerns
- **Exception Handling**: Centralized error handling with `@ControllerAdvice`
- **Validation**: Input validation with Bean Validation
- **Security Integration**: Method-level security annotations

#### Services (`/src/main/java/com/pcd/manager/service/`)
- **Business Logic**: Core business rules and operations
- **Transaction Management**: Declarative transaction handling
- **Async Processing**: Background task processing for performance
- **Integration Points**: Service-to-service communication
- **Caching**: Strategic caching for performance optimization

#### Repositories (`/src/main/java/com/pcd/manager/repository/`)
- **Spring Data JPA**: Repository pattern with query methods
- **Custom Queries**: Complex queries with `@Query` annotations
- **Performance Optimization**: Efficient queries with fetch strategies
- **Bulk Operations**: Batch processing for large datasets

#### Models (`/src/main/java/com/pcd/manager/model/`)
- **JPA Entities**: Database mapping with annotations
- **Relationship Mapping**: Complex relationships with proper fetch strategies
- **Validation**: Bean validation with custom validators
- **Audit Support**: Automatic audit fields for tracking changes

#### Configuration (`/src/main/java/com/pcd/manager/config/`)
- **Security Configuration**: Spring Security setup with custom authentication
- **Database Configuration**: Connection pooling and transaction management
- **Web Configuration**: MVC configuration with interceptors
- **Async Configuration**: Asynchronous processing setup

### Frontend Architecture

#### Templates (`/src/main/resources/templates/`)
- **Thymeleaf Integration**: Server-side rendering with dynamic content
- **Responsive Design**: Mobile-first approach with Bootstrap
- **Component Architecture**: Reusable template fragments
- **SEO Optimization**: Semantic HTML with proper meta tags

#### Static Assets (`/src/main/resources/static/`)
- **JavaScript Modules**: Modern ES6+ modules with clean architecture
- **CSS Organization**: Modular CSS with utility classes
- **Asset Optimization**: Minification and compression for production
- **Progressive Enhancement**: Graceful degradation for older browsers

#### JavaScript Architecture
- **Module Pattern**: Clean separation of concerns
- **Event-Driven**: Responsive user interface with proper event handling
- **AJAX Integration**: Asynchronous communication with the backend
- **Error Handling**: Comprehensive error handling and user feedback

### Database Schema

#### Core Entities
- **Users**: User management with roles and permissions
- **Tools**: Tool inventory with status and location tracking
- **RMAs**: Return merchandise authorization with full lifecycle
- **Locations**: Facility and location management
- **Parts**: Parts inventory and movement tracking

#### Relationship Design
- **Many-to-Many**: Complex relationships with join tables
- **One-to-Many**: Hierarchical relationships with proper cascading
- **Foreign Keys**: Referential integrity with proper constraints
- **Indexing**: Strategic indexing for query performance

## Setup Instructions

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- MySQL 8.0+ (for production) or H2 (for development)
- Git

### Development Setup

1. **Clone the Repository**
   ```bash
   git clone [repository-url]
   cd pcd-manager
   ```

2. **Configure Database**
   ```properties
   # For development (H2)
   spring.profiles.active=dev
   
   # For production (MySQL)
   spring.profiles.active=prod
   spring.datasource.url=jdbc:mysql://localhost:3306/pcd_manager
   spring.datasource.username=your_username
   spring.datasource.password=your_password
   ```

3. **Build and Run**
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

4. **Access the Application**
   - URL: `http://localhost:8080`
   - Login with configured credentials

### Production Deployment

1. **Build for Production**
   ```bash
   mvn clean package -Pprod
   ```

2. **Configure External Properties**
   ```properties
   # application-prod.properties
   spring.profiles.active=prod
   server.port=8080
   # Database configuration
   # File upload configuration
   # Security configuration
   ```

3. **Deploy WAR/JAR**
   ```bash
   java -jar target/pcd-manager-0.0.1-SNAPSHOT.jar
   ```

## Configuration

### Application Properties
- **Database**: Connection settings, pool configuration
- **Security**: Authentication providers, session management
- **File Upload**: Storage locations, size limits
- **Logging**: Log levels, file rotation

### Environment Variables
- **Security**: Sensitive configuration via environment variables
- **Database**: Connection strings and credentials
- **File Storage**: Upload directories and permissions

## Development

### Code Style
- **Java**: Following Google Java Style Guide
- **JavaScript**: ES6+ with modern practices
- **HTML/CSS**: Semantic markup with accessibility

### Testing
- **Unit Tests**: JUnit 5 with Mockito
- **Integration Tests**: Spring Boot Test with TestContainers
- **End-to-End Tests**: Selenium WebDriver

### Performance Optimization
- **Database**: Query optimization and connection pooling
- **Caching**: Strategic caching with Spring Cache
- **Async Processing**: Background tasks for heavy operations
- **Frontend**: Asset optimization and lazy loading

---

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For support and questions, please contact the development team or create an issue in the repository. 