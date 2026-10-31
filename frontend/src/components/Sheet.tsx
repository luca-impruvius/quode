import { useEffect, useId, useRef, type ReactNode } from 'react';

/**
 * A bottom sheet for short tasks (a form, a confirmation): modal, titled, closed with Escape. Focus
 * moves into the sheet when it opens and back to where it was when it closes. The panel's bottom
 * padding keeps the content above the phone's home indicator.
 */
export function Sheet({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const titleId = useId();
  const panel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const first = panel.current?.querySelector<HTMLElement>(
      'input, select, textarea, button:not([disabled])',
    );
    first?.focus();
    return () => previous?.focus();
  }, []);

  return (
    <div className="fixed inset-0 z-10 flex items-end justify-center bg-bg/75">
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            onClose();
          }
        }}
        className="w-full max-w-md rounded-t-lg border-t-2 border-outline bg-surface pb-[env(safe-area-inset-bottom)]"
      >
        <div className="flex flex-col gap-space-4 px-space-4 pt-space-5 pb-space-5">
          <h2 id={titleId} className="type-heading">
            {title}
          </h2>
          {children}
        </div>
      </div>
    </div>
  );
}
