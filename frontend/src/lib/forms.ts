import { zodResolver } from '@hookform/resolvers/zod';
import { useForm, type FieldValues, type UseFormProps } from 'react-hook-form';
import type { z } from 'zod';

/**
 * React Hook Form with a Zod schema (ADR-0031). Client checks are for fast feedback only; the
 * backend validates again and is the source of truth.
 */
export function useZodForm<Input extends FieldValues, Output extends FieldValues>(
  schema: z.ZodType<Output, Input>,
  props?: Omit<UseFormProps<Input, unknown, Output>, 'resolver'>,
) {
  return useForm<Input, unknown, Output>({
    ...props,
    resolver: zodResolver<Input, unknown, Output>(schema),
  });
}
