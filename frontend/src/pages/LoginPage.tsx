import { useSearchParams } from 'react-router';
import duckUrl from '../../../design-system/logos/Duck.svg';
import { messages } from '../messages';

// The backend sends a failed Google sign-in back here with ?error=no-access or ?error=failed.
const errorText: Record<string, string> = {
  'no-access': messages.login.noAccess,
  failed: messages.login.failed,
};

export function LoginPage() {
  const [params] = useSearchParams();
  const error = errorText[params.get('error') ?? ''];

  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col px-space-4 pt-space-6 pb-space-6">
      <div className="flex flex-1 flex-col items-center justify-center gap-space-5 text-center">
        <img src={duckUrl} alt={messages.login.duckAlt} className="h-44 w-auto" />
        <h1 className="type-display">{messages.app.name}</h1>
        <p className="text-ink-muted">{messages.app.tagline}</p>
        {error !== undefined && (
          <p role="alert" className="w-full rounded-md bg-missed-bg px-space-4 py-space-3 text-ink">
            {error}
          </p>
        )}
      </div>
      {/* A full page load, not a router link: the backend starts the Google redirect. */}
      <a
        href="/api/oauth2/authorization/google"
        className="flex h-14 w-full items-center justify-center rounded-md border-bold border-outline bg-accent text-lg font-bold text-on-accent shadow-pop active:translate-x-0.5 active:translate-y-0.5 active:shadow-none"
      >
        {messages.login.continueWithGoogle}
      </a>
    </main>
  );
}
