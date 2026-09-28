import { useEffect, useState } from 'react';
import { formatRemaining } from '../utils/format.js';

export function useCountdown(expiresAt) {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!expiresAt) {
      return undefined;
    }
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, [expiresAt]);

  return expiresAt ? formatRemaining(expiresAt, now) : '';
}
