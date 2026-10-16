import { CheckCircle2, Clock, PlayCircle, AlertCircle, Circle } from 'lucide-react';

export default function Timeline({ status, createdAt, startedAt, completedAt, retryCount = 0 }) {
  const formatTime = (ts) => {
    if (!ts) return null;
    const d = new Date(ts);
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  };

  const isTerminal = status === 'SUCCESS' || status === 'FAILED';
  const isFailed = status === 'FAILED';

  // Determine stage states: 'completed' | 'current' | 'failed' | 'upcoming'
  const getStageState = (stageKey) => {
    if (stageKey === 'CREATED') {
      if (status === 'CREATED') return 'current';
      return 'completed';
    }

    if (stageKey === 'QUEUED') {
      if (status === 'CREATED') return 'upcoming';
      if (status === 'QUEUED') return 'current';
      return 'completed';
    }

    if (stageKey === 'PROCESSING') {
      if (status === 'CREATED' || status === 'QUEUED') return 'upcoming';
      if (status === 'PROCESSING') return 'current';
      if (status === 'ERROR') return 'warning';
      return 'completed';
    }

    if (stageKey === 'TERMINAL') {
      if (status === 'SUCCESS') return 'completed';
      if (status === 'FAILED') return 'failed';
      return 'upcoming';
    }

    return 'upcoming';
  };

  const stages = [
    {
      key: 'CREATED',
      label: 'Submitted',
      sublabel: 'Persisted to Outbox',
      time: formatTime(createdAt),
    },
    {
      key: 'QUEUED',
      label: 'Queued',
      sublabel: 'Published to Kafka',
      time: startedAt ? formatTime(createdAt) : null,
    },
    {
      key: 'PROCESSING',
      label: status === 'ERROR' ? `Retrying (${retryCount})` : 'Processing',
      sublabel: status === 'ERROR' ? 'Exponential Backoff' : 'Claimed by Worker',
      time: formatTime(startedAt),
    },
    {
      key: 'TERMINAL',
      label: isFailed ? 'Failed (DLQ)' : 'Completed',
      sublabel: isFailed ? 'Retries Exhausted' : 'Result Persisted',
      time: formatTime(completedAt),
    },
  ];

  return (
    <div className="lifecycle-timeline">
      <div className="timeline-track">
        {stages.map((stage, idx) => {
          const state = getStageState(stage.key);

          let Icon = Circle;
          if (state === 'completed') Icon = CheckCircle2;
          else if (state === 'current') Icon = PlayCircle;
          else if (state === 'warning') Icon = AlertCircle;
          else if (state === 'failed') Icon = AlertCircle;

          return (
            <div key={stage.key} className={`timeline-step-wrapper ${state}`}>
              <div className="timeline-node">
                <div className={`timeline-icon-box ${state}`}>
                  <Icon size={18} />
                </div>
                {idx < stages.length - 1 && (
                  <div
                    className={`timeline-connector ${
                      state === 'completed' ? 'active' : ''
                    }`}
                  />
                )}
              </div>

              <div className="timeline-meta">
                <span className="timeline-step-title">{stage.label}</span>
                <span className="timeline-step-desc">{stage.sublabel}</span>
                {stage.time && (
                  <span className="timeline-step-time">{stage.time}</span>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
