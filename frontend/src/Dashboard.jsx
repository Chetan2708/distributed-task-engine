import { useState, useCallback, useEffect } from 'react';
import {
  Activity,
  Zap,
  Play,
  Pause,
  Clock,
  CheckCircle,
  AlertOctagon,
  Layers,
  ArrowUpRight
} from 'lucide-react';
import { fetchWithAuth, isCurrentUserAdmin } from './api';
import { usePolling } from './usePolling';
import { Link } from 'react-router-dom';

export default function Dashboard({ onOpenCreateJob }) {
  const [stats, setStats] = useState(null);
  const isAdmin = isCurrentUserAdmin();
  const [liveEnabled, setLiveEnabled] = useState(true);
  const [timeAgo, setTimeAgo] = useState('just now');

  const fetchStats = useCallback(async () => {
    try {
      if (isAdmin) {
        const res = await fetchWithAuth('/jobs/stats');
        if (res.ok) {
          const data = await res.json();
          setStats(data);
        }
      } else {
        const jobsRes = await fetchWithAuth('/jobs?page=0&size=100');
        if (jobsRes.ok) {
          const jobsData = await jobsRes.json();
          const list = jobsData.content || [];
          const userStats = {
            total: jobsData.totalElements ?? list.length,
            queued: list.filter((j) => j.status === 'QUEUED' || j.status === 'CREATED').length,
            processing: list.filter((j) => j.status === 'PROCESSING').length,
            successful: list.filter((j) => j.status === 'SUCCESS').length,
            failed: list.filter((j) => j.status === 'FAILED' || j.status === 'ERROR').length,
            dlq: list.filter((j) => j.status === 'FAILED').length,
          };
          setStats(userStats);
        }
      }
    } catch (err) {
      console.error('Error fetching dashboard stats:', err);
    }
  }, [isAdmin]);

  const { lastUpdated, isRefreshing, refreshNow } = usePolling(
    fetchStats,
    2000,
    liveEnabled
  );

  // Update "X seconds ago" ticker
  useEffect(() => {
    const updateTicker = () => {
      const diffSec = Math.floor((Date.now() - lastUpdated) / 1000);
      if (diffSec < 2) setTimeAgo('just now');
      else setTimeAgo(`${diffSec}s ago`);
    };

    updateTicker();
    const interval = setInterval(updateTicker, 1000);
    return () => clearInterval(interval);
  }, [lastUpdated]);

  return (
    <div className="page-wrapper">
      <div className="page-header-row">
        <div>
          <div className="header-title-wrap">
            <h2 className="page-title" style={{ margin: 0 }}>
              {isAdmin === false ? 'Personal Dashboard' : 'Cluster Dashboard'}
            </h2>
            {liveEnabled && <span className="live-pulse-dot" title="Live Polling Active" />}
            {isAdmin === false && (
              <span className="sidebar-chip" style={{ background: '#238636', color: '#fff', fontSize: '0.7rem' }}>
                USER
              </span>
            )}
            {isAdmin === true && (
              <span className="sidebar-chip" style={{ background: '#8957e5', color: '#fff', fontSize: '0.7rem' }}>
                ADMIN
              </span>
            )}
          </div>
          <span className="page-subtitle">
            {isAdmin === false
              ? `Real-time metrics for your dispatched jobs • Updated ${timeAgo}`
              : `Real-time telemetry from Kafka consumers and PostgreSQL outbox • Updated ${timeAgo}`}
          </span>
        </div>

        <div className="header-actions">
          <button
            type="button"
            className={`live-toggle-btn ${liveEnabled ? 'active' : ''}`}
            onClick={() => setLiveEnabled(!liveEnabled)}
            title={liveEnabled ? 'Pause live auto-refresh' : 'Resume 2s live auto-refresh'}
          >
            {liveEnabled ? (
              <>
                <Pause size={14} /> Live Sync (2s)
              </>
            ) : (
              <>
                <Play size={14} /> Paused
              </>
            )}
          </button>

          {onOpenCreateJob && (
            <button
              type="button"
              className="btn-primary"
              onClick={onOpenCreateJob}
            >
              <Zap size={16} /> Trigger Job
            </button>
          )}
        </div>
      </div>

      {!stats ? (
        <div className="dashboard-grid">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <div key={i} className="card stat-card skeleton shimmer" style={{ height: '110px' }} />
          ))}
        </div>
      ) : (
        <div className="dashboard-grid">
          <div className="card stat-card stat-card-total">
            <div className="stat-card-header">
              <span className="stat-title">Total Submissions</span>
              <Layers size={18} className="stat-icon" color="#848d97" />
            </div>
            <div className="stat-value">{stats.total || 0}</div>
            <span className="stat-footnote">All recorded jobs</span>
          </div>

          <div className="card stat-card stat-card-queued">
            <div className="stat-card-header">
              <span className="stat-title">In Queue</span>
              <Clock size={18} className="stat-icon" color="var(--warning-color)" />
            </div>
            <div className="stat-value" style={{ color: 'var(--warning-color)' }}>
              {stats.queued || 0}
            </div>
            <span className="stat-footnote">Waiting for worker pick-up</span>
          </div>

          <div className="card stat-card stat-card-processing">
            <div className="stat-card-header">
              <span className="stat-title">Processing</span>
              <Activity size={18} className="stat-icon spin-subtle" color="var(--accent-color)" />
            </div>
            <div className="stat-value" style={{ color: 'var(--accent-color)' }}>
              {stats.processing || 0}
            </div>
            <span className="stat-footnote">Active consumer threads</span>
          </div>

          <div className="card stat-card stat-card-success">
            <div className="stat-card-header">
              <span className="stat-title">Successful</span>
              <CheckCircle size={18} className="stat-icon" color="var(--success-color)" />
            </div>
            <div className="stat-value" style={{ color: 'var(--success-color)' }}>
              {stats.successful || 0}
            </div>
            <span className="stat-footnote">Completed without errors</span>
          </div>

          <div className="card stat-card stat-card-failed">
            <div className="stat-card-header">
              <span className="stat-title">Failed / Errors</span>
              <AlertOctagon size={18} className="stat-icon" color="var(--error-color)" />
            </div>
            <div className="stat-value" style={{ color: 'var(--error-color)' }}>
              {stats.failed || 0}
            </div>
            <span className="stat-footnote">Exhausted retry policies</span>
          </div>

          <div className="card stat-card stat-card-dlq">
            <div className="stat-card-header">
              <span className="stat-title">Dead Letter Queue</span>
              <AlertOctagon size={18} className="stat-icon" color="#d29922" />
            </div>
            <div className="stat-value" style={{ color: '#d29922' }}>
              {stats.dlq || 0}
            </div>
            <span className="stat-footnote">Poison pills in job-dlq</span>
          </div>
        </div>
      )}

      {/* Quick Launchpad & Pipeline Overview */}
      <div className="dashboard-banner">
        <div className="banner-content">
          <h3>Interactive Distributed Architecture</h3>
          <p>
            Experience asynchronous job routing, Redis distributed locking, and Kafka Dead Letter
            Queues in action. Dispatch background tasks or inject intentional faults.
          </p>
        </div>
        <div className="banner-actions">
          {onOpenCreateJob && (
            <button
              type="button"
              className="btn-banner-trigger"
              onClick={onOpenCreateJob}
            >
              <Zap size={16} /> Open Orchestration Console
            </button>
          )}
          <Link to="/jobs" className="btn-banner-link">
            Explore All Jobs <ArrowUpRight size={16} />
          </Link>
        </div>
      </div>
    </div>
  );
}
