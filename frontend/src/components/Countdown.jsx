import { useCountdown } from '../hooks/useCountdown.js';

export function Countdown({ expiresAt, prefix = 'Expires in: ' }) {
  const label = useCountdown(expiresAt);
  if (!label) {
    return null;
  }
  return (
    <p className="muted">
      {label === 'Expired' ? 'Expired' : `${prefix}${label}`}
    </p>
  );
}
