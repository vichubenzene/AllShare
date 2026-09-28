import { useToast } from './Toast.jsx';

export function CopyButton({ value, label = 'Copy', className = 'btn btn-secondary' }) {
  const toast = useToast();

  function copy() {
    navigator.clipboard.writeText(value).then(
      () => toast('Copied'),
      () => toast('Could not copy', 'err'),
    );
  }

  return (
    <button type="button" className={className} onClick={copy}>
      {label}
    </button>
  );
}
