import { useCountdown } from '../hooks/useCountdown';

export function Countdown({ expiresAt }: { expiresAt?: string }) {
  const label = useCountdown(expiresAt);
  if (!label) {
    return null;
  }
  return <p className="text-sm text-zinc-500 dark:text-zinc-400">Expires in: {label}</p>;
}
