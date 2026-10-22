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
import { fetchWithAuth, isCurrentUserAdmin, subscribeToJobStream } from './api';
import { Link } from 'react-router-dom';

export default function Dashboard({ onOpenCreateJob }) {
  const [stats, setStats] = useState(null);
  const isAdmin = isCurrentUserAdmin();
  const [liveEnabled, setLiveEnabled] = useState(true);
  const [sseConnected, setSseConnected] = useState(false);
  const [lastUpdated, setLastUpdated] = useState(Date.now());
  const [timeAgo, setTimeAgo] = useState('just now');
  const [lastEvent, setLastEvent] = useState(null);

  const fetchStats = useCallback(async () => {
    try {
      if (isAdmin) {
        const res = await fetchWithAuth('/jobs/stats');
        if (res.ok) {
          const data = await res.json();
          setStats(data);
          setLastUpdated(Date.now());
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
          setLastUpdated(Date.now());
        }
      }
    } catch (err) {
      console.error('Error fetching dashboard stats:', err);
    }
  }, [isAdmin]);

  // Initial fetch on mount or role change
  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  // Real-Time Push Stream via Server-Sent Events (SSE) - No periodic polling!
  useEffect(() => {
    if (!liveEnabled) {
      setSseConnected(false);
      return;
    }

    const unsubscribe = subscribeToJobStream(
      (event) => {
        setLastEvent(event);
        fetchStats();
      },
      (err) => {
        setSseConnected(false);
      },
      (conn) => {
        setSseConnected(true);
      }
    );

    return () => {
      unsubscribe();
      setSseConnected(false);
    };
  }, [liveEnabled, fetchStats]);

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
            {liveEnabled && (
              <span
                className="live-pulse-dot"
                style={{ background: sseConnected ? '#2ea043' : '#d29922' }}
                title={sseConnected ? 'Real-Time SSE Push Active (0 Polling)' : 'Connecting to Event Stream...'}
              />
            )}
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
              ? `Real-time push metrics for your dispatched jobs • Updated ${timeAgo}`
              : `Real-time telemetry streamed via Kafka, Redis & SSE • Updated ${timeAgo}`}
          </span>
          {lastEvent && (
            <div style={{ marginTop: '0.25rem', fontSize: '0.75rem', color: '#58a6ff' }}>
              ⚡ Last Event: Job <code>{String(lastEvent.jobId).slice(0, 8)}</code> transitioned to{' '}
              <strong style={{ color: lastEvent.status === 'SUCCESS' ? '#3fb950' : lastEvent.status === 'FAILED' ? '#f85149' : '#d29922' }}>
                {lastEvent.status}
              </strong>
            </div>
          )}
        </div>

        <div className="header-actions">
          <button
            type="button"
            className={`live-toggle-btn ${liveEnabled ? 'active' : ''}`}
            onClick={() => setLiveEnabled(!liveEnabled)}
            title={liveEnabled ? 'Pause real-time push stream' : 'Resume real-time push stream'}
          >
            {liveEnabled ? (
              <>
                <Zap size={14} style={{ color: sseConnected ? '#2ea043' : '#d29922' }} />
                {sseConnected ? 'SSE Live Stream' : 'Connecting...'}
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
