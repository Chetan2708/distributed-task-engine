import {
  BrowserRouter as Router,
  Routes,
  Route,
  NavLink
} from 'react-router-dom';

import {
  LayoutDashboard,
  List,
  Zap,
  Plus,
  LogOut,
  Workflow
} from 'lucide-react';

import { useState, useCallback } from 'react';

import Dashboard from './Dashboard';
import Jobs from './Jobs';
import JobDetails from './JobDetails';
import Login from './Login';
import CreateJob from './CreateJob';
import Toast from './Toast';

function App() {
  const [token, setToken] = useState(
    localStorage.getItem('token')
  );

  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [toasts, setToasts] = useState([]);

  const showToast = useCallback((toast) => {
    const id = Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
    setToasts((prev) => [...prev, { ...toast, id }]);
  }, []);

  const dismissToast = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

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
          <div className="sidebar-brand">
            <div className="brand-icon-box">
              <Workflow size={22} color="#fff" />
            </div>
            <h1>JobFlow</h1>
          </div>

          <nav className="sidebar-nav">
            <NavLink
              to="/"
              className={({ isActive }) =>
                `nav-link ${isActive ? 'active' : ''}`
              }
              end
            >
              <LayoutDashboard size={19} />
              Dashboard
            </NavLink>

            <NavLink
              to="/jobs"
              className={({ isActive }) =>
                `nav-link ${isActive ? 'active' : ''}`
              }
            >
              <List size={19} />
              Jobs Pipeline
            </NavLink>

            <button
              type="button"
              className="nav-link trigger-nav-item"
              onClick={() => setIsCreateOpen(true)}
            >
              <Zap size={19} className="zap-trigger-icon" />
              <span>Trigger Job</span>
              <span className="sidebar-chip">NEW</span>
            </button>
          </nav>

          <div className="sidebar-footer">
            <button
              type="button"
              className="nav-link logout-btn"
              onClick={handleLogout}
            >
              <LogOut size={18} />
              Logout
            </button>
          </div>
        </aside>

        <main className="main-content">
          <Routes>
            <Route
              path="/"
              element={<Dashboard onOpenCreateJob={() => setIsCreateOpen(true)} />}
            />
            <Route
              path="/jobs"
              element={<Jobs onOpenCreateJob={() => setIsCreateOpen(true)} />}
            />
            <Route
              path="/jobs/:id"
              element={<JobDetails />}
            />
          </Routes>
        </main>

        {/* Global Floating Action Button (FAB) */}
        <button
          type="button"
          className="fab-trigger-btn"
          onClick={() => setIsCreateOpen(true)}
          title="Trigger a new job"
          aria-label="Trigger new job"
        >
          <Plus size={24} />
        </button>

        {/* Slide-in Trigger Panel */}
        <CreateJob
          isOpen={isCreateOpen}
          onClose={() => setIsCreateOpen(false)}
          showToast={showToast}
        />

        {/* Global Toast Notifications */}
        <Toast toasts={toasts} onDismiss={dismissToast} />
      </div>
    </Router>
  );
}

export default App;
