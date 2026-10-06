import { BrowserRouter as Router, Routes, Route, NavLink } from 'react-router-dom';
import { LayoutDashboard, List, Settings } from 'lucide-react';
import Dashboard from './Dashboard';
import Jobs from './Jobs';
import JobDetails from './JobDetails';

function App() {
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
