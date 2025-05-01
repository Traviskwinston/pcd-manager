import React, { useState, useEffect } from 'react';
import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import api from '../../services/api';

// Icons (to be replaced with your preferred icon library)
const DashboardIcon = () => <span className="material-icons">dashboard</span>;
const ToolsIcon = () => <span className="material-icons">build</span>;
const LocationsIcon = () => <span className="material-icons">place</span>;
const StarIcon = () => <span className="material-icons">star</span>;
const PartsIcon = () => <span className="material-icons">hardware</span>;
const PassdownsIcon = () => <span className="material-icons">article</span>;
const UsersIcon = () => <span className="material-icons">people</span>;
const LogoutIcon = () => <span className="material-icons">logout</span>;

const PageLayout = () => {
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const { currentUser, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [defaultLocation, setDefaultLocation] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchDefaultLocation = async () => {
      try {
        const response = await api.locations.getDefault();
        if (response.status === 200) {
          setDefaultLocation(response.data);
        }
      } catch (error) {
        console.log('No default location set');
      } finally {
        setLoading(false);
      }
    };

    fetchDefaultLocation();
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const isActive = (path) => {
    return location.pathname === path || location.pathname.startsWith(`${path}/`);
  };

  // Reordered navigation items with Locations before Dashboard
  const navItems = [
    { 
      to: '/locations', 
      label: defaultLocation ? defaultLocation.displayName : 'Locations', 
      icon: defaultLocation ? <StarIcon /> : <LocationsIcon />,
      isDefaultLocation: !!defaultLocation
    },
    { to: '/dashboard', label: 'Dashboard', icon: <DashboardIcon /> },
    { to: '/tools', label: 'Tools', icon: <ToolsIcon /> },
    { to: '/parts', label: 'Parts', icon: <PartsIcon /> },
    { to: '/passdowns', label: 'Passdowns', icon: <PassdownsIcon /> },
  ];

  // Admin-only menu items
  if (currentUser && currentUser.admin) {
    navItems.push({ to: '/admin/users', label: 'User Management', icon: <UsersIcon /> });
  }

  return (
    <div className="flex h-screen bg-gray-100">
      {/* Sidebar */}
      <aside 
        className={`bg-blue-800 text-white ${sidebarOpen ? 'w-64' : 'w-20'} transition-all duration-300 ease-in-out`}
      >
        {/* Logo */}
        <div className="p-4 flex items-center justify-between">
          {sidebarOpen && <span className="text-xl font-bold">PCD Manager</span>}
          <button 
            onClick={() => setSidebarOpen(!sidebarOpen)} 
            className="p-1 rounded-full hover:bg-blue-700"
          >
            <span className="material-icons">
              {sidebarOpen ? 'chevron_left' : 'chevron_right'}
            </span>
          </button>
        </div>

        {/* Navigation */}
        <nav className="mt-8">
          <ul>
            {navItems.map((item) => (
              <li key={item.to}>
                <Link
                  to={item.to}
                  className={`flex items-center py-3 px-4 ${
                    isActive(item.to)
                      ? 'bg-blue-700'
                      : 'hover:bg-blue-700'
                  } ${item.isDefaultLocation ? 'text-yellow-300' : ''} transition-colors`}
                >
                  <span className="mr-3">{item.icon}</span>
                  {sidebarOpen && <span>{item.label}</span>}
                </Link>
              </li>
            ))}
            <li>
              <button
                onClick={handleLogout}
                className="w-full flex items-center py-3 px-4 hover:bg-blue-700 transition-colors"
              >
                <span className="mr-3"><LogoutIcon /></span>
                {sidebarOpen && <span>Logout</span>}
              </button>
            </li>
          </ul>
        </nav>
      </aside>

      {/* Main content */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Header */}
        <header className="bg-white shadow-sm">
          <div className="flex items-center justify-between p-4">
            <h1 className="text-xl font-semibold text-gray-800">
              {location.pathname === '/dashboard' && 'Dashboard'}
              {location.pathname === '/tools' && 'Tool Management'}
              {location.pathname === '/locations' && 'Locations'}
              {location.pathname === '/parts' && 'Parts Inventory'}
              {location.pathname === '/passdowns' && 'Passdowns'}
              {location.pathname === '/admin/users' && 'User Management'}
            </h1>
            <div className="flex items-center">
              <span className="text-sm text-gray-600 mr-2">
                {currentUser?.firstName} {currentUser?.lastName}
              </span>
            </div>
          </div>
        </header>

        {/* Page content */}
        <main className="flex-1 overflow-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default PageLayout; 