import { useQuery } from '@tanstack/react-query';
import { ApiError, apiGet } from '../api/client';
import type { components } from '../api/schema';

export type Me = components['schemas']['MeResponse'];

/** The signed-in user from GET /api/me. A 401 means signed out; it is never retried. */
export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: () => apiGet<Me>('/me'),
    retry: (failures, error) => !isSignedOut(error) && failures < 1,
  });
}

export function isSignedOut(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401;
}

/** "Luca" from "Luca Example"; the whole name if it has no space. */
export function firstName(name: string | undefined): string {
  return name?.trim().split(/\s+/)[0] ?? '';
}
