import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAdminAuth } from '../../auth/AdminAuthContext';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

const PLACEHOLDER_TOKEN =
  import.meta.env.VITE_PLACEHOLDER_ADMIN_TOKEN ?? 'local-dev-admin-token-not-for-production';

export default function LoginPage() {
  const { login } = useAdminAuth();
  const navigate = useNavigate();
  const [token, setToken] = useState('');
  const [error, setError] = useState('');

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!token.trim()) {
      setError('Admin token is required.');
      return;
    }
    if (token.trim() !== PLACEHOLDER_TOKEN) {
      setError('Invalid admin token.');
      return;
    }
    login(token.trim());
    navigate('/kyc-queue', { replace: true });
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="text-2xl">Stash Admin</CardTitle>
          <CardDescription>
            Placeholder auth — for local dev and staging only. Real admin auth ships in v0.5.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
            <div className="flex flex-col gap-2">
              <Label htmlFor="token">Admin Token</Label>
              <Input
                id="token"
                type="password"
                placeholder="Paste your admin token"
                value={token}
                onChange={(e) => {
                  setToken(e.target.value);
                  setError('');
                }}
              />
              {error ? <p className="text-sm text-red-600">{error}</p> : null}
            </div>
            <Button type="submit" className="w-full">
              Sign In
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
