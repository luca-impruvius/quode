import { useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

const focusable =
  'a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), button:not([disabled])';

/**
 * A bottom sheet for short tasks (a form, a confirmation). Modal for real: rendered at the end of
 * <body>, everything else is `inert` while it's open, Tab stays inside, Escape or a tap on the
 * backdrop closes it. Focus moves in on open and back on close (to `returnFocus` when the opener is
 * gone, e.g. a deleted row). The panel's bottom padding keeps it above the phone's home indicator.
 */
export function Sheet({
  title,
  onClose,
  returnFocus,
  children,
}: {
  title: string;
  onClose: () => void;
  returnFocus?: () => HTMLElement | null;
  children: ReactNode;
}) {
  const titleId = useId();
  const panel = useRef<HTMLDivElement>(null);
  const [host] = useState(() => document.createElement('div'));

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    document.body.append(host);
    const others = [...document.body.children].filter(
      (el) => el !== host && !el.hasAttribute('inert'),
    );
    others.forEach((el) => el.setAttribute('inert', ''));
    panel.current?.querySelector<HTMLElement>(focusable)?.focus();
    return () => {
      others.forEach((el) => el.removeAttribute('inert'));
      host.remove();
      const target = previous?.isConnected ? previous : returnFocus?.();
      target?.focus();
    };
    // The sheet mounts once per opening; returnFocus is read only on close.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [host]);

  function onKeyDown(event: KeyboardEvent) {
    if (event.key === 'Escape') {
      onClose();
      return;
    }
    if (event.key !== 'Tab') {
      return;
    }
    const items = [...(panel.current?.querySelectorAll<HTMLElement>(focusable) ?? [])];
    const first = items[0];
    const last = items.at(-1);
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  }

  return createPortal(
    <div
      className="fixed inset-0 z-10 flex items-end justify-center bg-bg/75"
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
    >
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onKeyDown={onKeyDown}
        className="w-full max-w-md rounded-t-lg border-t-2 border-outline bg-surface pb-[env(safe-area-inset-bottom)]"
      >
        <div className="flex flex-col gap-space-4 px-space-4 pt-space-5 pb-space-5">
          <h2 id={titleId} className="type-heading">
            {title}
          </h2>
          {children}
        </div>
      </div>
    </div>,
    host,
  );
}
