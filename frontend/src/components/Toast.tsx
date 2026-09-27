import { createContext, useCallback, useContext, useState } from 'react';

type Tone = 'ok' | 'err';

type ToastItem = {
  id: number;
  message: string;
  tone: Tone;
};

type Push = (message: string, tone?: Tone) => void;

const ToastContext = createContext<Push>(() => {});

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const push = useCallback<Push>((message, tone = 'ok') => {
    const id = Date.now() + Math.random();
    setToasts((current) => [...current, { id, message, tone }]);
    window.setTimeout(() => {
      setToasts((current) => current.filter((item) => item.id !== id));
    }, 3800);
  }, []);

  return (
    <ToastContext.Provider value={push}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 bottom-6 z-50 flex flex-col items-center gap-2 px-4">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            role="status"
            className={`rounded-full px-4 py-2 text-sm shadow-lg ${
              toast.tone === 'err'
                ? 'bg-red-600 text-white'
                : 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
            }`}
          >
            {toast.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): Push {
  return useContext(ToastContext);
}
