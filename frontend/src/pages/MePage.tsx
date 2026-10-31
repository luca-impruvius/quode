import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ChevronRight } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { apiSend } from '../api/client';
import { useMe } from '../app/useMe';
import { applyTheme, readTheme, saveTheme, type Theme } from '../app/theme';
import { ErrorNotice } from '../components/ErrorNotice';
import { messages } from '../messages';

const themes: { value: Theme; label: string }[] = [
  { value: 'system', label: messages.me.themeSystem },
  { value: 'light', label: messages.me.themeLight },
  { value: 'dark', label: messages.me.themeDark },
];

const row = 'flex min-h-13 items-center justify-between border-b border-line';

export function MePage() {
  const me = useMe();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [theme, setTheme] = useState<Theme>(readTheme);

  const signOut = useMutation({
    mutationFn: () => apiSend('POST', '/logout'),
    onSuccess: () => {
      // Forget everything cached for this user before showing Login.
      queryClient.clear();
      void navigate('/login', { replace: true });
    },
  });

  function chooseTheme(next: Theme) {
    setTheme(next);
    saveTheme(next);
    applyTheme(next);
  }

  const name = me.data?.name ?? '';

  return (
    <section className="flex flex-col gap-space-5">
      <div className="flex items-center gap-space-3">
        <span
          aria-hidden="true"
          className="flex size-12 items-center justify-center rounded-pill border-bold border-outline bg-accent type-heading text-on-accent"
        >
          {name.charAt(0).toUpperCase()}
        </span>
        <div className="flex min-w-0 flex-col">
          <h1 className="type-title">{name}</h1>
          <p className="type-body-sm text-ink-muted">{messages.me.signedInWithGoogle}</p>
          <p className="truncate type-body-sm text-ink-muted">{me.data?.email}</p>
        </div>
      </div>

      <div className="flex flex-col">
        <h2 className="type-label text-ink-muted uppercase">{messages.me.settings}</h2>
        <div className={row}>
          <span id="theme-label">{messages.me.theme}</span>
          {/* No overflow-hidden here: it would clip the focus ring; the end buttons round instead. */}
          <div
            role="group"
            aria-labelledby="theme-label"
            className="flex rounded-sm border-bold border-outline"
          >
            {themes.map((option) => (
              <button
                key={option.value}
                type="button"
                aria-pressed={theme === option.value}
                onClick={() => chooseTheme(option.value)}
                className="min-h-11 px-space-3 type-body-sm first:rounded-l-[6px] last:rounded-r-[6px] aria-pressed:bg-ink aria-pressed:text-surface"
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>
        <Link to="/stats" className={row}>
          <span>{messages.me.stats}</span>
          <ChevronRight aria-hidden="true" size={20} strokeWidth={2} />
        </Link>
        <button
          type="button"
          onClick={() => signOut.mutate()}
          disabled={signOut.isPending}
          className="flex min-h-13 items-center text-left font-semibold disabled:opacity-45"
        >
          {messages.me.signOut}
        </button>
      </div>

      {signOut.isError && <ErrorNotice error={signOut.error} />}
    </section>
  );
}
