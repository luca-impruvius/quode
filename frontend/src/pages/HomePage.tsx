import { useQuery } from '@tanstack/react-query';
import duckUrl from '../../../design-system/logos/Duck.svg';
import { apiGet } from '../api/client';
import { messages } from '../messages';

interface Health {
  status: string;
}

// Placeholder start screen until M0-07 builds the login screen and app shell.
export function HomePage() {
  const health = useQuery({
    queryKey: ['health'],
    queryFn: () => apiGet<Health>('/health'),
    retry: false,
  });

  const serverText = health.isPending
    ? messages.home.serverChecking
    : health.data?.status === 'UP'
      ? messages.home.serverUp
      : messages.home.serverDown;

  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col items-center justify-center gap-space-5 px-space-4 text-center">
      <img src={duckUrl} alt={messages.home.duckAlt} className="h-40 w-auto" />
      <h1 className="font-display text-display">{messages.app.name}</h1>
      <p className="text-ink-muted">{messages.app.tagline}</p>
      <p
        className="rounded-pill bg-surface-sunken px-space-3 py-space-1 text-body-sm"
        role="status"
      >
        {serverText}
      </p>
    </main>
  );
}
