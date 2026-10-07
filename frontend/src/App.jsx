import { BrowserRouter as Router, Routes, Route, NavLink } from 'react-router-dom';
import { LayoutDashboard, List, Settings, LogOut } from 'lucide-react';
import { useState } from 'react';
import Dashboard from './Dashboard';
import Jobs from './Jobs';
import JobDetails from './JobDetails';
import Login from './Login';

function App() {
  const [token, setToken] = useState(localStorage.getItem('token'));

  if (!token) {
    return <Login onLogin={setToken} />;
  }

  const handleLogout = () => {
    localStorage.removeItem('token');
    setToken(null);
  };

  return (
    <Router>
      <div className="app-container">
        <aside className="sidebar">
          <h1>JobFlow</h1>
          <nav>
            <NavLink to="/" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`} end>
              <LayoutDashboard size={20} />
              Dashboard
            </NavLink>
            <NavLink to="/jobs" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              <List size={20} />
              Jobs
            </NavLink>
            <a href="#" className="nav-link" style={{opacity: 0.5, cursor: 'not-allowed'}}>
              <Settings size={20} />
              Settings
            </a>
            <a href="#" className="nav-link" onClick={handleLogout} style={{marginTop: 'auto'}}>
              <LogOut size={20} />
              Logout
            </a>
          </nav>
        </aside>
        
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/jobs" element={<Jobs />} />
          <Route path="/jobs/:id" element={<JobDetails />} />
        </Routes>
      </div>
    </Router>
  );
}

export default App;
