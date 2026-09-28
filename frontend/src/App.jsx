import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { ToastProvider } from './components/Toast.jsx';
import { AppLayout } from './layouts/AppLayout.jsx';
import { CreateSharePage } from './pages/CreateSharePage.jsx';
import { ShareCreatedPage } from './pages/ShareCreatedPage.jsx';
import { ViewSharePage } from './pages/ViewSharePage.jsx';

export function App() {
  return (
    <ToastProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/" element={<CreateSharePage />} />
            <Route path="/created/:slug" element={<ShareCreatedPage />} />
            <Route path="/:slug" element={<ViewSharePage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </ToastProvider>
  );
}
