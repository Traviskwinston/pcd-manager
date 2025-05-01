import React, { useState, useEffect } from 'react';
import api from '../../services/api';
import { useAuth } from '../../context/AuthContext';

const UserManagement = () => {
  const { currentUser } = useAuth();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [editingUser, setEditingUser] = useState(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    userType: 'TECHNICIAN',
    admin: false
  });

  // Redirect if not admin
  if (currentUser && !currentUser.admin) {
    return (
      <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded" role="alert">
        <p>You do not have permission to access this page.</p>
      </div>
    );
  }

  useEffect(() => {
    const fetchUsers = async () => {
      try {
        setLoading(true);
        const response = await api.users.getAll();
        setUsers(response.data);
      } catch (err) {
        console.error('Error fetching users:', err);
        setError('Failed to load users. Please try again later.');
      } finally {
        setLoading(false);
      }
    };

    fetchUsers();
  }, []);

  const handleInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData({
      ...formData,
      [name]: type === 'checkbox' ? checked : value
    });
  };

  const resetForm = () => {
    setFormData({
      firstName: '',
      lastName: '',
      email: '',
      password: '',
      userType: 'TECHNICIAN',
      admin: false
    });
    setEditingUser(null);
    setShowAddForm(false);
  };

  const handleAddNewClick = () => {
    resetForm();
    setShowAddForm(true);
  };

  const handleEditClick = (user) => {
    setFormData({
      firstName: user.firstName,
      lastName: user.lastName,
      email: user.email,
      password: '', // Don't prefill password
      userType: user.userType,
      admin: user.admin
    });
    setEditingUser(user.id);
    setShowAddForm(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      if (editingUser) {
        // If editing, update the user
        const userData = { ...formData };
        if (!userData.password) {
          // Don't send password if it's empty (meaning no change)
          delete userData.password;
        }
        
        const response = await api.users.update(editingUser, userData);
        
        // Update the users list
        setUsers(users.map(user => 
          user.id === editingUser ? response.data : user
        ));
      } else {
        // If adding, create a new user
        const response = await api.users.create(formData);
        setUsers([...users, response.data]);
      }
      
      // Reset form and state
      resetForm();
    } catch (err) {
      console.error('Error saving user:', err);
      alert(
        err.response?.data?.message || 
        'Failed to save user. Please check your inputs and try again.'
      );
    }
  };

  const handleDelete = async (id) => {
    // Prevent deletion of current user
    if (id === currentUser.id) {
      alert('You cannot delete yourself.');
      return;
    }
    
    if (window.confirm('Are you sure you want to delete this user?')) {
      try {
        await api.users.delete(id);
        setUsers(users.filter(user => user.id !== id));
      } catch (err) {
        console.error('Error deleting user:', err);
        alert('Failed to delete user.');
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

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold text-gray-800">User Management</h1>
        <button
          onClick={handleAddNewClick}
          className="bg-blue-600 hover:bg-blue-700 text-white py-2 px-4 rounded flex items-center"
        >
          <span className="material-icons mr-1">person_add</span>
          Add User
        </button>
      </div>

      {/* User form (add/edit) */}
      {showAddForm && (
        <div className="bg-white p-6 rounded-lg shadow-md">
          <h2 className="text-xl font-semibold mb-4">
            {editingUser ? 'Edit User' : 'Add New User'}
          </h2>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label htmlFor="firstName" className="block text-sm font-medium text-gray-700 mb-1">
                  First Name
                </label>
                <input
                  type="text"
                  id="firstName"
                  name="firstName"
                  value={formData.firstName}
                  onChange={handleInputChange}
                  required
                  className="block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                />
              </div>
              
              <div>
                <label htmlFor="lastName" className="block text-sm font-medium text-gray-700 mb-1">
                  Last Name
                </label>
                <input
                  type="text"
                  id="lastName"
                  name="lastName"
                  value={formData.lastName}
                  onChange={handleInputChange}
                  required
                  className="block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                />
              </div>
            </div>
            
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-1">
                Email Address
              </label>
              <input
                type="email"
                id="email"
                name="email"
                value={formData.email}
                onChange={handleInputChange}
                required
                className="block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
              />
            </div>
            
            <div>
              <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">
                {editingUser ? 'New Password (leave blank to keep current)' : 'Password'}
              </label>
              <input
                type="password"
                id="password"
                name="password"
                value={formData.password}
                onChange={handleInputChange}
                required={!editingUser}
                className="block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
              />
            </div>
            
            <div>
              <label htmlFor="userType" className="block text-sm font-medium text-gray-700 mb-1">
                User Type
              </label>
              <select
                id="userType"
                name="userType"
                value={formData.userType}
                onChange={handleInputChange}
                className="block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
              >
                <option value="TECHNICIAN">Technician</option>
                <option value="CUSTOMER">Customer</option>
                <option value="ENGINEER">Engineer</option>
              </select>
            </div>
            
            <div className="flex items-center">
              <input
                type="checkbox"
                id="admin"
                name="admin"
                checked={formData.admin}
                onChange={handleInputChange}
                className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300 rounded"
              />
              <label htmlFor="admin" className="ml-2 block text-sm text-gray-900">
                Admin User
              </label>
            </div>
            
            <div className="flex justify-end space-x-3">
              <button
                type="button"
                onClick={resetForm}
                className="px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
              >
                {editingUser ? 'Update User' : 'Add User'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* User list */}
      <div className="bg-white rounded-lg shadow-md overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Name
                </th>
                <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Email
                </th>
                <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Type
                </th>
                <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Admin
                </th>
                <th scope="col" className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {users.length > 0 ? (
                users.map((user) => (
                  <tr key={user.id} className="hover:bg-gray-50">
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm font-medium text-gray-900">
                        {user.firstName} {user.lastName}
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm text-gray-500">{user.email}</div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-green-100 text-green-800">
                        {user.userType}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      {user.admin ? (
                        <span className="material-icons text-green-600">check_circle</span>
                      ) : (
                        <span className="material-icons text-gray-400">cancel</span>
                      )}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                      <button 
                        onClick={() => handleEditClick(user)}
                        className="text-blue-600 hover:text-blue-900 mr-4"
                      >
                        Edit
                      </button>
                      <button 
                        onClick={() => handleDelete(user.id)}
                        className="text-red-600 hover:text-red-900"
                        disabled={user.id === currentUser?.id}
                      >
                        {user.id === currentUser?.id ? 'Current User' : 'Delete'}
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan="5" className="px-6 py-4 text-center text-gray-500">
                    No users available. Add your first user!
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

export default UserManagement; 