import React, { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import api from '../../services/api';

const ToolDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [tool, setTool] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activeTab, setActiveTab] = useState('details');

  useEffect(() => {
    const fetchToolDetails = async () => {
      try {
        setLoading(true);
        const response = await api.tools.getById(id);
        setTool(response.data);
      } catch (err) {
        console.error('Error fetching tool details:', err);
        setError('Failed to load tool details. Please try again later.');
      } finally {
        setLoading(false);
      }
    };

    fetchToolDetails();
  }, [id]);

  const handleDeleteTool = async () => {
    if (window.confirm('Are you sure you want to delete this tool? This action cannot be undone.')) {
      try {
        await api.tools.delete(id);
        navigate('/tools', { replace: true });
      } catch (err) {
        console.error('Error deleting tool:', err);
        alert('Failed to delete tool. It may be referenced by other records.');
      }
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

  if (!tool) {
    return (
      <div className="bg-yellow-100 border border-yellow-400 text-yellow-700 px-4 py-3 rounded" role="alert">
        <p>Tool not found.</p>
      </div>
    );
  }

  // Format date helper
  const formatDate = (dateString) => {
    if (!dateString) return 'N/A';
    return new Date(dateString).toLocaleDateString();
  };

  return (
    <div className="space-y-6">
      {/* Header with actions */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-800">{tool.name}</h1>
          <p className="text-gray-600">
            {tool.model && `Model: ${tool.model}`}
            {tool.serialNumber && ` | Serial: ${tool.serialNumber}`}
          </p>
          {tool.location && (
            <p className="text-gray-600">
              Location: {tool.location.displayName}
            </p>
          )}
        </div>
        <div className="flex space-x-3">
          <Link 
            to={`/tools/${id}/edit`}
            className="bg-blue-600 hover:bg-blue-700 text-white py-2 px-4 rounded flex items-center"
          >
            <span className="material-icons mr-1">edit</span>
            Edit
          </Link>
          <button 
            onClick={handleDeleteTool}
            className="bg-red-600 hover:bg-red-700 text-white py-2 px-4 rounded flex items-center"
          >
            <span className="material-icons mr-1">delete</span>
            Delete
          </button>
        </div>
      </div>

      {/* Tabs navigation */}
      <div className="border-b border-gray-200">
        <nav className="-mb-px flex space-x-8">
          <button
            className={`${
              activeTab === 'details'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            } whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm`}
            onClick={() => setActiveTab('details')}
          >
            Details
          </button>
          <button
            className={`${
              activeTab === 'parts'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            } whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm`}
            onClick={() => setActiveTab('parts')}
          >
            Parts
          </button>
          <button
            className={`${
              activeTab === 'passdowns'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            } whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm`}
            onClick={() => setActiveTab('passdowns')}
          >
            Passdowns
          </button>
          <button
            className={`${
              activeTab === 'documents'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            } whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm`}
            onClick={() => setActiveTab('documents')}
          >
            Documents & Images
          </button>
        </nav>
      </div>

      {/* Tab content */}
      <div className="bg-white p-6 rounded-lg shadow-md">
        {/* Details Tab */}
        {activeTab === 'details' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <h3 className="text-lg font-medium text-gray-900 mb-3">General Information</h3>
              <div className="space-y-2">
                <div className="grid grid-cols-3 gap-4">
                  <div className="text-sm font-medium text-gray-500">Name</div>
                  <div className="text-sm text-gray-900 col-span-2">{tool.name}</div>
                </div>
                <div className="grid grid-cols-3 gap-4">
                  <div className="text-sm font-medium text-gray-500">Model</div>
                  <div className="text-sm text-gray-900 col-span-2">{tool.model || 'N/A'}</div>
                </div>
                <div className="grid grid-cols-3 gap-4">
                  <div className="text-sm font-medium text-gray-500">Serial Number</div>
                  <div className="text-sm text-gray-900 col-span-2">{tool.serialNumber || 'N/A'}</div>
                </div>
                <div className="grid grid-cols-3 gap-4">
                  <div className="text-sm font-medium text-gray-500">Location</div>
                  <div className="text-sm text-gray-900 col-span-2">
                    {tool.location ? tool.location.displayName : 'N/A'}
                  </div>
                </div>
              </div>
            </div>
            
            <div>
              <h3 className="text-lg font-medium text-gray-900 mb-3">Milestone Dates</h3>
              <div className="space-y-2">
                <div className="grid grid-cols-3 gap-4">
                  <div className="text-sm font-medium text-gray-500">Pre SL1</div>
                  <div className="text-sm text-gray-900 col-span-2">{formatDate(tool.preSl1Date)}</div>
                </div>
                <div className="grid grid-cols-3 gap-4">
                  <div className="text-sm font-medium text-gray-500">SL1</div>
                  <div className="text-sm text-gray-900 col-span-2">{formatDate(tool.sl1Date)}</div>
                </div>
                <div className="grid grid-cols-3 gap-4">
                  <div className="text-sm font-medium text-gray-500">SL2</div>
                  <div className="text-sm text-gray-900 col-span-2">{formatDate(tool.sl2Date)}</div>
                </div>
                <div className="grid grid-cols-3 gap-4">
                  <div className="text-sm font-medium text-gray-500">HEC</div>
                  <div className="text-sm text-gray-900 col-span-2">{formatDate(tool.hazardousEnergyControlDate)}</div>
                </div>
                <div className="grid grid-cols-3 gap-4">
                  <div className="text-sm font-medium text-gray-500">Pre-Mechanical</div>
                  <div className="text-sm text-gray-900 col-span-2">{formatDate(tool.preMechanicalDate)}</div>
                </div>
                <div className="grid grid-cols-3 gap-4">
                  <div className="text-sm font-medium text-gray-500">Post-Mechanical</div>
                  <div className="text-sm text-gray-900 col-span-2">{formatDate(tool.postMechanicalDate)}</div>
                </div>
                <div className="grid grid-cols-3 gap-4">
                  <div className="text-sm font-medium text-gray-500">FSR Uploaded</div>
                  <div className="text-sm text-gray-900 col-span-2">{formatDate(tool.fsrUploadedDate)}</div>
                </div>
                <div className="grid grid-cols-3 gap-4">
                  <div className="text-sm font-medium text-gray-500">COA Uploaded</div>
                  <div className="text-sm text-gray-900 col-span-2">{formatDate(tool.certificateOfApprovalUploadedDate)}</div>
                </div>
                <div className="grid grid-cols-3 gap-4">
                  <div className="text-sm font-medium text-gray-500">Customer Turnover</div>
                  <div className="text-sm text-gray-900 col-span-2">{formatDate(tool.turnedOverToCustomerDate)}</div>
                </div>
              </div>
            </div>

            <div className="md:col-span-2">
              <h3 className="text-lg font-medium text-gray-900 mb-3">Assigned Technicians</h3>
              {tool.currentTechnicians && tool.currentTechnicians.length > 0 ? (
                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
                  {tool.currentTechnicians.map(tech => (
                    <div key={tech.id} className="bg-gray-50 p-3 rounded border border-gray-200">
                      <div className="font-medium">{tech.firstName} {tech.lastName}</div>
                      <div className="text-sm text-gray-500">{tech.email}</div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-gray-500">No technicians currently assigned.</p>
              )}
            </div>
          </div>
        )}

        {/* Parts Tab */}
        {activeTab === 'parts' && (
          <div>
            <div className="flex justify-between items-center mb-6">
              <h3 className="text-lg font-medium text-gray-900">Parts History</h3>
              <button className="bg-green-600 hover:bg-green-700 text-white py-2 px-4 rounded flex items-center">
                <span className="material-icons mr-1">add</span>
                Add Part Movement
              </button>
            </div>
            
            {tool.partMovements && tool.partMovements.length > 0 ? (
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Date
                      </th>
                      <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Part
                      </th>
                      <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Action
                      </th>
                      <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Source/Destination
                      </th>
                      <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Comments
                      </th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {tool.partMovements.map((movement) => (
                      <tr key={movement.id}>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          {new Date(movement.timestamp).toLocaleString()}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                          {movement.part.name}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          <span className={`px-2 py-1 inline-flex text-xs leading-5 font-semibold rounded-full ${
                            movement.type === 'ADDED' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                          }`}>
                            {movement.type}
                          </span>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          {movement.sourceDestination || '-'}
                        </td>
                        <td className="px-6 py-4 text-sm text-gray-500">
                          {movement.comments || '-'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-gray-500">No part movements recorded for this tool.</p>
            )}
          </div>
        )}

        {/* Passdowns Tab */}
        {activeTab === 'passdowns' && (
          <div>
            <div className="flex justify-between items-center mb-6">
              <h3 className="text-lg font-medium text-gray-900">Tool Passdowns</h3>
              <button className="bg-green-600 hover:bg-green-700 text-white py-2 px-4 rounded flex items-center">
                <span className="material-icons mr-1">add</span>
                New Passdown
              </button>
            </div>
            
            {/* For now, just showing a placeholder since we haven't loaded passdowns */}
            <p className="text-gray-500">Passdown information will be displayed here.</p>
          </div>
        )}

        {/* Documents Tab */}
        {activeTab === 'documents' && (
          <div>
            <div className="flex justify-between items-center mb-6">
              <h3 className="text-lg font-medium text-gray-900">Documents & Images</h3>
              <button className="bg-green-600 hover:bg-green-700 text-white py-2 px-4 rounded flex items-center">
                <span className="material-icons mr-1">upload</span>
                Upload
              </button>
            </div>
            
            {/* We would display document and image galleries here */}
            <p className="text-gray-500">Document and image galleries will be displayed here.</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default ToolDetail; 