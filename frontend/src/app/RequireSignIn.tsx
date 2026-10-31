import { Navigate, Outlet } from 'react-router';
import { ErrorNotice } from '../components/ErrorNotice';
import { messages } from '../messages';
import { isSignedOut, useMe } from './useMe';

/** Guards every screen except Login: signed-out users go to /login. */
export function RequireSignIn() {
  const me = useMe();

  if (me.isPending) {
    return (
      <p role="status" className="p-space-4 text-ink-muted">
        {messages.loading}
      </p>
    );
  }
  if (me.isError) {
    if (isSignedOut(me.error)) {
      return <Navigate to="/login" replace />;
    }
    return (
      <main className="mx-auto max-w-md p-space-4">
        <ErrorNotice error={me.error} />
      </main>
    );
  }
  return <Outlet />;
}
