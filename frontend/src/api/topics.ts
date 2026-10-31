import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiSend } from './client';
import type { components } from './schema';

/**
 * A topic from GET /api/v1/topics (docs/specs/M1-topics.md). `questionCount`: distinct
 * non-archived questions in the topic and all its sub-topics.
 */
export type Topic = components['schemas']['TopicView'];

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
  return useQuery({ queryKey: topicsKey, queryFn: () => apiGet('/v1/topics') });
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
  // A top-level topic is created without parentId (the API reads a missing one as top level).
  create: (args: { name: string; parentId: string | null }) =>
    apiSend('post', '/v1/topics', {
      body:
        args.parentId === null ? { name: args.name } : { name: args.name, parentId: args.parentId },
    }),
  rename: (args: { id: string; name: string }) =>
    apiSend('put', '/v1/topics/{id}/name', { path: { id: args.id }, body: { name: args.name } }),
  move: (args: { id: string; parentId: string | null }) =>
    apiSend('put', '/v1/topics/{id}/parent', {
      path: { id: args.id },
      body: { parentId: args.parentId },
    }),
  remove: (args: { id: string }) => apiSend('delete', '/v1/topics/{id}', { path: { id: args.id } }),
  addStarter: () => apiSend('post', '/v1/topics/starter'),
};
