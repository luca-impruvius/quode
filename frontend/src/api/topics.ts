import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiSend } from './client';

/** A topic from GET /api/v1/topics (docs/specs/M1-topics.md). Hand-typed until M1-08 tightens the generated types. */
export interface Topic {
  id: string;
  parentId: string | null;
  name: string;
  /** Distinct non-archived questions in the topic and all its sub-topics. */
  questionCount: number;
}

const topicsKey = ['topics'] as const;

/** "Languages › Java" for every topic; the API sends parents before children. */
export function topicPaths(topics: Topic[]): Map<string, string> {
  const paths = new Map<string, string>();
  for (const topic of topics) {
    const parent = topic.parentId === null ? undefined : paths.get(topic.parentId);
    paths.set(topic.id, parent === undefined ? topic.name : `${parent} › ${topic.name}`);
  }
  return paths;
}

/** The tree in display order: parents before children, siblings A–Z. */
export function useTopics() {
  return useQuery({ queryKey: topicsKey, queryFn: () => apiGet<Topic[]>('/v1/topics') });
}

/** A write to the tree; on success the tree is fetched again (counts and order may change). */
export function useTopicMutation<Args>(send: (args: Args) => Promise<unknown>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: send,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: topicsKey }),
  });
}

export const topicApi = {
  create: (args: { name: string; parentId: string | null }) => apiSend('POST', '/v1/topics', args),
  rename: (args: { id: string; name: string }) =>
    apiSend('PUT', `/v1/topics/${args.id}/name`, { name: args.name }),
  move: (args: { id: string; parentId: string | null }) =>
    apiSend('PUT', `/v1/topics/${args.id}/parent`, { parentId: args.parentId }),
  remove: (args: { id: string }) => apiSend('DELETE', `/v1/topics/${args.id}`),
  addStarter: () => apiSend('POST', '/v1/topics/starter'),
};
