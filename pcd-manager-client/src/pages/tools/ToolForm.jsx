import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../../services/api';

const ToolForm = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEditMode = !!id;

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [locations, setLocations] = useState([]);
  
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    serialNumber: '',
    category: '',
    status: 'AVAILABLE',
    location: '',
    lastMaintenanceDate: '',
    nextMaintenanceDate: '',
    notes: ''
  });

  // Load tool data if in edit mode and fetch locations
  useEffect(() => {
    const fetchInitialData = async () => {
      try {
        setLoading(true);
        
        // Fetch locations for dropdown
        const locationsResponse = await api.locations.getAll();
        setLocations(locationsResponse.data);
        
        // If edit mode, fetch tool data
        if (isEditMode) {
          const toolResponse = await api.tools.getById(id);
          const tool = toolResponse.data;
          
          setFormData({
            name: tool.name || '',
            description: tool.description || '',
            serialNumber: tool.serialNumber || '',
            category: tool.category || '',
            status: tool.status || 'AVAILABLE',
            location: tool.location?.id || '',
            lastMaintenanceDate: tool.lastMaintenanceDate ? new Date(tool.lastMaintenanceDate).toISOString().split('T')[0] : '',
            nextMaintenanceDate: tool.nextMaintenanceDate ? new Date(tool.nextMaintenanceDate).toISOString().split('T')[0] : '',
            notes: tool.notes || ''
          });
        } else {
          // For create mode, just set loading to false since we don't need to fetch tool data
          setLoading(false);
        }
      } catch (err) {
        console.error('Error fetching initial data:', err);
        setError('Failed to load necessary data. Please try again later.');
        setLoading(false);
      }
    };

    fetchInitialData();
  }, [id, isEditMode]);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData({
      ...formData,
      [name]: value
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    try {
      setLoading(true);
      
      // Create payload with correct data types
      const payload = {
        ...formData,
        location: formData.location ? { id: formData.location } : null
      };
      
      if (isEditMode) {
        await api.tools.update(id, payload);
      } else {
        await api.tools.create(payload);
      }
      
      navigate('/tools');
    } catch (err) {
      console.error('Error saving tool:', err);
      setError('Failed to save tool. Please check your inputs and try again.');
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold text-gray-800">
          {isEditMode ? 'Edit Tool' : 'Create New Tool'}
        </h1>
      </div>

      {error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded" role="alert">
          <p>{error}</p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="bg-white p-6 rounded-lg shadow-md space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label htmlFor="name" className="block text-sm font-medium text-gray-700 mb-1">
              Name <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              id="name"
              name="name"
              required
              value={formData.name}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <div>
            <label htmlFor="serialNumber" className="block text-sm font-medium text-gray-700 mb-1">
              Serial Number
            </label>
            <input
              type="text"
              id="serialNumber"
              name="serialNumber"
              value={formData.serialNumber}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <div>
            <label htmlFor="category" className="block text-sm font-medium text-gray-700 mb-1">
              Category
            </label>
            <input
              type="text"
              id="category"
              name="category"
              value={formData.category}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <div>
            <label htmlFor="status" className="block text-sm font-medium text-gray-700 mb-1">
              Status <span className="text-red-500">*</span>
            </label>
            <select
              id="status"
              name="status"
              required
              value={formData.status}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            >
              <option value="AVAILABLE">Available</option>
              <option value="IN_USE">In Use</option>
              <option value="MAINTENANCE">Maintenance</option>
              <option value="BROKEN">Broken</option>
              <option value="LOST">Lost</option>
            </select>
          </div>

          <div>
            <label htmlFor="location" className="block text-sm font-medium text-gray-700 mb-1">
              Location
            </label>
            <select
              id="location"
              name="location"
              value={formData.location}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            >
              <option value="">Select a location</option>
              {locations.map((location) => (
                <option key={location.id} value={location.id}>
                  {location.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="lastMaintenanceDate" className="block text-sm font-medium text-gray-700 mb-1">
              Last Maintenance Date
            </label>
            <input
              type="date"
              id="lastMaintenanceDate"
              name="lastMaintenanceDate"
              value={formData.lastMaintenanceDate}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <div>
            <label htmlFor="nextMaintenanceDate" className="block text-sm font-medium text-gray-700 mb-1">
              Next Maintenance Date
            </label>
            <input
              type="date"
              id="nextMaintenanceDate"
              name="nextMaintenanceDate"
              value={formData.nextMaintenanceDate}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <div className="md:col-span-2">
            <label htmlFor="description" className="block text-sm font-medium text-gray-700 mb-1">
              Description
            </label>
            <textarea
              id="description"
              name="description"
              rows="3"
              value={formData.description}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            ></textarea>
          </div>

          <div className="md:col-span-2">
            <label htmlFor="notes" className="block text-sm font-medium text-gray-700 mb-1">
              Notes
            </label>
            <textarea
              id="notes"
              name="notes"
              rows="3"
              value={formData.notes}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            ></textarea>
          </div>
        </div>

        <div className="flex justify-end space-x-3 pt-6">
          <button
            type="button"
            onClick={() => navigate('/tools')}
            className="px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
          >
            Cancel
          </button>
          <button
            type="submit"
            className="px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
          >
            {isEditMode ? 'Update Tool' : 'Create Tool'}
          </button>
        </div>
      </form>
    </div>
  );
};

export default ToolForm; 