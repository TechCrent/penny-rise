import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import LoginPage from '@/routes/login';

const router = createBrowserRouter([
  {
    path: '/',
    element: <LoginPage />,
  },
  {
    path: '/login',
    element: <LoginPage />,
  },
]);

export default function App() {
  return <RouterProvider router={router} />;
}
