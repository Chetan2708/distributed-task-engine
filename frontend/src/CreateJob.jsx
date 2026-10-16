import { useState } from 'react';
import {
  X,
  BarChart3,
  FileText,
  Mail,
  Zap,
  Flame,
  Bug,
  ChevronDown,
  ChevronUp,
  Loader2,
  Sparkles
} from 'lucide-react';
import { postWithAuth } from './api';

const JOB_TYPES = [
  {
    id: 'REPORT',
    label: 'System Report',
    icon: BarChart3,
    badge: '✨ Real Analytics',
    badgeClass: 'badge-real',
    desc: 'Aggregates actual PostgreSQL DB metrics (counts, latencies, success rates)',
    defaultPayload: {
      reportTitle: 'System Health & Performance Audit',
      includeDetails: true,
    },
  },
  {
    id: 'PDF_GENERATION',
    label: 'PDF Invoice',
    icon: FileText,
    badge: 'Simulated ~3s',
    badgeClass: 'badge-simulated',
    desc: 'Simulates document rendering with 3-second processing delay',
    defaultPayload: {
      customerName: 'Acme Global Corp',
      invoiceId: `INV-${Math.floor(1000 + Math.random() * 9000)}`,
      amount: 499.5,
    },
  },
  {
    id: 'EMAIL',
    label: 'Email Dispatch',
    icon: Mail,
    badge: 'Simulated ~1.5s',
    badgeClass: 'badge-simulated',
    desc: 'Simulates transactional email dispatch via external SMTP gateway',
    defaultPayload: {
      recipientEmail: 'alex.smith@example.com',
      subject: 'Transaction Receipt & Order Confirmation',
      template: 'invoice_ready',
    },
  },
];

const PRIORITIES = [
  { label: 'Low', value: 0 },
  { label: 'Normal', value: 3 },
  { label: 'High', value: 7 },
  { label: 'Critical', value: 10 },
];

