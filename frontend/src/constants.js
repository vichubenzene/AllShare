export const EXPIRATIONS = [
  { minutes: 15, label: '15 minutes' },
  { minutes: 60, label: '1 hour' },
  { minutes: 360, label: '6 hours' },
  { minutes: 1440, label: '24 hours' },
  { minutes: 4320, label: '3 days' },
  { minutes: 10080, label: '7 days' },
];

export const MAX_FILE_BYTES = 50 * 1024 * 1024;

export const NAME_PATTERN = /^[a-z0-9][a-z0-9_-]{0,62}$/;
