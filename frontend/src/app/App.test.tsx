import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { messages } from '../messages';
import { createQueryClient } from './queryClient';
import { routes } from './routes';

// The app's defaults, minus the one-second wait before a retry.
function testQueryClient() {
  const client = createQueryClient();
  client.setDefaultOptions({
    queries: { ...client.getDefaultOptions().queries, retryDelay: 0 },
  });
  return client;
}

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <QueryClientProvider client={testQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

// Answers GET /api/me; anything else fails the test loudly.
function meAnswers(response: Response) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    if (String(input) === '/api/me') {
      return response.clone();
    }
    throw new Error(`Unexpected request: ${String(input)}`);
  });
}

const signedIn = () =>
  meAnswers(
    Response.json({
      name: 'Luca Example',
      email: 'user@example.com',
      timeZone: 'Europe/Bucharest',
    }),
  );

const signedOut = () =>
  meAnswers(
    Response.json(
      { status: 401, detail: 'Sign in to continue.' },
      { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
    ),
  );

describe('Login', () => {
  it('sends signed-out users to the login screen', async () => {
    const fetchMock = signedOut();

    const router = renderAt('/');

    expect(
      await screen.findByRole('link', { name: messages.login.continueWithGoogle }),
    ).toHaveAttribute('href', '/api/oauth2/authorization/google');
    expect(router.state.location.pathname).toBe('/login');
    // A 401 means signed out: never retried.
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('img', { name: messages.login.duckAlt })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: messages.app.name })).toBeInTheDocument();
  });

  it('tells a blocked account it has no access', async () => {
    signedOut();

    renderAt('/login?error=no-access');

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.login.noAccess);
  });

  it('asks to try again after a failed sign-in', async () => {
    signedOut();

    renderAt('/login?error=failed');

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.login.failed);
  });

  it('sends a signed-in user who opens Login to Today', async () => {
    signedIn();

    const router = renderAt('/login');

    expect(
      await screen.findByRole('heading', { name: messages.today.greeting('Luca') }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
  });
});

describe('App shell', () => {
  it('greets the user by first name on Today', async () => {
    signedIn();

    renderAt('/');

    expect(
      await screen.findByRole('heading', { name: messages.today.greeting('Luca') }),
    ).toBeInTheDocument();
  });

  it('has a tab bar whose other tabs say Coming soon', async () => {
    signedIn();
    renderAt('/');
    const nav = await screen.findByRole('navigation', { name: messages.tabs.label });

    for (const tab of [messages.tabs.library, messages.tabs.stats, messages.tabs.settings]) {
      await userEvent.click(screen.getByRole('link', { name: tab }));
      expect(await screen.findByRole('heading', { name: tab })).toBeInTheDocument();
      expect(screen.getByText(messages.comingSoon)).toBeInTheDocument();
    }

    await userEvent.click(screen.getByRole('link', { name: messages.tabs.add }));
    expect(await screen.findByText(messages.comingSoon)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('link', { name: messages.tabs.today }));
    expect(
      await screen.findByRole('heading', { name: messages.today.greeting('Luca') }),
    ).toBeInTheDocument();
    expect(nav).toBeInTheDocument();
  });

  it('marks the current tab', async () => {
    signedIn();

    renderAt('/library');

    expect(await screen.findByRole('link', { name: messages.tabs.library })).toHaveAttribute(
      'aria-current',
      'page',
    );
  });

  // A 500 is retried once before the error shows.
  it('shows the reference ID when the server fails', async () => {
    const fetchMock = meAnswers(
      Response.json(
        {
          status: 500,
          detail: 'Something went wrong.',
          traceId: '7f3a2c9be1d04a6f8c2e5d7a1b3c4d5e',
        },
        { status: 500, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    );

    renderAt('/');

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.errors.reference('7f3a2c'));
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('shows a not-found page for unknown paths', async () => {
    signedIn();

    renderAt('/no-such-page');

    expect(
      await screen.findByRole('heading', { name: messages.notFound.title }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: messages.notFound.backHome })).toHaveAttribute(
      'href',
      '/',
    );
  });
});
