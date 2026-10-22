import { useState } from 'react';
import {
  X,
  Zap,
  Flame,
  Bug,
  ChevronDown,
  ChevronUp,
  Loader2,
  Sparkles,
  Code2
} from 'lucide-react';
import { postWithAuth } from './api';

const PRIORITIES = [
  { label: 'Low', value: 0 },
  { label: 'Normal', value: 3 },
  { label: 'High', value: 7 },
  { label: 'Critical', value: 10 },
];

const DEFAULT_PAYLOAD = {
  message: 'Hello from JobFlow Task Engine',
  timestamp: new Date().toISOString()
};

export default function CreateJob({ isOpen, onClose, onJobCreated, showToast }) {
  const [jobType, setJobType] = useState('TEST');
  const [priority, setPriority] = useState(3);
  const [maxRetries, setMaxRetries] = useState(3);
  const [callbackUrl, setCallbackUrl] = useState('');
  const [payloadText, setPayloadText] = useState(JSON.stringify(DEFAULT_PAYLOAD, null, 2));
  const [jsonError, setJsonError] = useState(null);

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

  const parsePayload = () => {
    try {
      const parsed = JSON.parse(payloadText || '{}');
      if (chaosMode === 'temp_failure') {
        parsed.fail_attempts = 2;
      } else if (chaosMode === 'dlq') {
        parsed.fail_attempts = 5;
      }
      return parsed;
    } catch (err) {
      throw new Error(`Invalid JSON payload: ${err.message}`);
    }
  };

  const handlePayloadChange = (e) => {
    const val = e.target.value;
    setPayloadText(val);
    try {
      JSON.parse(val);
      setJsonError(null);
    } catch (err) {
      setJsonError(err.message);
    }
  };

  const handleSubmit = async (e) => {
    if (e) e.preventDefault();
    setIsSubmitting(true);

    try {
      if (!jobType.trim()) {
        throw new Error('Job type is required');
      }
      const payload = parsePayload();

      const res = await postWithAuth('/jobs', {
        type: jobType.trim().toUpperCase(),
        payload,
        priority: Number(priority),
        maxRetries: Number(maxRetries),
        callbackUrl: callbackUrl.trim() || undefined,
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.message || `Failed with HTTP ${res.status}`);
      }

      const data = await res.json();
      showToast?.({
        type: 'success',
        title: 'Job Dispatched Successfully',
        message: `Job #${data.jobId.slice(0, 8)}... (${jobType.toUpperCase()}) queued for execution.`,
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
    const createdJobs = [];
    let successCount = 0;
    const typeToUse = (jobType || 'TEST').trim().toUpperCase();

    try {
      const basePayload = parsePayload();

      for (let i = 0; i < 5; i++) {
        const batchPayload = {
          ...basePayload,
          batchIndex: i + 1,
          batchId: `blast-${Date.now().toString(36)}`,
        };

        const res = await postWithAuth('/jobs', {
          type: typeToUse,
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
        message: `Dispatched ${successCount} concurrent '${typeToUse}' jobs across Kafka partitions.`,
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
              <h3>Dispatch Background Job</h3>
              <span className="panel-subtitle">
                Queue durable jobs to Kafka via Transactional Outbox
              </span>
            </div>
          </div>
          <button
            type="button"
            className="btn-panel-close"
            onClick={onClose}
            aria-label="Close panel"
          >
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="panel-body">
          {/* Job Type Input */}
          <div className="form-section">
            <label className="section-label">Job Type</label>
            <input
              type="text"
              className="custom-input"
              value={jobType}
              onChange={(e) => setJobType(e.target.value)}
              placeholder="e.g. TEST, DATA_SYNC, WEBHOOK, EXPORT"
              required
            />
            <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.5rem', flexWrap: 'wrap' }}>
              <span className="field-hint">Quick types:</span>
              {[
                { type: 'HTTP_WEBHOOK', label: 'HTTP_WEBHOOK (Generic)', sample: {
                  url: 'https://httpbin.org/post',
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: { event: 'order.completed', orderId: 'ord_99812', amount: 49.99 },
                  rateLimitPerSecond: 10,
                  timeoutSeconds: 15
                }},
                { type: 'TEST', label: 'TEST', sample: DEFAULT_PAYLOAD },
                { type: 'DATA_SYNC', label: 'DATA_SYNC', sample: { source: 'crm', target: 'data-warehouse', syncMode: 'incremental' } },
                { type: 'EXPORT', label: 'EXPORT', sample: { format: 'parquet', dateRange: 'last_30_days' } }
              ].map((preset) => (
                <button
                  key={preset.type}
                  type="button"
                  style={{
                    fontSize: '0.75rem',
                    padding: '2px 8px',
                    borderRadius: '4px',
                    border: '1px solid var(--border-color, #30363d)',
                    background: jobType === preset.type ? 'var(--primary-color, #238636)' : 'transparent',
                    color: jobType === preset.type ? '#fff' : 'inherit',
                    cursor: 'pointer'
                  }}
                  onClick={() => {
                    setJobType(preset.type);
                    setPayloadText(JSON.stringify(preset.sample, null, 2));
                    setJsonError(null);
                  }}
                >
                  {preset.label}
                </button>
              ))}
            </div>
          </div>

          {/* Generic JSON Payload Form */}
          <div className="form-section">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
              <label className="section-label" style={{ margin: 0 }}>
                <Code2 size={16} style={{ display: 'inline', marginRight: '6px', verticalAlign: 'text-bottom' }} />
                Job Payload (JSON)
              </label>
              <button
                type="button"
                style={{
                  fontSize: '0.75rem',
                  padding: '2px 8px',
                  borderRadius: '4px',
                  border: '1px solid var(--border-color, #30363d)',
                  background: 'transparent',
                  color: 'inherit',
                  cursor: 'pointer'
                }}
                onClick={() => {
                  try {
                    const formatted = JSON.stringify(JSON.parse(payloadText), null, 2);
                    setPayloadText(formatted);
                    setJsonError(null);
                  } catch (e) {
                    // Ignore format error
                  }
                }}
              >
                Format JSON
              </button>
            </div>
            <textarea
              className="custom-input mono-font"
              style={{
                width: '100%',
                minHeight: '130px',
                fontFamily: 'monospace',
                fontSize: '0.85rem',
                lineHeight: '1.4',
                resize: 'vertical',
                borderColor: jsonError ? '#f85149' : undefined
              }}
              value={payloadText}
              onChange={handlePayloadChange}
              placeholder="{}"
            />
            {jsonError ? (
              <span style={{ color: '#f85149', fontSize: '0.75rem', display: 'block', marginTop: '4px' }}>
                ⚠️ {jsonError}
              </span>
            ) : (
              <span className="field-hint">
                Arbitrary JSON object forwarded to the worker event bus.
              </span>
            )}
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

          {/* Optional Completion Callback Webhook URL */}
          <div className="form-section">
            <label className="section-label">Completion Callback Webhook (Optional)</label>
            <input
              type="url"
              className="custom-input"
              value={callbackUrl}
              onChange={(e) => setCallbackUrl(e.target.value)}
              placeholder="e.g. https://api.myrepo.com/webhooks/job-done"
            />
            <span className="field-hint">
              JobFlow automatically POSTs job results & status to this URL upon SUCCESS or DLQ failure.
            </span>
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
                      Injects <code>fail_attempts: 2</code>. Worker retries using exponential backoff (1s → 2s) before succeeding.
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
              disabled={isSubmitting || isBlasting || Boolean(jsonError)}
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
              disabled={isSubmitting || isBlasting || Boolean(jsonError)}
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
