import type { ShareCreated } from '../types/share';

function createdKey(token: string): string {
  return `allshare:created:${token}`;
}

function accessKey(token: string): string {
  return `allshare:access:${token}`;
}

export function saveCreated(created: ShareCreated): void {
  sessionStorage.setItem(createdKey(created.token), JSON.stringify(created));
}

export function loadCreated(token: string): ShareCreated | null {
  const raw = sessionStorage.getItem(createdKey(token));
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as ShareCreated;
  } catch {
    return null;
  }
}

export function clearCreated(token: string): void {
  sessionStorage.removeItem(createdKey(token));
}

export function saveAccess(token: string, accessToken: string): void {
  sessionStorage.setItem(accessKey(token), accessToken);
}

export function loadAccess(token: string): string | null {
  return sessionStorage.getItem(accessKey(token));
}

export function clearAccess(token: string): void {
  sessionStorage.removeItem(accessKey(token));
}
