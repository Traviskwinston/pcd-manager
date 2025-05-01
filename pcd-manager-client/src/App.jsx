import React from 'react'
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import ToolList from './pages/tools/ToolList'
import ToolDetail from './pages/tools/ToolDetail'
import LocationList from './pages/locations/LocationList'
import PartList from './pages/parts/PartList'
import PassdownList from './pages/passdowns/PassdownList'
import UserManagement from './pages/admin/UserManagement'
import PageLayout from './components/layout/PageLayout'
import ProtectedRoute from './components/auth/ProtectedRoute'

function App() {
  return (
    <Router>
      <AuthProvider>
        <Routes>
          {/* Public routes */}
          <Route path="/login" element={<Login />} />
          
          {/* Protected routes - require authentication */}
          <Route element={<ProtectedRoute />}>
            <Route element={<PageLayout />}>
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="/dashboard" element={<Dashboard />} />
              <Route path="/tools" element={<ToolList />} />
              <Route path="/tools/:id" element={<ToolDetail />} />
              <Route path="/locations" element={<LocationList />} />
              <Route path="/parts" element={<PartList />} />
              <Route path="/passdowns" element={<PassdownList />} />
              <Route path="/admin/users" element={<UserManagement />} />
            </Route>
          </Route>
          
          {/* Fallback for unknown routes */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </Router>
  )
}

export default App 