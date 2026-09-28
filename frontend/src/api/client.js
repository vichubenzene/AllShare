import { loadAccess, nameFromSlug } from '../utils/session.js';

export class ApiError extends Error {
  constructor(status, code, message, shareUrl) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.shareUrl = shareUrl;
  }
}

const networkError = () => new ApiError(0, 'NETWORK', 'Could not reach the server.');

function toApiError(status, body) {
  return new ApiError(
    status,
    body?.code ?? 'UNKNOWN',
    body?.message ?? 'Something went wrong.',
    body?.shareUrl,
  );
}

async function readError(response) {
  try {
    return toApiError(response.status, await response.json());
  } catch {
    return toApiError(response.status, null);
  }
}

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(path, options);
  } catch {
    throw networkError();
  }
  if (!response.ok) {
    throw await readError(response);
  }
  return response.status === 204 ? undefined : response.json();
}

function accessHeaders(slug) {
  const access = loadAccess(nameFromSlug(slug));
  return access ? { 'X-Share-Access': access } : {};
}

const sharePath = (slug) => `/api/shares/${encodeURIComponent(slug)}`;

export function createTextShare({ name, content, expirationMinutes, password }) {
  return request('/api/shares', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      name,
      type: 'TEXT',
      content,
      expirationMinutes,
      password: password || undefined,
    }),
  });
}

/** Uses XMLHttpRequest because fetch cannot report upload progress. */
export function createFileShare({ name, file, expirationMinutes, password }, onProgress) {
  return new Promise((resolve, reject) => {
    const form = new FormData();
    form.append('name', name);
    form.append('file', file);
    form.append('expirationMinutes', String(expirationMinutes));
    if (password) {
      form.append('password', password);
    }
    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/api/shares/file');
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    };
    xhr.onerror = () => reject(networkError());
    xhr.onload = () => {
      let body = null;
      try {
        body = JSON.parse(xhr.responseText);
      } catch {
        body = null;
      }
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(body);
      } else {
        reject(toApiError(xhr.status, body));
      }
    };
    xhr.send(form);
  });
}

export function fetchShare(slug) {
  return request(sharePath(slug), { headers: accessHeaders(slug) });
}

export function verifyPassword(slug, password) {
  return request(`${sharePath(slug)}/verify`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password }),
  });
}

export function revokeShare(slug, managementToken) {
  return request(sharePath(slug), {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${managementToken}` },
  });
}

export async function downloadShare(slug, filename) {
  let response;
  try {
    response = await fetch(`${sharePath(slug)}/download`, { headers: accessHeaders(slug) });
  } catch {
    throw networkError();
  }
  if (!response.ok) {
    throw await readError(response);
  }
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export function absoluteUrl(shareUrl) {
  return `${window.location.origin}${shareUrl}`;
}
