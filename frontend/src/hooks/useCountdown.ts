import { useEffect, useState } from 'react';
import { formatRemaining } from '../utils/format';

export function useCountdown(expiresAt?: string): string {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!expiresAt) {
      return;
    }
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, [expiresAt]);

  if (!expiresAt) {
    return '';
  }
  return formatRemaining(expiresAt, now);
}
