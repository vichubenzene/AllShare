export type ShareType = 'TEXT' | 'FILE';

export type ShareCreated = {
  token: string;
  shareUrl: string;
  managementToken: string;
  expiresAt: string;
  passwordProtected: boolean;
};

export type ShareView = {
  passwordRequired?: boolean;
  type?: ShareType;
  content?: string;
  filename?: string;
  contentType?: string;
  fileSize?: number;
  expiresAt?: string;
  passwordProtected?: boolean;
};

export type ShareAccess = {
  accessToken: string;
  accessExpiresAt: string;
  share: ShareView;
};

export const EXPIRATIONS = [
  { minutes: 15, label: '15 minutes' },
  { minutes: 60, label: '1 hour' },
  { minutes: 360, label: '6 hours' },
  { minutes: 1440, label: '24 hours' },
  { minutes: 4320, label: '3 days' },
  { minutes: 10080, label: '7 days' },
] as const;

export const MAX_FILE_BYTES = 50 * 1024 * 1024;
