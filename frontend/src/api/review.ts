import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, apiGet, apiSend } from './client';
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

export type QuestionReview = components['schemas']['QuestionReview'];
export type ReviewControls = { enrolled: boolean; learnNext: boolean };

export const questionReviewKey = (id: string) => ['questions', id, 'review'] as const;

export function fetchQuestionReview(id: string) {
  return apiGet('/v1/questions/{id}/review', { path: { id } });
}

export function saveQuestionReview(id: string, controls: ReviewControls) {
  return apiSend('put', '/v1/questions/{id}/review', { path: { id }, body: controls });
}

/** A question's place in review. Read again on every visit: any answer may have moved it. */
export function useQuestionReview(id: string) {
  return useQuery({
    queryKey: questionReviewKey(id),
    queryFn: () => fetchQuestionReview(id),
    refetchOnMount: 'always',
  });
}

/** Learn next, Remove from review and Add to review. Today's counts may change, so they refresh. */
export function useSaveQuestionReview(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (controls: ReviewControls) => saveQuestionReview(id, controls),
    onSuccess: (saved) => {
      queryClient.setQueryData(questionReviewKey(id), saved);
      void queryClient.invalidateQueries({ queryKey: reviewTodayKey });
    },
  });
}

/** Of these questions, the ones out of review (Remove from review earlier); empty until known. */
export function useOutOfReview(ids: string[]): string[] {
  return useQueries({
    queries: ids.map((id) => ({
      queryKey: questionReviewKey(id),
      queryFn: () => fetchQuestionReview(id),
      refetchOnMount: 'always' as const,
    })),
    combine: (results) => ids.filter((_, index) => results[index]?.data?.enrolled === false),
  });
}

/** Add to review, as new, for several questions at once (Add missed to review). */
export function useAddToReview() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (ids: string[]) =>
      Promise.all(
        ids.map(async (id) => {
          const saved = await saveQuestionReview(id, { enrolled: true, learnNext: false });
          queryClient.setQueryData(questionReviewKey(id), saved);
          return saved;
        }),
      ),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: reviewTodayKey });
    },
  });
}
