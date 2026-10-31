import duckMarkUrl from '../../../design-system/logos/duck-mark.svg';
import { firstName, useMe } from '../app/useMe';
import { messages } from '../messages';

// Placeholder: the real Today (due count, start review) comes with the review milestone.
export function TodayPage() {
  const me = useMe();

  return (
    <section className="flex flex-col gap-space-5">
      <p className="type-label text-ink-muted uppercase">{messages.today.title}</p>
      <div className="flex items-center gap-space-3">
        <img src={duckMarkUrl} alt={messages.today.duckAlt} className="size-12" />
        <h1 className="type-title">{messages.today.greeting(firstName(me.data?.name))}</h1>
      </div>
    </section>
  );
}
