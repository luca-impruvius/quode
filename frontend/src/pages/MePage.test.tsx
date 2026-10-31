import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';
import { THEME_KEY } from '../app/theme';
import { messages } from '../messages';
import { me, mockApi, renderAt, unauthorized } from '../test/renderApp';

afterEach(() => {
  localStorage.clear();
  delete document.documentElement.dataset.theme;
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
});

describe('Me', () => {
  it('shows the profile', async () => {
    mockApi({ '/api/me': me() });

    renderAt('/me');

    expect(await screen.findByRole('heading', { name: 'Luca Example' })).toBeInTheDocument();
    expect(screen.getByText('user@example.com')).toBeInTheDocument();
    expect(screen.getByText(messages.me.signedInWithGoogle)).toBeInTheDocument();
  });

  it('switches the theme and remembers it', async () => {
    mockApi({ '/api/me': me() });
    renderAt('/me');
    const dark = await screen.findByRole('button', { name: messages.me.themeDark });

    await userEvent.click(dark);

    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(localStorage.getItem(THEME_KEY)).toBe('dark');
    expect(dark).toHaveAttribute('aria-pressed', 'true');

    await userEvent.click(screen.getByRole('button', { name: messages.me.themeSystem }));

    expect(document.documentElement.dataset.theme).toBeUndefined();
    expect(localStorage.getItem(THEME_KEY)).toBeNull();
  });

  it('starts with the remembered theme selected', async () => {
    localStorage.setItem(THEME_KEY, 'light');
    mockApi({ '/api/me': me() });

    renderAt('/me');

    expect(await screen.findByRole('button', { name: messages.me.themeLight })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  it('signs out with the CSRF token and shows Login', async () => {
    document.cookie = 'XSRF-TOKEN=token-123';
    let signedIn = true;
    const fetchMock = mockApi({
      '/api/me': () => (signedIn ? me() : unauthorized()),
      'POST /api/logout': () => {
        signedIn = false;
        return new Response(null, { status: 204 });
      },
    });
    renderAt('/me');

    await userEvent.click(await screen.findByRole('button', { name: messages.me.signOut }));

    expect(
      await screen.findByRole('link', { name: messages.login.continueWithGoogle }),
    ).toBeInTheDocument();
    const logout = fetchMock.mock.calls.find(([url]) => String(url) === '/api/logout');
    expect((logout?.[1]?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('token-123');
  });

  it('shows the reference when signing out fails', async () => {
    mockApi({
      '/api/me': me(),
      'POST /api/logout': Response.json(
        { status: 500, traceId: 'abcdef0123456789abcdef0123456789' },
        { status: 500 },
      ),
    });
    renderAt('/me');

    await userEvent.click(await screen.findByRole('button', { name: messages.me.signOut }));

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.errors.reference('abcdef'));
    // Usable again, so a retry is possible.
    expect(screen.getByRole('button', { name: messages.me.signOut })).toBeEnabled();
  });

  it('can be pressed only once while signing out', async () => {
    let finish: (response: Response) => void = () => {};
    mockApi({
      '/api/me': me(),
      'POST /api/logout': () =>
        new Promise<Response>((resolve) => {
          finish = resolve;
        }),
    });
    renderAt('/me');
    const button = await screen.findByRole('button', { name: messages.me.signOut });

    await userEvent.click(button);

    expect(button).toBeDisabled();
    finish(new Response(null, { status: 204 }));
  });

  it('links to the Stats placeholder', async () => {
    mockApi({ '/api/me': me() });
    renderAt('/me');

    await userEvent.click(await screen.findByRole('link', { name: messages.me.stats }));

    expect(await screen.findByRole('heading', { name: messages.me.stats })).toBeInTheDocument();
    expect(screen.getByText(messages.comingSoon)).toBeInTheDocument();
  });
});
