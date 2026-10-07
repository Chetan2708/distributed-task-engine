import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { fetchWithAuth } from './api';

export default function JobDetails() {
  const { id } = useParams();
  const [job, setJob] = useState(null);

  useEffect(() => {
    fetchWithAuth(`/jobs/${id}`)
      .then(res => res.json())
      .then(data => setJob(data))
      .catch(err => console.error(err));
  }, [id]);

  if (!job) return <div className="main-content">Loading...</div>;

  const getBadgeClass = (status) => {
    switch (status) {
      case 'SUCCESS': return 'badge-success';
      case 'PROCESSING': return 'badge-processing';
      case 'ERROR':
      case 'FAILED': return 'badge-error';
      case 'QUEUED': return 'badge-queued';
      default: return 'badge-default';
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleString();
  };

  return (
    <div className="main-content">
      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '2rem' }}>
        <Link to="/jobs" style={{ fontSize: '1.5rem', textDecoration: 'none' }}>←</Link>
        <h2 className="page-title" style={{ margin: 0 }}>Job Details</h2>
      </div>
      
      <div className="card">
        <div className="details-grid">
          <div className="details-label">Job ID</div>
          <div className="details-value">{job.id}</div>
          
          <div className="details-label">Status</div>
          <div>
            <span className={`badge ${getBadgeClass(job.status)}`}>
              {job.status}
            </span>
          </div>
          
          <div className="details-label">Type</div>
          <div className="details-value">{job.type}</div>
          
          <div className="details-label">Created At</div>
          <div className="details-value">{formatDate(job.createdAt)}</div>
          
          <div className="details-label">Started At</div>
          <div className="details-value">{formatDate(job.startedAt)}</div>
          
          <div className="details-label">Completed At</div>
          <div className="details-value">{formatDate(job.completedAt)}</div>
          
          <div className="details-label">Retry Count</div>
          <div className="details-value">{job.retryCount || 0} / {job.maxRetries || 3}</div>
          
          <div className="details-label">Worker</div>
          <div className="details-value">job-worker-node-1</div>
          
          {job.errorMessage && (
            <>
              <div className="details-label" style={{color: 'var(--error-color)'}}>Error</div>
              <div className="details-value" style={{color: 'var(--error-color)'}}>{job.errorMessage}</div>
            </>
          )}
          
          <div className="details-label">Payload</div>
          <div className="details-value">
            <pre style={{ 
              background: 'rgba(0,0,0,0.2)', 
              padding: '1rem', 
              borderRadius: '0.5rem',
              overflowX: 'auto'
            }}>
              {JSON.stringify(job.payload, null, 2)}
            </pre>
          </div>
        </div>
      </div>
    </div>
  );
}
