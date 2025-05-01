# PCD Manager

A Return Merchandise Authorization (RMA) management system for managing product returns, repairs, and documentation.

## Features

- RMA tracking and management
- File/document attachment with images and documents
- Tool management and tracking
- File transfer between RMAs

## Project Structure

- **pcd-manager-server**: Spring Boot backend
- **pcd-manager-client**: Frontend client application

## Setup Instructions

### Server

1. Navigate to the `pcd-manager-server` directory
2. Run with Maven: `mvn spring-boot:run`
3. Server will start on http://localhost:8080

### Client

1. Navigate to the `pcd-manager-client` directory
2. Install dependencies: `npm install`
3. Start development server: `npm start`
4. Client will be available at http://localhost:3000

## Requirements

- Java 17+
- Maven
- Node.js
- npm/yarn

## Configuration

The application uses application.properties for configuration. 
You can set the upload directory and other settings there. 