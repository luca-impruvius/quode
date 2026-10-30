import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { z } from 'zod';
import { useZodForm } from './forms';

const schema = z.object({ title: z.string().trim().min(1, 'Title is required') });

function TitleForm({ onSave }: { onSave: (values: z.output<typeof schema>) => void }) {
  const form = useZodForm(schema, { defaultValues: { title: '' } });
  return (
    <form onSubmit={form.handleSubmit(onSave)}>
      <label htmlFor="title">Title</label>
      <input id="title" {...form.register('title')} />
      {form.formState.errors.title && <p role="alert">{form.formState.errors.title.message}</p>}
      <button type="submit">Save</button>
    </form>
  );
}

describe('useZodForm', () => {
  it('shows the schema message and does not submit invalid input', async () => {
    const onSave = vi.fn();
    render(<TitleForm onSave={onSave} />);

    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Title is required');
    expect(onSave).not.toHaveBeenCalled();
  });

  it('submits the parsed values', async () => {
    const onSave = vi.fn();
    render(<TitleForm onSave={onSave} />);

    await userEvent.type(screen.getByLabelText('Title'), '  HashMap  ');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(onSave).toHaveBeenCalledWith({ title: 'HashMap' }, expect.anything());
  });
});
