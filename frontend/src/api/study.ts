import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiSend } from './client';
import type { components } from './schema';

// Types from the OpenAPI spec (docs/specs/M2-exam-prep-mvp.md, Quick quiz).
export type SessionView = components['schemas']['SessionView'];
export type QuestionCard = components['schemas']['QuestionCard'];
export type AnswerResult = components['schemas']['AnswerResult'];
export type FinishResult = components['schemas']['FinishResult'];
export type StartSession = components['schemas']['StartSession'];
export type QuizMode = 'ALL' | 'UNSEEN' | 'MISSED';
export type SelfGrade = 'GOT_IT' | 'PARTIAL' | 'MISSED';

export const sessionKey = (id: string) => ['study', id] as const;

/** One quiz; fetched once and then kept up to date from the answers' responses. */
export function useSession(id: string) {
  return useQuery({
    queryKey: sessionKey(id),
    queryFn: () => apiGet('/v1/study/sessions/{id}', { path: { id } }),
    retry: false,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  });
}

export const studyApi = {
  start: (input: StartSession) => apiSend('post', '/v1/study/sessions', { body: input }),
  answer: (args: {
    id: string;
    questionId: string;
    selectedOptionIds?: string[];
    grade?: SelfGrade;
  }) =>
    apiSend('post', '/v1/study/sessions/{id}/answers', {
      path: { id: args.id },
      body: {
        questionId: args.questionId,
        ...(args.selectedOptionIds ? { selectedOptionIds: args.selectedOptionIds } : {}),
        ...(args.grade ? { grade: args.grade } : {}),
      },
    }),
  finish: (id: string) => apiSend('post', '/v1/study/sessions/{id}/finish', { path: { id } }),
};

/** Starts a quiz and remembers it as the one to continue. */
export function useStartQuiz() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: studyApi.start,
    onSuccess: (session) => {
      queryClient.setQueryData(sessionKey(session.id), session);
      rememberQuiz(session.id);
    },
  });
}

// The quiz to continue lives on the server (ADR-0002); this browser only remembers its id.
const CURRENT = 'quode.quiz.current';

export function rememberQuiz(id: string | null) {
  try {
    if (id === null) {
      localStorage.removeItem(CURRENT);
    } else {
      localStorage.setItem(CURRENT, id);
    }
  } catch {
    // Storage blocked: Continue just doesn't show.
  }
}

export function rememberedQuiz(): string | null {
  try {
    return localStorage.getItem(CURRENT);
  } catch {
    return null;
  }
}
