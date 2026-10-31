import { ApiError } from '../api/client';
import { messages } from '../messages';

/**
 * The text for an error. A 4xx answer shows the server's message (a fixed, user-facing sentence by
 * convention: "Questions use this topic."); anything else shows the short reference, so a report can
 * be matched to the server logs.
 */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status >= 400 && error.status < 500 && error.message !== '') {
      return error.message;
    }
    if (error.reference !== undefined) {
      return messages.errors.reference(error.reference);
    }
  }
  return messages.errors.generic;
}
