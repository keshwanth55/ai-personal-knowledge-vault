const API_URL = (import.meta.env.VITE_API_URL || (import.meta.env.DEV ? 'http://localhost:8081/api' : '')).replace(/\/$/, '');

async function request(path, options = {}) {
  if (!API_URL) {
    throw new Error('Set VITE_API_URL to your deployed backend URL before building the frontend.');
  }
  const token = localStorage.getItem('vault-token');
  const headers = new Headers(options.headers || {});
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (options.body && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(`${API_URL}${path}`, { ...options, headers });
  if (!response.ok) {
    const payload = await response.json().catch(() => ({}));
    throw new Error(payload.message || payload.error || `Request failed (${response.status})`);
  }
  if (response.status === 204) return null;
  return response.json();
}

export const api = {
  auth: (path, email, password) =>
    request(`/auth/${path}`, { method: 'POST', body: JSON.stringify({ email, password }) }),
  documents: () => request('/documents'),
  upload: (file) => {
    const body = new FormData();
    body.append('file', file);
    return request('/documents', { method: 'POST', body });
  },
  removeDocument: (id) => request(`/documents/${id}`, { method: 'DELETE' }),
  ask: (question) =>
    request('/chat', { method: 'POST', body: JSON.stringify({ question }) }),
  search: (query) => request(`/search?q=${encodeURIComponent(query)}`),
};
