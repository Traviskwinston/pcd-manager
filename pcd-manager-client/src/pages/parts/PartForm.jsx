import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../../services/api';

const PartForm = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEditMode = !!id;

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [locations, setLocations] = useState([]);
  
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    partNumber: '',
    category: '',
    location: '',
    quantity: 0,
    minimumQuantity: 0,
    unitCost: '',
    manufacturer: '',
    supplier: '',
    lastOrderDate: '',
    notes: ''
  });

  // Load part data if in edit mode and fetch locations
  useEffect(() => {
    const fetchInitialData = async () => {
      try {
        setLoading(true);
        
        // Fetch locations for dropdown
        const locationsResponse = await api.locations.getAll();
        setLocations(locationsResponse.data);
        
        // If edit mode, fetch part data
        if (isEditMode) {
          const partResponse = await api.parts.getById(id);
          const part = partResponse.data;
          
          setFormData({
            name: part.name || '',
            description: part.description || '',
            partNumber: part.partNumber || '',
            category: part.category || '',
            location: part.location?.id || '',
            quantity: part.quantity || 0,
            minimumQuantity: part.minimumQuantity || 0,
            unitCost: part.unitCost || '',
            manufacturer: part.manufacturer || '',
            supplier: part.supplier || '',
            lastOrderDate: part.lastOrderDate ? part.lastOrderDate.substring(0, 10) : '',
            notes: part.notes || ''
          });
        }
        
        setLoading(false);
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
        location: formData.location ? { id: formData.location } : null,
        quantity: parseInt(formData.quantity, 10),
        minimumQuantity: parseInt(formData.minimumQuantity, 10),
        unitCost: formData.unitCost ? parseFloat(formData.unitCost) : null
      };
      
      if (isEditMode) {
        await api.parts.update(id, payload);
      } else {
        await api.parts.create(payload);
      }
      
      navigate('/parts');
    } catch (err) {
      console.error('Error saving part:', err);
      setError('Failed to save part. Please check your inputs and try again.');
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
          {isEditMode ? 'Edit Part' : 'Create New Part'}
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
            <label htmlFor="partNumber" className="block text-sm font-medium text-gray-700 mb-1">
              Part Number
            </label>
            <input
              type="text"
              id="partNumber"
              name="partNumber"
              value={formData.partNumber}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <div>
            <label htmlFor="category" className="block text-sm font-medium text-gray-700 mb-1">
              Category <span className="text-red-500">*</span>
            </label>
            <select
              id="category"
              name="category"
              required
              value={formData.category}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            >
              <option value="">-- Select Category --</option>
              <option value="ELECTRICAL">Electrical</option>
              <option value="MECHANICAL">Mechanical</option>
              <option value="HYDRAULIC">Hydraulic</option>
              <option value="PNEUMATIC">Pneumatic</option>
              <option value="STRUCTURAL">Structural</option>
              <option value="FASTENER">Fastener</option>
              <option value="CONSUMABLE">Consumable</option>
              <option value="OTHER">Other</option>
            </select>
          </div>

          <div>
            <label htmlFor="manufacturer" className="block text-sm font-medium text-gray-700 mb-1">
              Manufacturer
            </label>
            <input
              type="text"
              id="manufacturer"
              name="manufacturer"
              value={formData.manufacturer}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            />
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
            <label htmlFor="supplier" className="block text-sm font-medium text-gray-700 mb-1">
              Supplier
            </label>
            <input
              type="text"
              id="supplier"
              name="supplier"
              value={formData.supplier}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <div>
            <label htmlFor="quantity" className="block text-sm font-medium text-gray-700 mb-1">
              Quantity <span className="text-red-500">*</span>
            </label>
            <input
              type="number"
              id="quantity"
              name="quantity"
              required
              min="0"
              value={formData.quantity}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <div>
            <label htmlFor="minimumQuantity" className="block text-sm font-medium text-gray-700 mb-1">
              Minimum Quantity
            </label>
            <input
              type="number"
              id="minimumQuantity"
              name="minimumQuantity"
              min="0"
              value={formData.minimumQuantity}
              onChange={handleInputChange}
              className="block w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <div>
            <label htmlFor="unitCost" className="block text-sm font-medium text-gray-700 mb-1">
              Unit Cost
            </label>
            <div className="flex">
              <span className="inline-flex items-center px-3 rounded-l-md border border-r-0 border-gray-300 bg-gray-50 text-gray-500">$</span>
              <input
                type="number"
                id="unitCost"
                name="unitCost"
                step="0.01"
                min="0"
                value={formData.unitCost}
                onChange={handleInputChange}
                className="block w-full p-2 border border-gray-300 rounded-r-md focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
          </div>

          <div>
            <label htmlFor="lastOrderDate" className="block text-sm font-medium text-gray-700 mb-1">
              Last Order Date
            </label>
            <input
              type="date"
              id="lastOrderDate"
              name="lastOrderDate"
              value={formData.lastOrderDate}
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
            onClick={() => navigate('/parts')}
            className="px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
          >
            Cancel
          </button>
          <button
            type="submit"
            className="px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
          >
            {isEditMode ? 'Update Part' : 'Create Part'}
          </button>
        </div>
      </form>
    </div>
  );
};

export default PartForm; 