import { useInfiniteQuery } from '@tanstack/react-query';
import { apiGet } from './client';
import type { QuestionType } from './questions';

// Hand-typed from docs/specs/M1-library.md until M1-08 types the API from the spec.
export interface LibraryItem {
  id: string;
  type: QuestionType;
  promptMd: string;
  difficulty: number;
  primaryTopic: { id: string; name: string; path: string } | null;
  archived: boolean;
  flagged: boolean;
  createdAt: string;
  updatedAt: string;
}

interface LibraryPageResponse {
  items: LibraryItem[];
  page: number;
  size: number;
  totalElements: number;
  hasNext: boolean;
}

export type LibraryStatus = 'active' | 'archived' | 'flagged';
export type LibrarySort = 'created' | 'updated' | 'difficulty';

/** The Library filters; absent values mean "any". Kept in the URL so links can open a filtered view. */
export interface LibraryFilters {
  q?: string;
  topicId?: string;
  type?: QuestionType;
  difficulty?: number;
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
      const params = new URLSearchParams({
        status: filters.status,
        sort: filters.sort,
        page: String(pageParam),
        size: String(PAGE_SIZE),
      });
      if (filters.q) params.set('q', filters.q);
      if (filters.topicId) params.set('topicId', filters.topicId);
      if (filters.type) params.set('type', filters.type);
      if (filters.difficulty) params.set('difficulty', String(filters.difficulty));
      return apiGet<LibraryPageResponse>(`/v1/questions?${params}`);
    },
    getNextPageParam: (last) => (last.hasNext ? last.page + 1 : undefined),
  });
}
