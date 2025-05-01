import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../../services/api';

const PassdownForm = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEditMode = !!id;

  const [loading, setLoading] = useState(isEditMode);
  const [error, setError] = useState(null);
  
  const [formData, setFormData] = useState({
    title: '',
    content: '',
    priority: 'MEDIUM',
    status: 'ACTIVE',
    createDate: new Date().toISOString().split('T')[0],
    resolveDate: '',
    createdBy: '',
    assignedTo: ''
  });

  // Load passdown data if in edit mode
  useEffect(() => {
    const fetchPassdownData = async () => {
      if (!isEditMode) return;

      try {
        setLoading(true);
        const response = await api.passdowns.getById(id);
        const passdown = response.data;
        
        setFormData({
          title: passdown.title || '',
          content: passdown.content || '',
          priority: passdown.priority || 'MEDIUM',
          status: passdown.status || 'ACTIVE',
          createDate: passdown.createDate ? passdown.createDate.split('T')[0] : new Date().toISOString().split('T')[0],
          resolveDate: passdown.resolveDate ? passdown.resolveDate.split('T')[0] : '',
          createdBy: passdown.createdBy || '',
          assignedTo: passdown.assignedTo || ''
        });
      } catch (err) {
        console.error('Error fetching passdown data:', err);
        setError('Failed to load passdown data. Please try again later.');
      } finally {
        setLoading(false);
      }
    };

    fetchPassdownData();
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
        await api.passdowns.update(id, formData);
      } else {
        await api.passdowns.create(formData);
      }
      
      navigate('/passdowns');
    } catch (err) {
      console.error('Error saving passdown:', err);
      setError('Failed to save passdown. Please check your inputs and try again.');
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
          {isEditMode ? 'Edit Passdown' : 'Create New Passdown'}
        </h1>
      </div>

      {error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded" role="alert">
          <p>{error}</p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="bg-white p-6 rounded-lg shadow-md space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="md:col-span-2">
            <label htmlFor="title" className="block text-sm font-medium text-gray-700 mb-1">
              Title <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              id="title"
              name="title"
              required
              value={formData.title}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <div>
            <label htmlFor="priority" className="block text-sm font-medium text-gray-700 mb-1">
              Priority
            </label>
            <select
              id="priority"
              name="priority"
              value={formData.priority}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            >
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="CRITICAL">Critical</option>
            </select>
          </div>

          <div>
            <label htmlFor="status" className="block text-sm font-medium text-gray-700 mb-1">
              Status
            </label>
            <select
              id="status"
              name="status"
              value={formData.status}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            >
              <option value="ACTIVE">Active</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="RESOLVED">Resolved</option>
              <option value="ARCHIVED">Archived</option>
            </select>
          </div>

          <div>
            <label htmlFor="createDate" className="block text-sm font-medium text-gray-700 mb-1">
              Create Date
            </label>
            <input
              type="date"
              id="createDate"
              name="createDate"
              value={formData.createDate}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <div>
            <label htmlFor="resolveDate" className="block text-sm font-medium text-gray-700 mb-1">
              Resolve Date
            </label>
            <input
              type="date"
              id="resolveDate"
              name="resolveDate"
              value={formData.resolveDate}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <div>
            <label htmlFor="createdBy" className="block text-sm font-medium text-gray-700 mb-1">
              Created By
            </label>
            <input
              type="text"
              id="createdBy"
              name="createdBy"
              value={formData.createdBy}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <div>
            <label htmlFor="assignedTo" className="block text-sm font-medium text-gray-700 mb-1">
              Assigned To
            </label>
            <input
              type="text"
              id="assignedTo"
              name="assignedTo"
              value={formData.assignedTo}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <div className="md:col-span-2">
            <label htmlFor="content" className="block text-sm font-medium text-gray-700 mb-1">
              Content <span className="text-red-500">*</span>
            </label>
            <textarea
              id="content"
              name="content"
              rows="6"
              required
              value={formData.content}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            ></textarea>
          </div>
        </div>

        <div className="flex justify-end space-x-3 pt-6">
          <button
            type="button"
            onClick={() => navigate('/passdowns')}
            className="px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
          >
            Cancel
          </button>
          <button
            type="submit"
            className="px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
          >
            {isEditMode ? 'Update Passdown' : 'Create Passdown'}
          </button>
        </div>
      </form>
    </div>
  );
};

export default PassdownForm; 