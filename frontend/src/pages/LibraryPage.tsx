import { Link } from 'react-router';
import { messages } from '../messages';

// Placeholder until M1-06 builds the Library; the Topics button already sits where the wireframe has it.
export function LibraryPage() {
  return (
    <section className="flex flex-col gap-space-3">
      <div className="flex items-center justify-between">
        <h1 className="type-title">{messages.tabs.library}</h1>
        <Link
          to="/topics"
          className="flex min-h-11 items-center rounded-md border-bold border-outline px-space-3 font-semibold"
        >
          {messages.topics.title}
        </Link>
      </div>
      <p className="text-ink-muted">{messages.comingSoon}</p>
    </section>
  );
}
