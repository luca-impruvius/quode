import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/client';
import { messages } from '../messages';
import { ErrorNotice } from './ErrorNotice';

describe('ErrorNotice', () => {
  it("shows the server's message for a 4xx", () => {
    render(<ErrorNotice error={new ApiError(409, 'Questions use this topic.', 'abcdef0123')} />);

    expect(screen.getByRole('alert')).toHaveTextContent('Questions use this topic.');
  });

  it('shows the reference, not the message, for a 5xx', () => {
    render(<ErrorNotice error={new ApiError(500, 'NullPointerException at …', 'abcdef0123')} />);

    expect(screen.getByRole('alert')).toHaveTextContent(messages.errors.reference('abcdef'));
  });

  it('falls back to a generic sentence without a reference', () => {
    render(<ErrorNotice error={new Error('network down')} />);

    expect(screen.getByRole('alert')).toHaveTextContent(messages.errors.generic);
  });
});
