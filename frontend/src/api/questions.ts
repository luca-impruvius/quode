import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiSend } from './client';

// Hand-typed from docs/specs/M1-questions.md until M1-08 types the API from the spec.
export type QuestionType = 'FLASHCARD' | 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'OPEN_TEXT';

export interface QuestionInput {
  type: QuestionType;
  promptMd: string;
  answerMd: string | null;
  explanationMd: string | null;
  difficulty: number;
  sourceUrl: string | null;
  topicIds: string[];
  primaryTopicId: string;
  options: { textMd: string; correct: boolean }[];
  /** The version the edit started from; required on edit. */
  version?: number;
}

export interface QuestionView {
  id: string;
  type: QuestionType;
  promptMd: string;
  answerMd: string | null;
  explanationMd: string | null;
  difficulty: number;
  status: 'DRAFT' | 'ACTIVE';
  sourceUrl: string | null;
  /** The primary first, then by path. */
  topics: { id: string; name: string; path: string; primary: boolean }[];
  options: { id: string; textMd: string; correct: boolean }[];
  flaggedAt: string | null;
  flagNote: string | null;
  archivedAt: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export const questionKey = (id: string) => ['questions', id] as const;

export function useQuestion(id: string | undefined) {
  return useQuery({
    queryKey: questionKey(id ?? ''),
    queryFn: () => apiGet<QuestionView>(`/v1/questions/${id}`),
    enabled: id !== undefined,
    // A missing question won't appear on a retry.
    retry: false,
  });
}

/** Writes return the saved question; it replaces the cached copy, and topic counts refresh. */
export function useQuestionMutation<Args>(send: (args: Args) => Promise<QuestionView>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: send,
    onSuccess: (saved) => {
      queryClient.setQueryData(questionKey(saved.id), saved);
      void queryClient.invalidateQueries({ queryKey: ['topics'] });
    },
  });
}

export const questionApi = {
  create: (input: QuestionInput) => apiSend<QuestionView>('POST', '/v1/questions', input),
  update: (args: { id: string; input: QuestionInput }) =>
    apiSend<QuestionView>('PUT', `/v1/questions/${args.id}`, args.input),
  archive: (args: { id: string; version: number }) =>
    apiSend<QuestionView>('POST', `/v1/questions/${args.id}/archive`, { version: args.version }),
  unarchive: (args: { id: string; version: number }) =>
    apiSend<QuestionView>('POST', `/v1/questions/${args.id}/unarchive`, {
      version: args.version,
    }),
};
