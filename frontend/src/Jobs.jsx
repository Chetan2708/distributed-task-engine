import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { fetchWithAuth } from './api';

export default function Jobs() {
  const [jobs, setJobs] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  useEffect(() => {
    fetchWithAuth(`/jobs?page=${page}&size=10&sort=createdAt,desc`)
      .then(res => res.json())
      .then(data => {
        setJobs(data.content || []);
        setTotalPages(data.totalPages || 0);
      })
      .catch(err => console.error(err));
  }, [page]);

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

  return (
    <div className="main-content">
      <h2 className="page-title">Jobs</h2>
      
      <div className="card">
        <table className="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Type</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {jobs.map(job => (
              <tr key={job.id}>
                <td style={{fontFamily: 'monospace'}}>{job.id}</td>
                <td>{job.type}</td>
                <td>
                  <span className={`badge ${getBadgeClass(job.status)}`}>
                    {job.status}
                  </span>
                </td>
                <td>
                  <Link to={`/jobs/${job.id}`}>View Details</Link>
                </td>
              </tr>
            ))}
            {jobs.length === 0 && (
              <tr>
                <td colSpan="4" style={{textAlign: 'center', padding: '2rem'}}>No jobs found</td>
              </tr>
            )}
          </tbody>
        </table>
        
        {totalPages > 1 && (
          <div style={{ display: 'flex', justifyContent: 'center', gap: '1rem', padding: '1rem', borderTop: '1px solid var(--border-color)' }}>
            <button 
              onClick={() => setPage(p => Math.max(0, p - 1))} 
              disabled={page === 0}
              style={{ padding: '0.5rem 1rem', background: 'var(--bg-dark)', color: 'white', border: '1px solid var(--border-color)', borderRadius: '4px', cursor: page === 0 ? 'not-allowed' : 'pointer' }}
            >
              Previous
            </button>
            <span style={{ display: 'flex', alignItems: 'center', color: 'var(--text-secondary)' }}>
              Page {page + 1} of {totalPages}
            </span>
            <button 
              onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} 
              disabled={page === totalPages - 1}
              style={{ padding: '0.5rem 1rem', background: 'var(--bg-dark)', color: 'white', border: '1px solid var(--border-color)', borderRadius: '4px', cursor: page === totalPages - 1 ? 'not-allowed' : 'pointer' }}
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
