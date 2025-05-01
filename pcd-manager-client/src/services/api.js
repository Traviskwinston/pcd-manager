import axios from 'axios';

// Create axios instance with base URL
const instance = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api', // Default to /api if not specified
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to add auth token to requests
instance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor to handle auth errors
instance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      // Unauthorized, clear local storage and redirect to login
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

const api = {
  // Method to set auth token manually
  setAuthToken: (token) => {
    instance.defaults.headers.common.Authorization = `Bearer ${token}`;
  },

  // Method to remove auth token
  removeAuthToken: () => {
    delete instance.defaults.headers.common.Authorization;
  },

  // User API
  users: {
    getAll: () => instance.get('/users'),
    getById: (id) => instance.get(`/users/${id}`),
    create: (userData) => instance.post('/users', userData),
    update: (id, userData) => instance.put(`/users/${id}`, userData),
    delete: (id) => instance.delete(`/users/${id}`),
  },

  // Tool API
  tools: {
    getAll: () => instance.get('/tools'),
    getById: (id) => instance.get(`/tools/${id}`),
    create: (toolData) => instance.post('/tools', toolData),
    update: (id, toolData) => instance.put(`/tools/${id}`, toolData),
    delete: (id) => instance.delete(`/tools/${id}`),
  },

  // Location API
  locations: {
    getAll: () => instance.get('/locations'),
    getById: (id) => instance.get(`/locations/${id}`),
    getDefault: () => instance.get('/locations/default'),
    setDefault: (id) => instance.post(`/locations/${id}/default`),
    create: (locationData) => instance.post('/locations', locationData),
    update: (id, locationData) => instance.put(`/locations/${id}`, locationData),
    delete: (id) => instance.delete(`/locations/${id}`),
  },

  // Part API
  parts: {
    getAll: () => instance.get('/parts'),
    getById: (id) => instance.get(`/parts/${id}`),
    create: (partData) => instance.post('/parts', partData),
    update: (id, partData) => instance.put(`/parts/${id}`, partData),
    delete: (id) => instance.delete(`/parts/${id}`),
  },

  // Passdown API
  passdowns: {
    getAll: () => instance.get('/passdowns'),
    getById: (id) => instance.get(`/passdowns/${id}`),
    create: (passdownData) => instance.post('/passdowns', passdownData),
    update: (id, passdownData) => instance.put(`/passdowns/${id}`, passdownData),
    delete: (id) => instance.delete(`/passdowns/${id}`),
  },

  // Projects API
  projects: {
    getAll: () => instance.get('/projects'),
    getById: (id) => instance.get(`/projects/${id}`),
    create: (projectData) => instance.post('/projects', projectData),
    update: (id, projectData) => instance.put(`/projects/${id}`, projectData),
    delete: (id) => instance.delete(`/projects/${id}`),
  },

  // Authentication
  auth: {
    login: (credentials) => instance.post('/auth/login', credentials),
    register: (userData) => instance.post('/auth/register', userData),
    getCurrentUser: () => instance.get('/auth/me'),
  },

  // Generic requests
  get: (url, config) => instance.get(url, config),
  post: (url, data, config) => instance.post(url, data, config),
  put: (url, data, config) => instance.put(url, data, config),
  delete: (url, config) => instance.delete(url, config),
};

export default api; 