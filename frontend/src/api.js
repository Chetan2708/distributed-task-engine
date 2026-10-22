
const BASE_URL = import.meta.env.VITE_API_URL || '/api/v1';

export const fetchWithAuth = async (endpoint, options = {}) => {
  const token = localStorage.getItem('token');

  const headers = {
    'Content-Type': 'application/json',
    ...options.headers,
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${BASE_URL}${endpoint}`, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    localStorage.removeItem('token');
    window.location.reload();
  }

  return response;
};

export const postWithAuth = async (endpoint, body) => {
  return fetchWithAuth(endpoint, {
    method: 'POST',
    body: JSON.stringify(body),
  });
};

export const getUserRole = () => {
  const token = localStorage.getItem('token');
  if (!token) return null;
  try {
    const base64Url = token.split('.')[1];
    if (!base64Url) return null;
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    const parsed = JSON.parse(jsonPayload);
    return parsed.role || null;
  } catch (e) {
    return null;
  }
};

export const isCurrentUserAdmin = () => {
  const role = getUserRole();
  return Boolean(role && role.includes('ROLE_ADMIN'));
};

export const subscribeToJobStream = (onEvent, onError, onConnected) => {
  const token = localStorage.getItem('token');
  if (!token) return () => {};

  const url = `${BASE_URL}/jobs/stream?token=${encodeURIComponent(token)}`;
  const eventSource = new EventSource(url);

  eventSource.addEventListener('connected', (e) => {
    try {
      const data = JSON.parse(e.data);
      if (onConnected) onConnected(data);
    } catch (err) {
      // ignore
    }
  });

  eventSource.addEventListener('job_event', (e) => {
    try {
      const data = JSON.parse(e.data);
      if (onEvent) onEvent(data);
    } catch (err) {
      console.error('Failed to parse SSE job event:', err);
    }
  });

  eventSource.onerror = (err) => {
    if (onError) onError(err);
  };

  return () => {
    eventSource.close();
  };
};
