import { QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { vi } from 'vitest';
import { createQueryClient } from '../app/queryClient';
import { routes } from '../app/routes';

// The app's defaults, minus the one-second wait before a retry.
function testQueryClient() {
  const client = createQueryClient();
  client.setDefaultOptions({
    queries: { ...client.getDefaultOptions().queries, retryDelay: 0 },
  });
  return client;
}

/** Renders the whole app (real routes) at a path; returns the router to inspect the location. */
export function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <QueryClientProvider client={testQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

// A function answer runs per request; it may return a promise to hold the request open.
type Answer = Response | (() => Response | Promise<Response>);

/**
 * Fakes the backend: keys are "METHOD /api/path" (GET when the method is left out). Any request
 * without an answer fails the test loudly.
 */
export function mockApi(answers: Record<string, Answer>) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const method = init?.method ?? 'GET';
    const answer =
      answers[`${method} ${String(input)}`] ??
      (method === 'GET' ? answers[String(input)] : undefined);
    if (answer === undefined) {
      throw new Error(`Unexpected request: ${method} ${String(input)}`);
    }
    return typeof answer === 'function' ? answer() : answer.clone();
  });
}

export const me = () =>
  Response.json({ name: 'Luca Example', email: 'user@example.com', timeZone: 'Europe/Bucharest' });

export const unauthorized = () =>
  Response.json(
    { status: 401, detail: 'Sign in to continue.' },
    { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
  );
