import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { messages } from '../messages';
import { createQueryClient } from './queryClient';
import { routes } from './routes';

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <QueryClientProvider client={createQueryClient()}>
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
    signedOut();

    const router = renderAt('/');

    expect(
      await screen.findByRole('link', { name: messages.login.continueWithGoogle }),
    ).toHaveAttribute('href', '/api/oauth2/authorization/google');
    expect(router.state.location.pathname).toBe('/login');
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
    expect(await screen.findByRole('heading', { name: messages.today.greeting('Luca') }));
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

  // A 500 is retried once (about a second) before the error shows.
  it('shows the reference ID when the server fails', async () => {
    meAnswers(
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

    expect(await screen.findByRole('alert', {}, { timeout: 3000 })).toHaveTextContent(
      messages.errors.reference('7f3a2c'),
    );
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
