import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';

export default function Jobs() {
  const [jobs, setJobs] = useState([]);

  useEffect(() => {
    fetch('http://localhost:8080/api/v1/jobs')
      .then(res => res.json())
      .then(data => setJobs(data))
      .catch(err => console.error(err));
  }, []);

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
      </div>
    </div>
  );
}
