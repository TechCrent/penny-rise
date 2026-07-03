import { StrictMode, type ReactNode } from 'react';
import { createBrowserRouter, RouterProvider, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AdminAuthProvider, useAdminAuth } from './auth/AdminAuthContext';
import LoginPage from './routes/login';
import KycQueuePage from './routes/kyc-queue/KycQueuePage';
import UserSearchPage from './routes/users/UserSearchPage';
import UserDetailPage from './routes/users/UserDetailPage';
import DisputeQueuePage from './routes/disputes/DisputeQueuePage';
import DisputeDetailPage from './routes/disputes/DisputeDetailPage';
import AuditLogPage from './routes/audit-log/AuditLogPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 30_000 },
  },
});

function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAdminAuth();
  if (isLoading) return null;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/kyc-queue" replace /> },
  { path: '/login', element: <LoginPage /> },
  {
    path: '/kyc-queue',
    element: (
      <ProtectedRoute>
        <KycQueuePage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/users',
    element: (
      <ProtectedRoute>
        <UserSearchPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/users/:userId',
    element: (
      <ProtectedRoute>
        <UserDetailPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/disputes',
    element: (
      <ProtectedRoute>
        <DisputeQueuePage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/disputes/:disputeId',
    element: (
      <ProtectedRoute>
        <DisputeDetailPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/audit-log',
    element: (
      <ProtectedRoute>
        <AuditLogPage />
      </ProtectedRoute>
    ),
  },
]);

export default function App() {
  return (
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <AdminAuthProvider>
          <RouterProvider router={router} />
        </AdminAuthProvider>
      </QueryClientProvider>
    </StrictMode>
  );
}
