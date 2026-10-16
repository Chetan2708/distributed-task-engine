import { useState, useCallback, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import {
  Play,
  Pause,
  Zap,
  ArrowRight,
  Inbox,
  Filter,
  RefreshCw,
  Sparkles
} from 'lucide-react';
import { fetchWithAuth } from './api';
import { usePolling } from './usePolling';

export default function Jobs({ onOpenCreateJob }) {
  const [jobs, setJobs] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [liveEnabled, setLiveEnabled] = useState(true);
  const [loading, setLoading] = useState(true);

  // Keep track of recent job IDs for highlight effect
  const prevJobIdsRef = useRef(new Set());
  const [newlyAddedIds, setNewlyAddedIds] = useState(new Set());

  const fetchJobs = useCallback(async () => {
    try {
      const res = await fetchWithAuth(`/jobs?page=${page}&size=10&sort=createdAt,desc`);
      if (res.ok) {
        const data = await res.json();
        const content = data.content || [];
        setJobs(content);
        setTotalPages(data.totalPages || 0);
        setTotalElements(data.totalElements || 0);

        // Detect new incoming jobs for animation
        if (prevJobIdsRef.current.size > 0) {
          const freshIds = new Set();
          content.forEach((job) => {
            if (!prevJobIdsRef.current.has(job.id)) {
              freshIds.add(job.id);
            }
          });
          if (freshIds.size > 0) {
            setNewlyAddedIds(freshIds);
            setTimeout(() => {
              setNewlyAddedIds(new Set());
            }, 3000);
          }
        }
        prevJobIdsRef.current = new Set(content.map((j) => j.id));
      }
    } catch (err) {
      console.error('Error fetching jobs:', err);
    } finally {
      setLoading(false);
    }
  }, [page]);

  const { isRefreshing, refreshNow } = usePolling(fetchJobs, 2000, liveEnabled);

  const getBadgeClass = (status) => {
    switch (status) {
      case 'SUCCESS': return 'badge-success';
      case 'PROCESSING': return 'badge-processing badge-pulsing';
      case 'ERROR': return 'badge-warning';
      case 'FAILED': return 'badge-error';
      case 'QUEUED': return 'badge-queued';
      default: return 'badge-default';
    }
  };

  const formatTime = (ts) => {
    if (!ts) return '—';
    const d = new Date(ts);
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  };

  return (
    <div className="page-wrapper">
      <div className="page-header-row">
        <div>
          <div className="header-title-wrap">
            <h2 className="page-title" style={{ margin: 0 }}>Jobs Pipeline</h2>
            {liveEnabled && <span className="live-pulse-dot" title="Live Auto-Refresh Active" />}
          </div>
          <span className="page-subtitle">
            Showing {jobs.length} of {totalElements} cluster tasks • Auto-syncing
          </span>
        </div>

        <div className="header-actions">
          <button
            type="button"
            className={`live-toggle-btn ${liveEnabled ? 'active' : ''}`}
            onClick={() => setLiveEnabled(!liveEnabled)}
            title={liveEnabled ? 'Pause live polling' : 'Resume live polling'}
          >
            {liveEnabled ? (
              <>
                <Pause size={14} /> Live Stream (2s)
              </>
            ) : (
              <>
                <Play size={14} /> Paused
              </>
            )}
          </button>

          <button
            type="button"
            className="btn-icon-action"
            onClick={refreshNow}
            title="Refresh now"
          >
            <RefreshCw size={15} />
          </button>

          {onOpenCreateJob && (
            <button
              type="button"
              className="btn-primary"
              onClick={onOpenCreateJob}
            >
              <Zap size={16} /> New Job
            </button>
          )}
        </div>
      </div>

      <div className="card table-container">
        <table className="data-table">
          <thead>
            <tr>
              <th style={{ width: '220px' }}>Job ID</th>
              <th>Type</th>
              <th>Priority</th>
              <th>Status</th>
              <th>Retries</th>
              <th>Submitted</th>
              <th style={{ textAlign: 'right' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading && jobs.length === 0 ? (
              [1, 2, 3, 4, 5].map((i) => (
                <tr key={i}>
                  <td colSpan="7">
                    <div className="skeleton shimmer" style={{ height: '36px', borderRadius: '4px' }} />
                  </td>
                </tr>
              ))
            ) : jobs.length === 0 ? (
              <tr>
                <td colSpan="7">
                  <div className="empty-state-box">
                    <Inbox size={42} color="#848d97" />
                    <h3>No Jobs Recorded</h3>
                    <p>Trigger your first job to watch it dispatch through Kafka and process live.</p>
                    {onOpenCreateJob && (
                      <button
                        type="button"
                        className="btn-primary"
                        onClick={onOpenCreateJob}
                        style={{ marginTop: '1rem' }}
                      >
                        <Zap size={16} /> Trigger New Job Now
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ) : (
              jobs.map((job) => {
                const isNew = newlyAddedIds.has(job.id);
                return (
                  <tr key={job.id} className={isNew ? 'row-new-highlight' : ''}>
                    <td>
                      <span className="mono-job-id" title={job.id}>
                        {job.id.slice(0, 8)}...{job.id.slice(-4)}
                      </span>
                    </td>
                    <td>
                      <span className="type-tag">
                        {job.type === 'REPORT' && '📊 '}
                        {job.type === 'PDF_GENERATION' && '📄 '}
                        {job.type === 'EMAIL' && '✉️ '}
                        {job.type}
                      </span>
                    </td>
                    <td>
                      <span className="priority-pill">
                        P{job.priority || 0}
                      </span>
                    </td>
                    <td>
                      <span className={`badge ${getBadgeClass(job.status)}`}>
                        {job.status}
                      </span>
                    </td>
                    <td>
                      <span className="retries-text">
                        {job.retryCount || 0} / {job.maxRetries || 3}
                      </span>
                    </td>
                    <td style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
                      {formatTime(job.createdAt)}
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <Link to={`/jobs/${job.id}`} className="table-action-link">
                        Details <ArrowRight size={14} />
                      </Link>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>

        {totalPages > 1 && (
          <div className="table-pagination">
            <button
              type="button"
              className="pagination-btn"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
            >
              Previous
            </button>
            <span className="pagination-text">
              Page {page + 1} of {totalPages}
            </span>
            <button
              type="button"
              className="pagination-btn"
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page === totalPages - 1}
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
