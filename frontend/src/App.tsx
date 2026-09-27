import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { ToastProvider } from './components/Toast';
import { AppLayout } from './layouts/AppLayout';
import { CreateSharePage } from './pages/CreateSharePage';
import { PasswordPage } from './pages/PasswordPage';
import { ShareCreatedPage } from './pages/ShareCreatedPage';
import { ViewSharePage } from './pages/ViewSharePage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: false,
    },
  },
});

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <BrowserRouter>
          <Routes>
            <Route element={<AppLayout />}>
              <Route path="/" element={<CreateSharePage />} />
              <Route path="/share-created/:token" element={<ShareCreatedPage />} />
              <Route path="/s/:token" element={<ViewSharePage />} />
              <Route path="/s/:token/password" element={<PasswordPage />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </ToastProvider>
    </QueryClientProvider>
  );
}
