import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import api from '../../services/api';

const ToolList = () => {
  const [tools, setTools] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [sortField, setSortField] = useState('name');
  const [sortDirection, setSortDirection] = useState('asc');

  useEffect(() => {
    const fetchTools = async () => {
      try {
        setLoading(true);
        const response = await api.tools.getAll();
        setTools(response.data);
      } catch (err) {
        console.error('Error fetching tools:', err);
        setError('Failed to load tools. Please try again later.');
      } finally {
        setLoading(false);
      }
    };

    fetchTools();
  }, []);

  // Handle sort change
  const handleSort = (field) => {
    if (field === sortField) {
      // If same field, toggle direction
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
    } else {
      // If new field, set it and default to ascending
      setSortField(field);
      setSortDirection('asc');
    }
  };

  // Filter and sort tools
  const filteredTools = tools.filter(tool => 
    tool.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    (tool.model && tool.model.toLowerCase().includes(searchTerm.toLowerCase())) ||
    (tool.serialNumber && tool.serialNumber.toLowerCase().includes(searchTerm.toLowerCase()))
  );

  const sortedTools = [...filteredTools].sort((a, b) => {
    let aValue = a[sortField];
    let bValue = b[sortField];
    
    // Handle null values for sorting
    if (aValue === null || aValue === undefined) aValue = '';
    if (bValue === null || bValue === undefined) bValue = '';
    
    // For strings, use localeCompare
    if (typeof aValue === 'string') {
      return sortDirection === 'asc' 
        ? aValue.localeCompare(bValue) 
        : bValue.localeCompare(aValue);
    }
    
    // For other types (numbers, dates)
    return sortDirection === 'asc' ? aValue - bValue : bValue - aValue;
  });

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
        <h1 className="text-2xl font-bold text-gray-800">Tool Management</h1>
        <Link 
          to="/tools/new" 
          className="bg-blue-600 hover:bg-blue-700 text-white py-2 px-4 rounded flex items-center"
        >
          <span className="material-icons mr-1">add</span>
          Add Tool
        </Link>
      </div>

      {/* Search and filters */}
      <div className="bg-white p-4 rounded-lg shadow-md">
        <div className="flex flex-col md:flex-row gap-4">
          <div className="flex-1">
            <label htmlFor="search" className="block text-sm font-medium text-gray-700 mb-1">
              Search Tools
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <span className="material-icons text-gray-400">search</span>
              </div>
              <input
                type="text"
                id="search"
                className="block w-full pl-10 pr-3 py-2 border border-gray-300 rounded-md leading-5 bg-white placeholder-gray-500 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                placeholder="Search by name, model, or serial number"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </div>
          </div>
          
          {/* Additional filters could be added here */}
        </div>
      </div>

      {/* Tool list */}
      <div className="bg-white rounded-lg shadow-md overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th 
                  scope="col"
                  className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                  onClick={() => handleSort('name')}
                >
                  <div className="flex items-center">
                    Name
                    {sortField === 'name' && (
                      <span className="material-icons ml-1 text-xs">
                        {sortDirection === 'asc' ? 'arrow_upward' : 'arrow_downward'}
                      </span>
                    )}
                  </div>
                </th>
                <th 
                  scope="col"
                  className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                  onClick={() => handleSort('model')}
                >
                  <div className="flex items-center">
                    Model
                    {sortField === 'model' && (
                      <span className="material-icons ml-1 text-xs">
                        {sortDirection === 'asc' ? 'arrow_upward' : 'arrow_downward'}
                      </span>
                    )}
                  </div>
                </th>
                <th 
                  scope="col"
                  className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                  onClick={() => handleSort('serialNumber')}
                >
                  <div className="flex items-center">
                    Serial Number
                    {sortField === 'serialNumber' && (
                      <span className="material-icons ml-1 text-xs">
                        {sortDirection === 'asc' ? 'arrow_upward' : 'arrow_downward'}
                      </span>
                    )}
                  </div>
                </th>
                <th 
                  scope="col"
                  className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                >
                  Location
                </th>
                <th scope="col" className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {sortedTools.length > 0 ? (
                sortedTools.map((tool) => (
                  <tr key={tool.id} className="hover:bg-gray-50">
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm font-medium text-gray-900">{tool.name}</div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm text-gray-500">{tool.model || '-'}</div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm text-gray-500">{tool.serialNumber || '-'}</div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm text-gray-500">
                        {tool.location ? tool.location.displayName : '-'}
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                      <Link 
                        to={`/tools/${tool.id}`} 
                        className="text-blue-600 hover:text-blue-900 mr-4"
                      >
                        View
                      </Link>
                      <Link 
                        to={`/tools/${tool.id}/edit`} 
                        className="text-green-600 hover:text-green-900"
                      >
                        Edit
                      </Link>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan="5" className="px-6 py-4 text-center text-gray-500">
                    {searchTerm 
                      ? 'No tools match your search criteria' 
                      : 'No tools available. Add your first tool!'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default ToolList; 