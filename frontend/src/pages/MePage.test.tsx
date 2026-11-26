import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';
import { THEME_KEY } from '../app/theme';
import { messages } from '../messages';
import { problem } from '../test/questionFixtures';
import { me, mockApi, renderAt, unauthorized } from '../test/renderApp';

const t = messages.me;
const settings = (newPerDay = 5, reviewsPerDay = 30) => Response.json({ newPerDay, reviewsPerDay });

afterEach(() => {
  localStorage.clear();
  delete document.documentElement.dataset.theme;
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
});

describe('Me', () => {
  it('shows the profile', async () => {
    mockApi({ '/api/me': me(), '/api/v1/me/settings': settings() });

    renderAt('/me');

    expect(await screen.findByRole('heading', { name: 'Luca Example' })).toBeInTheDocument();
    expect(screen.getByText('user@example.com')).toBeInTheDocument();
    expect(screen.getByText(messages.me.signedInWithGoogle)).toBeInTheDocument();
  });

  it('switches the theme and remembers it', async () => {
    mockApi({ '/api/me': me(), '/api/v1/me/settings': settings() });
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
    mockApi({ '/api/me': me(), '/api/v1/me/settings': settings() });

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
      '/api/v1/me/settings': settings(),
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
      '/api/v1/me/settings': settings(),
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
      '/api/v1/me/settings': settings(),
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
    mockApi({ '/api/me': me(), '/api/v1/me/settings': settings() });
    renderAt('/me');

    await userEvent.click(await screen.findByRole('link', { name: messages.me.stats }));

    expect(await screen.findByRole('heading', { name: messages.me.stats })).toBeInTheDocument();
    expect(screen.getByText(messages.comingSoon)).toBeInTheDocument();
  });

  describe('daily limits', () => {
    it('shows the saved limits', async () => {
      mockApi({ '/api/me': me(), '/api/v1/me/settings': settings(7, 40) });

      renderAt('/me');

      const fresh = await screen.findByLabelText(t.newPerDay);
      await waitFor(() => expect(fresh).toHaveValue(7));
      expect(screen.getByLabelText(t.reviewsPerDay)).toHaveValue(40);
      expect(screen.getByText(t.newPerDayHint)).toBeInTheDocument();
    });

    it('saves new limits and confirms', async () => {
      document.cookie = 'XSRF-TOKEN=token-123';
      const fetchMock = mockApi({
        '/api/me': me(),
        '/api/v1/me/settings': settings(),
        'PUT /api/v1/me/settings': () => settings(12, 1),
      });
      const user = userEvent.setup();
      renderAt('/me');
      const fresh = await screen.findByLabelText(t.newPerDay);
      await waitFor(() => expect(fresh).toHaveValue(5));

      await user.clear(fresh);
      await user.type(fresh, '12');
      await user.clear(screen.getByLabelText(t.reviewsPerDay));
      await user.type(screen.getByLabelText(t.reviewsPerDay), '1');
      await user.click(screen.getByRole('button', { name: t.saveLimits }));

      expect(await screen.findByText(t.saved)).toBeInTheDocument();
      const put = fetchMock.mock.calls.find(
        ([url, init]) => String(url) === '/api/v1/me/settings' && init?.method === 'PUT',
      );
      expect(JSON.parse(String(put?.[1]?.body))).toEqual({ newPerDay: 12, reviewsPerDay: 1 });
      expect((put?.[1]?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('token-123');
    });

    it.each([
      ['101', '30', t.newPerDayRange],
      ['-1', '30', t.newPerDayRange],
      ['2.5', '30', t.newPerDayRange],
      ['', '30', t.newPerDayRange],
      ['5', '0', t.reviewsPerDayRange],
      ['5', '501', t.reviewsPerDayRange],
    ])('refuses new %s, reviews %s without sending', async (fresh, reviews, error) => {
      const fetchMock = mockApi({ '/api/me': me(), '/api/v1/me/settings': settings() });
      const user = userEvent.setup();
      renderAt('/me');
      const freshField = await screen.findByLabelText(t.newPerDay);
      await waitFor(() => expect(freshField).toHaveValue(5));

      await user.clear(freshField);
      if (fresh !== '') await user.type(freshField, fresh);
      await user.clear(screen.getByLabelText(t.reviewsPerDay));
      await user.type(screen.getByLabelText(t.reviewsPerDay), reviews);
      await user.click(screen.getByRole('button', { name: t.saveLimits }));

      expect(await screen.findByText(error)).toBeInTheDocument();
      expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false);
    });

    it('accepts the bounds 0 and 500', async () => {
      const fetchMock = mockApi({
        '/api/me': me(),
        '/api/v1/me/settings': settings(),
        'PUT /api/v1/me/settings': () => settings(0, 500),
      });
      const user = userEvent.setup();
      renderAt('/me');
      const freshField = await screen.findByLabelText(t.newPerDay);
      await waitFor(() => expect(freshField).toHaveValue(5));

      await user.clear(freshField);
      await user.type(freshField, '0');
      await user.clear(screen.getByLabelText(t.reviewsPerDay));
      await user.type(screen.getByLabelText(t.reviewsPerDay), '500');
      await user.click(screen.getByRole('button', { name: t.saveLimits }));

      expect(await screen.findByText(t.saved)).toBeInTheDocument();
      const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT');
      expect(JSON.parse(String(put?.[1]?.body))).toEqual({ newPerDay: 0, reviewsPerDay: 500 });
    });

    it("shows the server's refusal", async () => {
      mockApi({
        '/api/me': me(),
        '/api/v1/me/settings': settings(),
        'PUT /api/v1/me/settings': () =>
          problem(400, 'New per day must be 0 to 100.', 'abcdef0123456789abcdef0123456789'),
      });
      const user = userEvent.setup();
      renderAt('/me');
      const fresh = await screen.findByLabelText(t.newPerDay);
      await waitFor(() => expect(fresh).toHaveValue(5));

      await user.click(screen.getByRole('button', { name: t.saveLimits }));

      expect(await screen.findByRole('alert')).toHaveTextContent('New per day must be 0 to 100.');
      expect(screen.queryByText(t.saved)).not.toBeInTheDocument();
    });
  });
});
