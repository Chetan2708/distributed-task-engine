import { useState, useEffect, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ArrowLeft,
  Clock,
  Cpu,
  RefreshCw,
  CheckCircle2,
  AlertTriangle,
  RotateCw,
  BarChart2,
  FileCode,
  Layers,
  Database
} from 'lucide-react';
import { fetchWithAuth } from './api';
import Timeline from './Timeline';

export default function JobDetails() {
  const { id } = useParams();
  const [job, setJob] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isPolling, setIsPolling] = useState(false);

  const fetchJob = async () => {
    try {
      const res = await fetchWithAuth(`/jobs/${id}`);
      if (res.ok) {
        const data = await res.json();
        setJob(data);
        return data;
      }
    } catch (err) {
      console.error('Error fetching job details:', err);
    } finally {
      setLoading(false);
    }
    return null;
  };

  useEffect(() => {
    let timer = null;
    let isMounted = true;

    const poll = async () => {
      const data = await fetchJob();
      if (!isMounted) return;

      // If job is in active/in-progress state, continue polling every 1.5s
      if (data && (data.status === 'CREATED' || data.status === 'QUEUED' || data.status === 'PROCESSING' || data.status === 'ERROR')) {
        setIsPolling(true);
        timer = setTimeout(poll, 1500);
      } else {
        setIsPolling(false);
      }
    };

    poll();

    return () => {
      isMounted = false;
      if (timer) clearTimeout(timer);
    };
  }, [id]);

  if (loading) {
    return (
      <div className="page-wrapper">
        <div className="skeleton-details">
          <div className="skeleton shimmer" style={{ height: '40px', width: '220px', borderRadius: '8px', marginBottom: '1.5rem' }} />
          <div className="skeleton shimmer" style={{ height: '140px', borderRadius: '12px', marginBottom: '1.5rem' }} />
          <div className="skeleton shimmer" style={{ height: '320px', borderRadius: '12px' }} />
        </div>
      </div>
    );
  }

  if (!job) {
    return (
      <div className="page-wrapper">
        <div className="card empty-state-box">
          <AlertTriangle size={48} color="#f85149" />
          <h3>Job Not Found</h3>
          <p>We couldn't retrieve job with ID <code>{id}</code></p>
          <Link to="/jobs" className="btn-secondary">
            ← Return to Jobs
          </Link>
        </div>
      </div>
    );
  }

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

  const formatDate = (dateString) => {
    if (!dateString) return '—';
    const d = new Date(dateString);
    return d.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  };

  const calculateDurations = () => {
    if (!job.createdAt) return null;
    const created = new Date(job.createdAt).getTime();
    const started = job.startedAt ? new Date(job.startedAt).getTime() : null;
    const completed = job.completedAt ? new Date(job.completedAt).getTime() : null;

    const queueMs = started ? Math.max(0, started - created) : null;
    const processMs = started && completed ? Math.max(0, completed - started) : null;
    const totalMs = completed ? Math.max(0, completed - created) : null;

    const formatMs = (ms) => {
      if (ms === null || isNaN(ms)) return null;
      if (ms < 1000) return `${ms}ms`;
      return `${(ms / 1000).toFixed(2)}s`;
    };

    return {
      queueWait: formatMs(queueMs),
      processingTime: formatMs(processMs),
      totalDuration: formatMs(totalMs),
    };
  };

  const durations = calculateDurations();
  const isTerminal = job.status === 'SUCCESS' || job.status === 'FAILED';

  return (
    <div className="page-wrapper">
      {/* Top Header */}
      <div className="page-header-row">
        <div className="header-left">
          <Link to="/jobs" className="back-link" title="Back to jobs">
            <ArrowLeft size={18} />
          </Link>
          <div>
            <div className="header-title-wrap">
              <h2 className="page-title" style={{ margin: 0 }}>Job Details</h2>
              <span className={`badge ${getBadgeClass(job.status)}`}>
                {job.status}
              </span>
              {isPolling && (
                <span className="live-indicator">
                  <span className="live-dot" /> Live Auto-Sync
                </span>
              )}
            </div>
            <span className="mono-job-id">{job.id}</span>
          </div>
        </div>

        <button
          type="button"
          className="btn-icon-action"
          onClick={fetchJob}
          title="Manual Refresh"
        >
          <RefreshCw size={16} />
        </button>
      </div>

      {/* Lifecycle Timeline */}
      <div className="card timeline-card">
        <h4 className="card-subheading">
          <Clock size={16} /> Distributed Execution Lifecycle
        </h4>
        <Timeline
          status={job.status}
          createdAt={job.createdAt}
          startedAt={job.startedAt}
          completedAt={job.completedAt}
          retryCount={job.retryCount}
        />
      </div>

      {/* Latency Benchmarks Bar */}
      {durations && (durations.queueWait || durations.processingTime) && (
        <div className="durations-bar">
          {durations.queueWait && (
            <div className="duration-pill">
              <span className="duration-pill-label">Queue Latency</span>
              <span className="duration-pill-val">{durations.queueWait}</span>
            </div>
          )}
          {durations.processingTime && (
            <div className="duration-pill">
              <span className="duration-pill-label">Worker Run Time</span>
              <span className="duration-pill-val">{durations.processingTime}</span>
            </div>
          )}
          {durations.totalDuration && (
            <div className="duration-pill highlight">
              <span className="duration-pill-label">Total End-to-End</span>
              <span className="duration-pill-val">{durations.totalDuration}</span>
            </div>
          )}
        </div>
      )}

      {/* Real-time Computed Report Results */}
      {job.result && job.type === 'REPORT' && (
        <div className="card report-result-card">
          <div className="report-header">
            <div className="report-title-group">
              <div className="report-icon-badge">
                <BarChart2 size={22} color="#3fb950" />
              </div>
              <div>
                <h3 className="report-main-title">Real-Time System Analytics Report</h3>
                <span className="report-timestamp">
                  Generated by worker: {job.result.generatedAt || formatDate(job.completedAt)}
                </span>
              </div>
            </div>
            <span className="badge badge-real">✨ Live DB Aggregate</span>
          </div>

          <div className="report-metrics-grid">
            <div className="report-stat-box">
              <span className="report-stat-num">{job.result.totalJobs || 0}</span>
              <span className="report-stat-label">Total Jobs in DB</span>
            </div>
            <div className="report-stat-box">
              <span className="report-stat-num" style={{ color: '#3fb950' }}>
                {job.result.successRate || '100%'}
              </span>
              <span className="report-stat-label">Success Rate</span>
            </div>
            <div className="report-stat-box">
              <span className="report-stat-num" style={{ color: '#58a6ff' }}>
                {job.result.avgProcessingTimeSec != null ? `${job.result.avgProcessingTimeSec}s` : '—'}
              </span>
              <span className="report-stat-label">Avg Processing Time</span>
            </div>
          </div>

          <div className="report-breakdown-row">
            {job.result.jobsByStatus && (
              <div className="breakdown-card">
                <h4>Jobs by Status</h4>
                <div className="breakdown-list">
                  {Object.entries(job.result.jobsByStatus).map(([st, cnt]) => (
                    <div key={st} className="breakdown-item">
                      <span className="breakdown-label">{st}</span>
                      <span className="breakdown-count">{cnt}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {job.result.jobsByType && (
              <div className="breakdown-card">
                <h4>Jobs by Type</h4>
                <div className="breakdown-list">
                  {Object.entries(job.result.jobsByType).map(([tp, cnt]) => (
                    <div key={tp} className="breakdown-item">
                      <span className="breakdown-label">{tp}</span>
                      <span className="breakdown-count">{cnt}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Result for Non-Report Jobs (Simulated Handlers) */}
      {job.result && job.type !== 'REPORT' && (
        <div className="card">
          <h4 className="card-subheading">
            <CheckCircle2 size={16} color="#3fb950" /> Handler Output
          </h4>
          <pre className="json-pre-block">
            {JSON.stringify(job.result, null, 2)}
          </pre>
        </div>
      )}

      {/* Main Details Grid */}
      <div className="card">
        <h4 className="card-subheading">
          <Layers size={16} /> Job Metadata & Environment
        </h4>
        <div className="details-grid">
          <div className="details-label">Type</div>
          <div className="details-value font-semibold">{job.type}</div>

          <div className="details-label">Priority</div>
          <div className="details-value">
            <span className="priority-tag">Level {job.priority || 0}</span>
          </div>

          <div className="details-label">Retry Attempts</div>
          <div className="details-value">
            {job.retryCount || 0} / {job.maxRetries || 3}
            {job.retryCount > 0 && (
              <span className="retry-status-text">
                ({job.retryCount} fault recovery cycle{job.retryCount > 1 ? 's' : ''})
              </span>
            )}
          </div>

          <div className="details-label">Created At</div>
          <div className="details-value">{formatDate(job.createdAt)}</div>

          <div className="details-label">Execution Started</div>
          <div className="details-value">{formatDate(job.startedAt)}</div>

          <div className="details-label">Execution Finished</div>
          <div className="details-value">{formatDate(job.completedAt)}</div>

          <div className="details-label">Active Worker Node</div>
          <div className="details-value flex-align-center">
            <Cpu size={14} style={{ marginRight: '6px', color: '#58a6ff' }} />
            <span>job-worker (Redisson Distributed Lock)</span>
          </div>

          {job.errorMessage && (
            <>
              <div className="details-label" style={{ color: 'var(--error-color)' }}>
                Failure Reason
              </div>
              <div className="details-value error-message-box">
                {job.errorMessage}
              </div>
            </>
          )}

          <div className="details-label">Request Payload</div>
          <div className="details-value">
            <pre className="json-pre-block">
              {JSON.stringify(job.payload, null, 2)}
            </pre>
          </div>
        </div>
      </div>
    </div>
  );
}
