export function formatBytes(size) {
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

export function fileKind(extension, contentType) {
  if (extension) {
    return extension.toUpperCase();
  }
  const subtype = contentType?.split('/')[1];
  return subtype ? subtype.toUpperCase() : 'FILE';
}

export function formatRemaining(expiresAt, now) {
  const diff = new Date(expiresAt).getTime() - now;
  if (Number.isNaN(diff)) {
    return '';
  }
  if (diff <= 0) {
    return 'Expired';
  }
  const minutes = Math.ceil(diff / 60_000);
  if (minutes < 60) {
    return minutes === 1 ? '1 minute' : `${minutes} minutes`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 48) {
    const rest = minutes % 60;
    if (rest !== 0) {
      return `${hours}h ${rest}m`;
    }
    return hours === 1 ? '1 hour' : `${hours} hours`;
  }
  const days = Math.round(minutes / 1440);
  return days === 1 ? '1 day' : `${days} days`;
}

export function formatTimestamp(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}
