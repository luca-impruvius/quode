import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { ImportInput } from '../lib/importParser';
import { apiSend } from './client';
import type { components } from './schema';

export type ImportReport = components['schemas']['ImportReport'];

/** Check (dryRun) or import (docs/specs/M2-exam-prep-mvp.md). A real import refreshes lists. */
export function useImport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (args: { input: ImportInput; dryRun: boolean }) =>
      apiSend('post', '/v1/imports', { query: { dryRun: args.dryRun }, body: args.input }),
    onSuccess: (_report, args) => {
      if (!args.dryRun) {
        void queryClient.invalidateQueries({ queryKey: ['topics'] });
        void queryClient.invalidateQueries({ queryKey: ['library'] });
      }
    },
  });
}
