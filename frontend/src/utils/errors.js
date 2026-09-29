const PAGES = {
  SHARE_NOT_FOUND: { title: 'Share not found', body: 'Check the link, or the share may have been removed.' },
  NOT_FOUND: { title: 'Share not found', body: 'Check the link, or the share may have been removed.' },
  SHARE_EXPIRED: { title: 'This share has expired.', body: 'The content is no longer available.' },
  SHARE_REVOKED: { title: 'This share has been revoked.', body: 'The creator removed it.' },
  RATE_LIMITED: { title: 'Too many requests', body: 'Wait a minute and try again.' },
  NETWORK: { title: 'Server unavailable', body: 'Could not reach the server. Is the backend running?' },
};

export function errorPage(error) {
  return PAGES[error?.code] ?? { title: 'Server error', body: 'Something went wrong. Try again later.' };
}

export function errorMessage(error) {
  if (!error) {
    return 'Something went wrong.';
  }
  if (error.code === 'RATE_LIMITED') {
    return 'Too many requests. Wait a minute and try again.';
  }
  if (error.code === 'INTERNAL_ERROR' || error.code === 'UNKNOWN') {
    return 'Server error. Try again later.';
  }
  return error.message;
}
