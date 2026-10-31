import { messages } from '../messages';

/** Placeholder for tabs that later milestones fill in. */
export function ComingSoonPage({ title }: { title: string }) {
  return (
    <section className="flex flex-col gap-space-3">
      <h1 className="type-title">{title}</h1>
      <p className="text-ink-muted">{messages.comingSoon}</p>
    </section>
  );
}
