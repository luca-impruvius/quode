import { QueryClient } from '@tanstack/react-query';

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      // One retry is enough on a phone; errors then reach the screen with their reference.
      queries: { retry: 1, staleTime: 30_000 },
    },
  });
}
