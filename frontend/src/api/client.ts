import type { ShareAccess, ShareCreated, ShareView } from '../types/share';
import { loadAccess } from '../utils/session';

export const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';

export class ApiError extends Error {
  status: number;
  code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

async function readError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as { code?: string; message?: string };
    return new ApiError(response.status, body.code ?? 'UNKNOWN', body.message ?? 'Something went wrong.');
  } catch {
    return new ApiError(response.status, 'UNKNOWN', 'Something went wrong.');
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, { ...init, headers });
  } catch {
    throw new ApiError(0, 'NETWORK', 'Could not reach the server.');
  }
  if (!response.ok) {
    throw await readError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export function createTextShare(input: {
  content: string;
  expirationMinutes: number;
  password?: string;
}): Promise<ShareCreated> {
  return request<ShareCreated>('/api/shares', {
    method: 'POST',
    body: JSON.stringify({
      type: 'TEXT',
      content: input.content,
      expirationMinutes: input.expirationMinutes,
      password: input.password ? input.password : undefined,
    }),
  });
}

export function uploadFileShare(
  file: File,
  expirationMinutes: number,
  password: string,
  onProgress: (percent: number) => void,
): Promise<ShareCreated> {
  return new Promise((resolve, reject) => {
    const form = new FormData();
    form.append('file', file);
    form.append('expirationMinutes', String(expirationMinutes));
    if (password) {
      form.append('password', password);
    }
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${API_BASE}/api/shares/file`);
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    };
    xhr.onerror = () => reject(new ApiError(0, 'NETWORK', 'Could not reach the server.'));
    xhr.onload = () => {
      const status = xhr.status;
      let body: { code?: string; message?: string } & Partial<ShareCreated> = {};
      try {
        body = JSON.parse(xhr.responseText) as typeof body;
      } catch {
        body = {};
      }
      if (status < 200 || status >= 300) {
        reject(new ApiError(status, body.code ?? 'UNKNOWN', body.message ?? 'Something went wrong.'));
        return;
      }
      resolve(body as ShareCreated);
    };
    xhr.send(form);
  });
}

export function fetchShare(token: string): Promise<ShareView> {
  const headers = new Headers();
  const access = loadAccess(token);
  if (access) {
    headers.set('X-Share-Access', access);
  }
  return request<ShareView>(`/api/shares/${encodeURIComponent(token)}`, { headers });
}

export function verifyPassword(token: string, password: string): Promise<ShareAccess> {
  return request<ShareAccess>(`/api/shares/${encodeURIComponent(token)}/verify`, {
    method: 'POST',
    body: JSON.stringify({ password }),
  });
}

export function revokeShare(token: string, managementToken: string): Promise<void> {
  return request<void>(`/api/shares/${encodeURIComponent(token)}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${managementToken}` },
  });
}

export async function downloadShare(token: string, fallbackName: string): Promise<void> {
  const headers = new Headers();
  const access = loadAccess(token);
  if (access) {
    headers.set('X-Share-Access', access);
  }
  let response: Response;
  try {
    response = await fetch(`${API_BASE}/api/shares/${encodeURIComponent(token)}/download`, { headers });
  } catch {
    throw new ApiError(0, 'NETWORK', 'Could not reach the server.');
  }
  if (!response.ok) {
    throw await readError(response);
  }
  const blob = await response.blob();
  const filename = filenameFromDisposition(response.headers.get('Content-Disposition')) ?? fallbackName;
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function filenameFromDisposition(header: string | null): string | null {
  if (!header) {
    return null;
  }
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (encoded?.[1]) {
    try {
      return decodeURIComponent(encoded[1]);
    } catch {
      return encoded[1];
    }
  }
  const plain = /filename="([^"]+)"/i.exec(header);
  return plain?.[1] ?? null;
}

export function absoluteShareUrl(token: string): string {
  return `${window.location.origin}/s/${token}`;
}
