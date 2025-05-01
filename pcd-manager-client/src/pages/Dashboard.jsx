import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';

// Card component for dashboard summary items
const SummaryCard = ({ title, count, icon, color, linkTo }) => (
  <Link 
    to={linkTo} 
    className="bg-white rounded-lg shadow-md p-6 flex items-center hover:shadow-lg transition-shadow"
  >
    <div className={`rounded-full p-3 ${color} text-white mr-4`}>
      {icon}
    </div>
    <div>
      <h3 className="text-xl font-semibold text-gray-700">{title}</h3>
      <p className="text-2xl font-bold">{count}</p>
    </div>
  </Link>
);

// Activity item component
const ActivityItem = ({ date, title, description, type }) => {
  // Different color border based on activity type
  let borderColor = "border-gray-300";
  if (type === "tool") borderColor = "border-blue-500";
  if (type === "part") borderColor = "border-green-500";
  if (type === "passdown") borderColor = "border-yellow-500";

  return (
    <div className={`border-l-4 ${borderColor} pl-4 py-2 mb-4`}>
      <div className="text-sm text-gray-500">{date}</div>
      <div className="font-medium">{title}</div>
      <div className="text-gray-600">{description}</div>
    </div>
  );
};

const Dashboard = () => {
  const { currentUser } = useAuth();
  const [summary, setSummary] = useState({
    tools: 0,
    locations: 0,
    parts: 0,
    passdowns: 0
  });
  const [recentActivity, setRecentActivity] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchDashboardData = async () => {
      try {
        setLoading(true);
        // In a real app, you would have a dashboard API endpoint
        // Here we're simulating with separate calls
        
        // Fetch summary data
        const toolsRes = await api.tools.getAll();
        const locationsRes = await api.locations.getAll();
        const partsRes = await api.parts.getAll();
        const passdownsRes = await api.passdowns.getAll();

        setSummary({
          tools: toolsRes.data.length,
          locations: locationsRes.data.length,
          parts: partsRes.data.length,
          passdowns: passdownsRes.data.length
        });

        // Fetch recent activity (this would be a dedicated API in a real app)
        // For now, simulate with hardcoded data
        setRecentActivity([
          {
            id: 1,
            date: '2023-06-01 14:30',
            title: 'Tool Updated: ASML Scanner',
            description: 'Status updated to "Maintenance"',
            type: 'tool'
          },
          {
            id: 2,
            date: '2023-06-01 11:15',
            title: 'Part Added: Laser Assembly',
            description: 'Added to inventory',
            type: 'part'
          },
          {
            id: 3,
            date: '2023-05-31 16:45',
            title: 'Passdown Created',
            description: 'New passdown for night shift',
            type: 'passdown'
          },
          {
            id: 4,
            date: '2023-05-31 09:20',
            title: 'Location Added: AZ F42',
            description: 'New fab location added',
            type: 'location'
          }
        ]);
      } catch (err) {
        console.error('Error fetching dashboard data:', err);
        setError('Failed to load dashboard data. Please try again later.');
      } finally {
        setLoading(false);
      }
    };

    fetchDashboardData();
  }, []);

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
    <div className="space-y-8">
      {/* Welcome message */}
      <div className="bg-white p-6 rounded-lg shadow-md">
        <h2 className="text-2xl font-bold text-gray-800">
          Welcome, {currentUser?.firstName}!
        </h2>
        <p className="text-gray-600 mt-2">
          Here's an overview of your PCD Manager system.
        </p>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <SummaryCard 
          title="Tools" 
          count={summary.tools}
          icon={<span className="material-icons">build</span>}
          color="bg-blue-600"
          linkTo="/tools"
        />
        <SummaryCard 
          title="Locations" 
          count={summary.locations}
          icon={<span className="material-icons">place</span>}
          color="bg-green-600"
          linkTo="/locations"
        />
        <SummaryCard 
          title="Parts" 
          count={summary.parts}
          icon={<span className="material-icons">hardware</span>}
          color="bg-yellow-600"
          linkTo="/parts"
        />
        <SummaryCard 
          title="Passdowns" 
          count={summary.passdowns}
          icon={<span className="material-icons">article</span>}
          color="bg-purple-600"
          linkTo="/passdowns"
        />
      </div>

      {/* Recent activity */}
      <div className="bg-white p-6 rounded-lg shadow-md">
        <div className="flex justify-between items-center mb-6">
          <h3 className="text-xl font-semibold text-gray-800">Recent Activity</h3>
        </div>
        <div className="space-y-4">
          {recentActivity.map((activity) => (
            <ActivityItem 
              key={activity.id}
              date={activity.date}
              title={activity.title}
              description={activity.description}
              type={activity.type}
            />
          ))}
        </div>
      </div>
    </div>
  );
};

export default Dashboard; 