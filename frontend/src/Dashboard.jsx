import { useState, useEffect } from 'react';
import { Activity } from 'lucide-react';
import { fetchWithAuth } from './api';

export default function Dashboard() {
  const [stats, setStats] = useState(null);

  useEffect(() => {
    fetchWithAuth('/jobs/stats')
      .then(res => res.json())
      .then(data => setStats(data))
      .catch(err => console.error(err));
  }, []);

  if (!stats) return <div className="main-content">Loading...</div>;

  return (
    <div className="main-content">
      <h2 className="page-title">Dashboard</h2>
      
      <div className="dashboard-grid">
        <div className="card stat-card">
          <div className="stat-title">Total Jobs</div>
          <div className="stat-value">{stats.total || 0}</div>
        </div>
        
        <div className="card stat-card" style={{ borderLeft: '4px solid var(--warning-color)'}}>
          <div className="stat-title">Queued</div>
          <div className="stat-value" style={{color: 'var(--warning-color)'}}>{stats.queued || 0}</div>
        </div>
        
        <div className="card stat-card" style={{ borderLeft: '4px solid var(--accent-color)'}}>
          <div className="stat-title">Processing</div>
          <div className="stat-value" style={{color: 'var(--accent-color)'}}>{stats.processing || 0}</div>
        </div>
        
        <div className="card stat-card" style={{ borderLeft: '4px solid var(--success-color)'}}>
          <div className="stat-title">Successful</div>
          <div className="stat-value" style={{color: 'var(--success-color)'}}>{stats.successful || 0}</div>
        </div>
        
        <div className="card stat-card" style={{ borderLeft: '4px solid var(--error-color)'}}>
          <div className="stat-title">Failed</div>
          <div className="stat-value" style={{color: 'var(--error-color)'}}>{stats.failed || 0}</div>
        </div>
        
        <div className="card stat-card" style={{ borderLeft: '4px solid #848d97'}}>
          <div className="stat-title">DLQ</div>
          <div className="stat-value" style={{color: '#848d97'}}>{stats.dlq || 0}</div>
        </div>
      </div>
    </div>
  );
}
