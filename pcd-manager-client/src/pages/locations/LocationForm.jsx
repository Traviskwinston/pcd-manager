import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../../services/api';

const LocationForm = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEditMode = !!id;

  const [loading, setLoading] = useState(isEditMode);
  const [error, setError] = useState(null);
  
  const [formData, setFormData] = useState({
    state: '',
    fab: ''
  });

  // Load location data if in edit mode
  useEffect(() => {
    const fetchLocationData = async () => {
      if (!isEditMode) return;

      try {
        setLoading(true);
        const response = await api.locations.getById(id);
        const location = response.data;
        
        setFormData({
          state: location.state || '',
          fab: location.fab || ''
        });
      } catch (err) {
        console.error('Error fetching location data:', err);
        setError('Failed to load location data. Please try again later.');
      } finally {
        setLoading(false);
      }
    };

    fetchLocationData();
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
      
      if (isEditMode) {
        await api.locations.update(id, formData);
      } else {
        await api.locations.create(formData);
      }
      
      navigate('/locations');
    } catch (err) {
      console.error('Error saving location:', err);
      setError('Failed to save location. Please check your inputs and try again.');
    } finally {
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
          {isEditMode ? 'Edit Location' : 'Create New Location'}
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
            <label htmlFor="state" className="block text-sm font-medium text-gray-700 mb-1">
              State <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              id="state"
              name="state"
              required
              value={formData.state}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
              placeholder="e.g. Arizona, Ireland, New Mexico"
            />
          </div>

          <div>
            <label htmlFor="fab" className="block text-sm font-medium text-gray-700 mb-1">
              Fab <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              id="fab"
              name="fab"
              required
              value={formData.fab}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
              placeholder="e.g. 12, 32, 42, 52, 24, 11, 11x"
            />
          </div>
        </div>

        <div className="flex justify-end space-x-3 pt-6">
          <button
            type="button"
            onClick={() => navigate('/locations')}
            className="px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
          >
            Cancel
          </button>
          <button
            type="submit"
            className="px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
          >
            {isEditMode ? 'Update Location' : 'Create Location'}
          </button>
        </div>
      </form>
    </div>
  );
};

export default LocationForm; 