export default function CreateJob({ isOpen, onClose, onJobCreated, showToast }) {
  const [selectedType, setSelectedType] = useState('REPORT');
  const [priority, setPriority] = useState(3);
  const [maxRetries, setMaxRetries] = useState(3);

  // Dynamic payload fields
  const [reportTitle, setReportTitle] = useState('System Health & Performance Audit');
  const [customerName, setCustomerName] = useState('Acme Global Corp');
  const [invoiceId, setInvoiceId] = useState(`INV-${Math.floor(1000 + Math.random() * 9000)}`);
  const [amount, setAmount] = useState(499.5);
  const [recipientEmail, setRecipientEmail] = useState('alex.smith@example.com');
  const [subject, setSubject] = useState('Transaction Receipt & Order Confirmation');
  const [emailTemplate, setEmailTemplate] = useState('invoice_ready');

  // Chaos engineering
  const [chaosExpanded, setChaosExpanded] = useState(false);
  const [chaosMode, setChaosMode] = useState('none'); // 'none' | 'temp_failure' | 'dlq'

  // Loading states
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isBlasting, setIsBlasting] = useState(false);

  if (!isOpen) return null;

  const handleChaosToggle = (mode) => {
    if (chaosMode === mode) {
      setChaosMode('none');
      setMaxRetries(3);
    } else {
      setChaosMode(mode);
      if (mode === 'dlq') {
        setMaxRetries(1);
      } else if (mode === 'temp_failure') {
        setMaxRetries(3);
      }
    }
  };

  const buildPayload = (type = selectedType) => {
    let payload = {};
    if (type === 'REPORT') {
      payload = {
        reportTitle: reportTitle || 'System Health & Performance Audit',
        requestedBy: 'Console User',
        timestamp: new Date().toISOString(),
      };
    } else if (type === 'PDF_GENERATION') {
      payload = {
        customerName: customerName || 'Acme Global Corp',
        invoiceId: invoiceId || `INV-${Date.now()}`,
        amount: Number(amount) || 100,
        currency: 'USD',
      };
    } else if (type === 'EMAIL') {
      payload = {
        recipientEmail: recipientEmail || 'user@example.com',
        subject: subject || 'Notification',
        template: emailTemplate,
      };
    }

    if (chaosMode === 'temp_failure') {
      payload.fail_attempts = 2;
    } else if (chaosMode === 'dlq') {
      payload.fail_attempts = 5;
    }

    return payload;
  };

  const handleSubmit = async (e) => {
    if (e) e.preventDefault();
    setIsSubmitting(true);

    try {
      const payload = buildPayload(selectedType);
      const res = await postWithAuth('/jobs', {
        type: selectedType,
        payload,
        priority: Number(priority),
        maxRetries: Number(maxRetries),
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.message || `Failed with HTTP ${res.status}`);
      }

      const data = await res.json();
      showToast?.({
        type: 'success',
        title: 'Job Dispatched Successfully',
        message: `Job #${data.jobId.slice(0, 8)}... queued for worker execution.`,
        link: `/jobs/${data.jobId}`,
        linkText: 'Track Execution →',
      });

      if (onJobCreated) onJobCreated(data);
      onClose();
    } catch (err) {
      showToast?.({
        type: 'error',
        title: 'Failed to Dispatch Job',
        message: err.message || 'Network error occurred',
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleBatchBlast = async () => {
    setIsBlasting(true);
    const types = ['REPORT', 'PDF_GENERATION', 'EMAIL', 'REPORT', 'PDF_GENERATION'];
    const createdJobs = [];
    let successCount = 0;

    try {
      for (let i = 0; i < types.length; i++) {
        const type = types[i];
        const batchPayload = buildPayload(type);
        batchPayload.batchIndex = i + 1;
        batchPayload.batchId = `blast-${Date.now().toString(36)}`;

        const res = await postWithAuth('/jobs', {
          type,
          payload: batchPayload,
          priority: (i * 2) % 10,
          maxRetries: 3,
        });

        if (res.ok) {
          const data = await res.json();
          createdJobs.push(data);
          successCount++;
        }
      }

      showToast?.({
        type: 'success',
        title: '🚀 Batch Blast Complete!',
        message: `Dispatched ${successCount} concurrent jobs across Kafka partitions.`,
        link: '/jobs',
        linkText: 'View in Jobs Stream →',
      });

      if (onJobCreated && createdJobs.length > 0) {
        onJobCreated(createdJobs[0]);
      }
      onClose();
    } catch (err) {
      showToast?.({
        type: 'error',
        title: 'Batch Blast Failed',
        message: err.message,
      });
    } finally {
      setIsBlasting(false);
    }
  };

  return (
    <div className="panel-overlay" onClick={onClose}>
      <div className="side-panel" onClick={(e) => e.stopPropagation()}>
        <div className="panel-header">
          <div className="panel-header-title">
            <Zap className="panel-header-icon" size={22} />
            <div>
              <h3>Trigger New Job</h3>
              <p className="panel-subtitle">Orchestrate asynchronous tasks with Kafka & Redis</p>
            </div>
          </div>
          <button
            type="button"
            className="panel-close-btn"
            onClick={onClose}
            aria-label="Close panel"
          >
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="panel-body">
          {/* Job Type Selector */}
          <div className="form-section">
            <label className="section-label">Select Job Type</label>
            <div className="type-cards-grid">
              {JOB_TYPES.map((type) => {
                const Icon = type.icon;
                const isSelected = selectedType === type.id;
                return (
                  <div
                    key={type.id}
                    className={`type-card ${isSelected ? 'selected' : ''}`}
                    onClick={() => setSelectedType(type.id)}
                  >
                    <div className="type-card-top">
                      <div className="type-card-icon-wrap">
                        <Icon size={20} />
                      </div>
                      <span className={`type-badge ${type.badgeClass}`}>
                        {type.badge}
                      </span>
                    </div>
                    <div className="type-card-label">{type.label}</div>
                    <div className="type-card-desc">{type.desc}</div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Dynamic Payload Form */}
          <div className="form-section">
            <label className="section-label">Job Payload Configuration</label>
            <div className="payload-box">
              {selectedType === 'REPORT' && (
                <div className="form-field">
                  <label className="input-label">Report Title</label>
                  <input
                    type="text"
                    className="custom-input"
                    value={reportTitle}
                    onChange={(e) => setReportTitle(e.target.value)}
                    placeholder="e.g. System Health & Performance Audit"
                  />
                  <span className="field-hint">
                    Worker will query live PostgreSQL job counts, durations & compute rates
                  </span>
                </div>
              )}

              {selectedType === 'PDF_GENERATION' && (
                <>
                  <div className="form-field">
                    <label className="input-label">Customer / Company Name</label>
                    <input
                      type="text"
                      className="custom-input"
                      value={customerName}
                      onChange={(e) => setCustomerName(e.target.value)}
                      placeholder="e.g. Acme Global Corp"
                    />
                  </div>
                  <div className="form-row">
                    <div className="form-field" style={{ flex: 1 }}>
                      <label className="input-label">Invoice ID</label>
                      <input
                        type="text"
                        className="custom-input"
                        value={invoiceId}
                        onChange={(e) => setInvoiceId(e.target.value)}
                      />
                    </div>
                    <div className="form-field" style={{ width: '130px' }}>
                      <label className="input-label">Amount ($)</label>
                      <input
                        type="number"
                        step="0.01"
                        className="custom-input"
                        value={amount}
                        onChange={(e) => setAmount(e.target.value)}
                      />
                    </div>
                  </div>
                </>
              )}

              {selectedType === 'EMAIL' && (
                <>
                  <div className="form-field">
                    <label className="input-label">Recipient Email</label>
                    <input
                      type="email"
                      className="custom-input"
                      value={recipientEmail}
                      onChange={(e) => setRecipientEmail(e.target.value)}
                    />
                  </div>
                  <div className="form-field">
                    <label className="input-label">Subject</label>
                    <input
                      type="text"
                      className="custom-input"
                      value={subject}
                      onChange={(e) => setSubject(e.target.value)}
                    />
                  </div>
                  <div className="form-field">
                    <label className="input-label">Email Template</label>
                    <select
                      className="custom-select"
                      value={emailTemplate}
                      onChange={(e) => setEmailTemplate(e.target.value)}
                    >
                      <option value="invoice_ready">Invoice Notification (Standard)</option>
                      <option value="welcome">Welcome Onboarding</option>
                      <option value="security_alert">System Security Alert</option>
                    </select>
                  </div>
                </>
              )}
            </div>
          </div>

          {/* Job Configuration: Priority & Max Retries */}
          <div className="form-section">
            <div className="form-row" style={{ gap: '1.5rem', alignItems: 'flex-start' }}>
              <div style={{ flex: 1 }}>
                <label className="section-label">Execution Priority</label>
                <div className="segmented-control">
                  {PRIORITIES.map((p) => (
                    <button
                      key={p.value}
                      type="button"
                      className={`segment-btn ${priority === p.value ? 'active' : ''}`}
                      onClick={() => setPriority(p.value)}
                    >
                      {p.label}
                    </button>
                  ))}
                </div>
              </div>

              <div style={{ width: '140px' }}>
                <label className="section-label">Max Retries</label>
                <div className="stepper-input">
                  <button
                    type="button"
                    className="stepper-btn"
                    disabled={maxRetries <= 0 || chaosMode === 'dlq'}
                    onClick={() => setMaxRetries((r) => Math.max(0, r - 1))}
                  >
                    −
                  </button>
                  <span className="stepper-value">{maxRetries}</span>
                  <button
                    type="button"
                    className="stepper-btn"
                    disabled={maxRetries >= 10 || chaosMode === 'dlq'}
                    onClick={() => setMaxRetries((r) => Math.min(10, r + 1))}
                  >
                    +
                  </button>
                </div>
              </div>
            </div>
          </div>

          {/* 🧪 Chaos Engineering Section */}
          <div className="chaos-container">
            <button
              type="button"
              className="chaos-toggle-header"
              onClick={() => setChaosExpanded(!chaosExpanded)}
            >
              <div className="chaos-header-left">
                <Flame size={18} className="chaos-icon" />
                <span>🧪 Chaos Engineering & Resiliency Lab</span>
                {chaosMode !== 'none' && (
                  <span className="chaos-active-badge">ACTIVE</span>
                )}
              </div>
              {chaosExpanded ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
            </button>

            {chaosExpanded && (
              <div className="chaos-content">
                <p className="chaos-info">
                  Inject deterministic faults to watch JobFlow's distributed retry backoff and
                  Dead Letter Queue routing in real-time.
                </p>

                <div className="chaos-options">
                  <div
                    className={`chaos-card ${chaosMode === 'temp_failure' ? 'active' : ''}`}
                    onClick={() => handleChaosToggle('temp_failure')}
                  >
                    <div className="chaos-card-header">
                      <Bug size={16} />
                      <strong>Simulate Temporary Failure</strong>
                    </div>
                    <p>
                      Injects <code>fail_attempts: 2</code>. Worker crashes on first 2 attempts,
                      succeeds on attempt 3 via exponential backoff (1s → 2s).
                    </p>
                  </div>

                  <div
                    className={`chaos-card ${chaosMode === 'dlq' ? 'active' : ''}`}
                    onClick={() => handleChaosToggle('dlq')}
                  >
                    <div className="chaos-card-header">
                      <Flame size={16} />
                      <strong>Simulate Crash → DLQ Route</strong>
                    </div>
                    <p>
                      Sets <code>maxRetries: 1</code> and <code>fail_attempts: 5</code>.
                      Exhausts retries and routes poison pill into <code>job-dlq</code>.
                    </p>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Panel Footer / Action Buttons */}
          <div className="panel-footer">
            <button
              type="button"
              className="btn-blast"
              onClick={handleBatchBlast}
              disabled={isSubmitting || isBlasting}
              title="Spawn 5 concurrent jobs to watch parallel distribution"
            >
              {isBlasting ? (
                <>
                  <Loader2 size={16} className="btn-spinner" /> Blasting...
                </>
              ) : (
                <>
                  <Sparkles size={16} /> 🚀 Batch Blast (5)
                </>
              )}
            </button>

            <button
              type="submit"
              className="btn-submit-job"
              disabled={isSubmitting || isBlasting}
            >
              {isSubmitting ? (
                <>
                  <Loader2 size={16} className="btn-spinner" /> Dispatching...
                </>
              ) : (
                <>
                  <Zap size={16} /> Dispatch Job
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
