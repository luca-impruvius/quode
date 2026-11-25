import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, apiGet } from './client';
import type { components } from './schema';
import { sessionKey, studyApi } from './study';

// Types from the OpenAPI spec (docs/specs/M3-daily-review.md, API).
export type ReviewToday = components['schemas']['ReviewToday'];
export type ReviewExtra = 'NONE' | 'KEEP_GOING' | 'LEARN_MORE';

export const reviewTodayKey = ['review', 'today'] as const;

/** Today's counts. Read again whenever a screen shows them: every answer moves them. */
export function useReviewToday() {
  return useQuery({
    queryKey: reviewTodayKey,
    queryFn: () => apiGet('/v1/review/today'),
    refetchOnMount: 'always',
  });
}

/**
 * Starts today's review (or Keep going / Learn more). The server returns today's unfinished one
 * instead when there is one; 409 means nothing is left, so the counts are read again.
 */
export function useStartReview() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (extra: ReviewExtra) => studyApi.start({ kind: 'REVIEW', extra }),
    onSuccess: (session) => {
      queryClient.setQueryData(sessionKey(session.id), session);
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 409) {
        void queryClient.invalidateQueries({ queryKey: reviewTodayKey });
      }
    },
  });
}
