import { Link } from 'react-router';
import { messages } from '../messages';

export function NotFoundPage() {
  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col items-center justify-center gap-space-4 px-space-4">
      <h1 className="type-title">{messages.notFound.title}</h1>
      <Link to="/" className="type-heading underline">
        {messages.notFound.backHome}
      </Link>
    </main>
  );
}
