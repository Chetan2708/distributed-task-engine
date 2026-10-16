import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, AlertTriangle, XCircle, Info, X } from 'lucide-react';

export default function Toast({ toasts, onDismiss }) {
  useEffect(() => {
    if (toasts.length === 0) return;
    const timers = toasts.map(t => {
      return setTimeout(() => {
        onDismiss(t.id);
      }, t.duration || 4500);
    });
    return () => timers.forEach(clearTimeout);
  }, [toasts, onDismiss]);

  if (!toasts || toasts.length === 0) return null;

  return (
    <div className="toast-container" aria-live="polite">
      {toasts.map(toast => {
        let Icon = CheckCircle2;
        let iconColor = '#3fb950';
        let borderColor = 'rgba(35, 134, 54, 0.4)';

        if (toast.type === 'error') {
          Icon = XCircle;
          iconColor = '#f85149';
          borderColor = 'rgba(248, 81, 73, 0.4)';
        } else if (toast.type === 'warning') {
          Icon = AlertTriangle;
          iconColor = '#d29922';
          borderColor = 'rgba(210, 153, 34, 0.4)';
        } else if (toast.type === 'info') {
          Icon = Info;
          iconColor = '#58a6ff';
          borderColor = 'rgba(47, 129, 247, 0.4)';
        }

        return (
          <div
            key={toast.id}
            className="toast-item"
            style={{ borderLeft: `4px solid ${iconColor}`, borderColor }}
          >
            <div className="toast-icon">
              <Icon size={20} color={iconColor} />
            </div>
            <div className="toast-content">
              <div className="toast-title">{toast.title}</div>
              {toast.message && <div className="toast-message">{toast.message}</div>}
              {toast.link && (
                <Link
                  to={toast.link}
                  className="toast-link"
                  onClick={() => onDismiss(toast.id)}
                >
                  {toast.linkText || 'View Details →'}
                </Link>
              )}
            </div>
            <button
              type="button"
              className="toast-close-btn"
              onClick={() => onDismiss(toast.id)}
              aria-label="Close notification"
            >
              <X size={16} />
            </button>
          </div>
        );
      })}
    </div>
  );
}
