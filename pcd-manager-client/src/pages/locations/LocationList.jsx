import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import api from '../../services/api';

const LocationList = () => {
  const [locations, setLocations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');

  useEffect(() => {
    const fetchLocations = async () => {
      try {
        setLoading(true);
        const response = await api.locations.getAll();
        setLocations(response.data);
      } catch (err) {
        console.error('Error fetching locations:', err);
        setError('Failed to load locations. Please try again later.');
      } finally {
        setLoading(false);
      }
    };

    fetchLocations();
  }, []);

  // Filter locations based on search term
  const filteredLocations = locations.filter(location =>
    location.state.toLowerCase().includes(searchTerm.toLowerCase()) ||
    location.fab.toLowerCase().includes(searchTerm.toLowerCase()) ||
    location.displayName?.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const handleDelete = async (id) => {
    if (window.confirm('Are you sure you want to delete this location?')) {
      try {
        await api.locations.delete(id);
        setLocations(locations.filter(location => location.id !== id));
      } catch (err) {
        console.error('Error deleting location:', err);
        alert('Failed to delete location. It may be in use by other entities.');
      }
    }
  };
  
  const handleSetDefault = async (id) => {
    try {
      await api.locations.setDefault(id);
      
      // Update locations to reflect the new default
      setLocations(prevLocations => 
        prevLocations.map(location => ({
          ...location,
          isDefault: location.id === id
        }))
      );
      
      // Reload the page to update the header
      window.location.reload();
    } catch (err) {
      console.error('Error setting default location:', err);
      alert('Failed to set default location.');
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded" role="alert">
        <p>{error}</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold text-gray-800">Locations</h1>
        <Link 
          to="/locations/new" 
          className="bg-blue-600 hover:bg-blue-700 text-white py-2 px-4 rounded flex items-center"
        >
          <span className="material-icons mr-1">add</span>
          Add Location
        </Link>
      </div>

      {/* Search */}
      <div className="bg-white p-4 rounded-lg shadow-md">
        <div className="flex flex-col md:flex-row gap-4">
          <div className="flex-1">
            <label htmlFor="search" className="block text-sm font-medium text-gray-700 mb-1">
              Search Locations
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <span className="material-icons text-gray-400">search</span>
              </div>
              <input
                type="text"
                id="search"
                className="block w-full pl-10 pr-3 py-2 border border-gray-300 rounded-md leading-5 bg-white placeholder-gray-500 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                placeholder="Search by state or fab"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Location grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
        {filteredLocations.length > 0 ? (
          filteredLocations.map(location => (
            <div key={location.id} className="bg-white rounded-lg shadow-md overflow-hidden">
              <div className="p-5">
                <div className="flex justify-between items-center">
                  <h3 className="text-lg font-bold text-gray-800">{location.displayName || `${location.state} F${location.fab}`}</h3>
                  {location.isDefault && (
                    <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                      Default
                    </span>
                  )}
                </div>
                <div className="mt-2 text-sm text-gray-600">
                  <p><span className="font-medium">State:</span> {location.state}</p>
                  <p><span className="font-medium">Fab:</span> {location.fab}</p>
                </div>
                <div className="mt-4 flex justify-end space-x-2">
                  {!location.isDefault && (
                    <button
                      onClick={() => handleSetDefault(location.id)}
                      className="text-green-600 hover:text-green-800"
                      title="Set as Default"
                    >
                      <span className="material-icons">star</span>
                    </button>
                  )}
                  <Link 
                    to={`/locations/${location.id}/edit`}
                    className="text-blue-600 hover:text-blue-800"
                  >
                    <span className="material-icons">edit</span>
                  </Link>
                  <button 
                    onClick={() => handleDelete(location.id)}
                    className="text-red-600 hover:text-red-800"
                  >
                    <span className="material-icons">delete</span>
                  </button>
                </div>
              </div>
            </div>
          ))
        ) : (
          <div className="col-span-3 bg-white p-6 rounded-lg shadow-md text-center text-gray-500">
            {searchTerm 
              ? 'No locations match your search criteria' 
              : 'No locations available. Add your first location!'}
          </div>
        )}
      </div>
    </div>
  );
};

export default LocationList; 