import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, apiGet, apiSend } from './client';
import type { components } from './schema';

// Types from the OpenAPI spec (docs/specs/M1-questions.md).
export type QuestionView = components['schemas']['QuestionView'];
export type QuestionType = QuestionView['type'];
/** What create and edit send; `version` is required on edit. */
export type QuestionInput = components['schemas']['QuestionInput'];

export const questionKey = (id: string) => ['questions', id] as const;

/**
 * One question. `live: false` (the editor) loads it once and never refetches in the background, so
 * a form built from it keeps the user's typing; only an explicit refetch updates it.
 */
export function useQuestion(id: string | undefined, { live = true }: { live?: boolean } = {}) {
  return useQuery({
    queryKey: questionKey(id ?? ''),
    queryFn: () => apiGet('/v1/questions/{id}', { path: { id: id ?? '' } }),
    enabled: id !== undefined,
    // A missing question won't appear on a retry.
    retry: false,
    ...(live
      ? {}
      : { staleTime: Infinity, refetchOnWindowFocus: false, refetchOnReconnect: false }),
  });
}

/** Writes return the saved question; it replaces the cached copy, and topic counts refresh. */
export function useQuestionMutation<Args>(send: (args: Args) => Promise<QuestionView>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: send,
    onSuccess: (saved) => {
      queryClient.setQueryData(questionKey(saved.id), saved);
      // Counts, badges and filtered lists may all have changed.
      void queryClient.invalidateQueries({ queryKey: ['topics'] });
      void queryClient.invalidateQueries({ queryKey: ['library'] });
    },
  });
}

/** After a stale write (409), fetch the question again so the next try uses the new version. */
export function useRefreshOnConflict() {
  const queryClient = useQueryClient();
  return (id: string, error: unknown) => {
    if (error instanceof ApiError && error.status === 409) {
      void queryClient.invalidateQueries({ queryKey: questionKey(id) });
    }
  };
}

export const questionApi = {
  create: (input: QuestionInput) => apiSend('post', '/v1/questions', { body: input }),
  update: (args: { id: string; input: QuestionInput }) =>
    apiSend('put', '/v1/questions/{id}', { path: { id: args.id }, body: args.input }),
  archive: (args: { id: string; version: number }) =>
    apiSend('post', '/v1/questions/{id}/archive', {
      path: { id: args.id },
      body: { version: args.version },
    }),
  flag: (args: { id: string; version: number; note: string }) =>
    apiSend('post', '/v1/questions/{id}/flag', {
      path: { id: args.id },
      body: { version: args.version, note: args.note },
    }),
  unflag: (args: { id: string; version: number }) =>
    apiSend('post', '/v1/questions/{id}/unflag', {
      path: { id: args.id },
      body: { version: args.version },
    }),
  unarchive: (args: { id: string; version: number }) =>
    apiSend('post', '/v1/questions/{id}/unarchive', {
      path: { id: args.id },
      body: { version: args.version },
    }),
};
