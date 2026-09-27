export function formatBytes(size: number): string {
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(size < 10 * 1024 ? 1 : 0)} KB`;
  }
  const mb = size / (1024 * 1024);
  return `${mb.toFixed(1)} MB`;
}

export function fileKind(contentType?: string, filename?: string): string {
  const type = contentType?.toLowerCase() ?? '';
  const name = filename?.toLowerCase() ?? '';
  if (type === 'application/pdf' || name.endsWith('.pdf')) {
    return 'PDF';
  }
  if (type.startsWith('image/')) {
    return 'Image';
  }
  if (type.startsWith('text/') || type === 'application/json') {
    return 'Text';
  }
  if (type.includes('zip') || name.endsWith('.zip')) {
    return 'Archive';
  }
  const subtype = type.split('/')[1];
  return subtype ? subtype.toUpperCase() : 'File';
}

export function formatRemaining(expiresAt: string, now: number): string {
  const diff = new Date(expiresAt).getTime() - now;
  if (Number.isNaN(diff)) {
    return '';
  }
  if (diff <= 0) {
    return 'Expired';
  }
  const minutesTotal = Math.max(1, Math.ceil(diff / 60_000));
  if (minutesTotal < 60) {
    return minutesTotal === 1 ? '1 minute' : `${minutesTotal} minutes`;
  }
  const hours = Math.floor(minutesTotal / 60);
  const minutes = minutesTotal % 60;
  if (hours < 48) {
    if (minutes === 0) {
      return hours === 1 ? '1 hour' : `${hours} hours`;
    }
    return `${hours}h ${minutes}m`;
  }
  const days = Math.max(1, Math.round(minutesTotal / 1440));
  return days === 1 ? '1 day' : `${days} days`;
}

export function formatTimestamp(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}
