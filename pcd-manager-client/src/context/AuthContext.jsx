import React, { createContext, useState, useContext, useEffect } from 'react';
import api from '../services/api';

const AuthContext = createContext(null);

export const useAuth = () => {
  return useContext(AuthContext);
};

export const AuthProvider = ({ children }) => {
  const [currentUser, setCurrentUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    // Check if user is stored in localStorage
    const storedUser = localStorage.getItem('user');
    if (storedUser) {
      try {
        setCurrentUser(JSON.parse(storedUser));
      } catch (e) {
        console.error('Failed to parse stored user', e);
        localStorage.removeItem('user');
      }
    }
    setLoading(false);
  }, []);

  // Login function
  const login = async (email, password) => {
    setLoading(true);
    setError('');
    
    try {
      const response = await api.post('/api/auth/login', { email, password });
      const userData = response.data;
      
      // Store token and user data
      localStorage.setItem('token', userData.token);
      localStorage.setItem('user', JSON.stringify(userData.user));
      
      setCurrentUser(userData.user);
      api.setAuthToken(userData.token);
      return userData.user;
    } catch (err) {
      console.error('Login error:', err);
      setError(
        err.response?.data?.message || 
        'Failed to login. Please check your credentials.'
      );
      throw err;
    } finally {
      setLoading(false);
    }
  };

  // Logout function
  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setCurrentUser(null);
    api.removeAuthToken();
  };

  // Value object to be provided to consumers
  const value = {
    currentUser,
    loading,
    error,
    login,
    logout,
    isAuthenticated: !!currentUser,
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};

export default AuthContext; 