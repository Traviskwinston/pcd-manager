import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../../services/api';

const ProjectForm = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEditMode = !!id;

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    status: 'PLANNED',
    client: '',
    startDate: '',
    dueDate: '',
    completionDate: '',
    priority: 'MEDIUM',
    budget: '',
    notes: ''
  });

  // Load project data if in edit mode
  useEffect(() => {
    const fetchInitialData = async () => {
      try {
        if (isEditMode) {
          setLoading(true);
          const projectResponse = await api.projects.getById(id);
          const project = projectResponse.data;
          
          setFormData({
            name: project.name || '',
            description: project.description || '',
            status: project.status || 'PLANNED',
            client: project.client || '',
            startDate: project.startDate ? new Date(project.startDate).toISOString().split('T')[0] : '',
            dueDate: project.dueDate ? new Date(project.dueDate).toISOString().split('T')[0] : '',
            completionDate: project.completionDate ? new Date(project.completionDate).toISOString().split('T')[0] : '',
            priority: project.priority || 'MEDIUM',
            budget: project.budget || '',
            notes: project.notes || ''
          });
        }
        
        setLoading(false);
      } catch (err) {
        console.error('Error fetching project data:', err);
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
        budget: formData.budget ? parseFloat(formData.budget) : null
      };
      
      if (isEditMode) {
        await api.projects.update(id, payload);
      } else {
        await api.projects.create(payload);
      }
      
      navigate('/projects');
    } catch (err) {
      console.error('Error saving project:', err);
      setError('Failed to save project. Please check your inputs and try again.');
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
          {isEditMode ? 'Edit Project' : 'Create New Project'}
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
              Project Name <span className="text-red-500">*</span>
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
            <label htmlFor="client" className="block text-sm font-medium text-gray-700 mb-1">
              Client
            </label>
            <input
              type="text"
              id="client"
              name="client"
              value={formData.client}
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
              <option value="PLANNED">Planned</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="ON_HOLD">On Hold</option>
              <option value="COMPLETED">Completed</option>
              <option value="CANCELED">Canceled</option>
            </select>
          </div>

          <div>
            <label htmlFor="priority" className="block text-sm font-medium text-gray-700 mb-1">
              Priority <span className="text-red-500">*</span>
            </label>
            <select
              id="priority"
              name="priority"
              required
              value={formData.priority}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            >
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="URGENT">Urgent</option>
            </select>
          </div>

          <div>
            <label htmlFor="startDate" className="block text-sm font-medium text-gray-700 mb-1">
              Start Date
            </label>
            <input
              type="date"
              id="startDate"
              name="startDate"
              value={formData.startDate}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <div>
            <label htmlFor="dueDate" className="block text-sm font-medium text-gray-700 mb-1">
              Due Date
            </label>
            <input
              type="date"
              id="dueDate"
              name="dueDate"
              value={formData.dueDate}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <div>
            <label htmlFor="completionDate" className="block text-sm font-medium text-gray-700 mb-1">
              Completion Date
            </label>
            <input
              type="date"
              id="completionDate"
              name="completionDate"
              value={formData.completionDate}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <div>
            <label htmlFor="budget" className="block text-sm font-medium text-gray-700 mb-1">
              Budget
            </label>
            <input
              type="number"
              id="budget"
              name="budget"
              step="0.01"
              min="0"
              value={formData.budget}
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
            onClick={() => navigate('/projects')}
            className="px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
          >
            Cancel
          </button>
          <button
            type="submit"
            className="px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
          >
            {isEditMode ? 'Update Project' : 'Create Project'}
          </button>
        </div>
      </form>
    </div>
  );
};

export default ProjectForm; 