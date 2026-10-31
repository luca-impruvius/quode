import { useInfiniteQuery } from '@tanstack/react-query';
import { apiGet } from './client';
import type { QuestionType } from './questions';
import type { components } from './schema';

// Types from the OpenAPI spec (docs/specs/M1-library.md).
export type LibraryItem = components['schemas']['LibraryItem'];

export type LibraryStatus = 'active' | 'archived' | 'flagged';
export type LibrarySort = 'created' | 'updated' | 'difficulty';

/** The Library filters; absent values mean "any". Kept in the URL so links can open a filtered view. */
export interface LibraryFilters {
  q?: string | undefined;
  topicId?: string | undefined;
  type?: QuestionType | undefined;
  difficulty?: number | undefined;
  status: LibraryStatus;
  sort: LibrarySort;
}

const PAGE_SIZE = 20;

/** Pages of matching questions; `fetchNextPage` is "Load more". */
export function useLibrary(filters: LibraryFilters) {
  return useInfiniteQuery({
    queryKey: ['library', filters],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => {
      // Empty values are left out of the URL by the client.
      return apiGet('/v1/questions', {
        query: {
          status: filters.status,
          sort: filters.sort,
          page: pageParam,
          size: PAGE_SIZE,
          ...(filters.q ? { q: filters.q } : {}),
          ...(filters.topicId ? { topicId: filters.topicId } : {}),
          ...(filters.type ? { type: filters.type } : {}),
          ...(filters.difficulty ? { difficulty: filters.difficulty } : {}),
        },
      });
    },
    getNextPageParam: (last) => (last.hasNext ? last.page + 1 : undefined),
  });
}
