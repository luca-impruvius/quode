import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiSend } from './client';
import { reviewTodayKey } from './review';
import type { components } from './schema';

// Types from the OpenAPI spec (docs/specs/M3-daily-review.md, API).
export type Settings = components['schemas']['Settings'];

export const settingsKey = ['me', 'settings'] as const;

/** The daily limits: new questions and reviews per day. */
export function useSettings() {
  return useQuery({
    queryKey: settingsKey,
    queryFn: () => apiGet('/v1/me/settings'),
  });
}

/** Saves the limits; today's counts change at once, so they are read again. */
export function useSaveSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (settings: Settings) => apiSend('put', '/v1/me/settings', { body: settings }),
    onSuccess: (saved) => {
      queryClient.setQueryData(settingsKey, saved);
      void queryClient.invalidateQueries({ queryKey: reviewTodayKey });
    },
  });
}
