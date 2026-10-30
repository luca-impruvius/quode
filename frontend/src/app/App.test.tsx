import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
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
}

function mockFetch(response: Response) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue(response);
}

describe('App', () => {
  it('shows the start screen with the server status from /api/health', async () => {
    const fetchMock = mockFetch(Response.json({ status: 'UP' }));

    renderAt('/');

    expect(screen.getByRole('heading', { name: messages.app.name })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: messages.home.duckAlt })).toBeInTheDocument();
    expect(await screen.findByText(messages.home.serverUp)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/health', expect.anything());
  });

  it('says when the server is not reachable', async () => {
    mockFetch(new Response(null, { status: 502 }));

    renderAt('/');

    expect(await screen.findByText(messages.home.serverDown)).toBeInTheDocument();
  });

  it('shows a not-found page for unknown paths', () => {
    mockFetch(Response.json({ status: 'UP' }));

    renderAt('/no-such-page');

    expect(screen.getByRole('heading', { name: messages.notFound.title })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: messages.notFound.backHome })).toHaveAttribute(
      'href',
      '/',
    );
  });
});
