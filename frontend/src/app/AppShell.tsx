import { BookOpen, House, ListChecks, Plus, UserRound, type LucideIcon } from 'lucide-react';
import { NavLink, Outlet } from 'react-router';
import { messages } from '../messages';
import { useDesktop } from './useDesktop';

interface Tab {
  to: string;
  label: string;
  Icon: LucideIcon;
}

const leftTabs: Tab[] = [
  { to: '/', label: messages.tabs.today, Icon: House },
  { to: '/library', label: messages.tabs.library, Icon: BookOpen },
];
const rightTabs: Tab[] = [
  { to: '/quizzes', label: messages.tabs.quizzes, Icon: ListChecks },
  { to: '/me', label: messages.tabs.me, Icon: UserRound },
];

/**
 * The signed-in frame: the screen above, the tab bar with the add button at the bottom. From 1024
 * px wide a sidebar replaces the tab bar and the screen widens to about 1100 px.
 */
export function AppShell() {
  // One navigation at a time, so there is a single "Main" landmark.
  const desktop = useDesktop();
  return (
    <div className="mx-auto flex min-h-dvh max-w-md flex-col lg:max-w-[1100px] lg:flex-row lg:gap-space-6">
      {desktop && <Sidebar />}
      <main className="flex-1 px-space-4 pt-space-5 pb-[calc(7rem+env(safe-area-inset-bottom))] lg:min-w-0 lg:pb-space-6">
        <Outlet />
      </main>
      {!desktop && (
        <nav
          aria-label={messages.tabs.label}
          className="fixed inset-x-0 bottom-0 mx-auto flex h-[calc(72px+env(safe-area-inset-bottom))] max-w-md items-start justify-around border-t border-line bg-surface pt-2.5 pb-[env(safe-area-inset-bottom)] lg:hidden"
        >
          {leftTabs.map((tab) => (
            <TabLink key={tab.to} {...tab} />
          ))}
          <NavLink
            to="/add"
            aria-label={messages.tabs.add}
            className="-mt-6 flex size-14 items-center justify-center rounded-pill border-bold border-outline bg-accent text-on-accent shadow-pop"
          >
            <Plus aria-hidden="true" size={24} strokeWidth={2} />
          </NavLink>
          {rightTabs.map((tab) => (
            <TabLink key={tab.to} {...tab} />
          ))}
        </nav>
      )}
    </div>
  );
}

/** Desktop only: the same places as the tab bar, in a column, with Add first. */
function Sidebar() {
  return (
    <nav
      aria-label={messages.tabs.label}
      className="sticky top-0 hidden h-dvh w-56 shrink-0 flex-col gap-space-2 border-r border-line px-space-3 py-space-5 lg:flex"
    >
      <NavLink
        to="/add"
        className="mb-space-3 flex min-h-12 items-center gap-space-2 rounded-pill border-bold border-outline bg-accent px-space-4 font-bold text-on-accent shadow-pop"
      >
        <Plus aria-hidden="true" size={20} strokeWidth={2} />
        {messages.tabs.add}
      </NavLink>
      {[...leftTabs, ...rightTabs].map(({ to, label, Icon }) => (
        <NavLink
          key={to}
          to={to}
          end
          className={({ isActive }) =>
            `flex min-h-11 items-center gap-space-3 rounded-md px-space-3 ${
              isActive ? 'bg-accent font-bold text-on-accent' : 'text-ink-muted'
            }`
          }
        >
          <Icon aria-hidden="true" size={20} strokeWidth={2} />
          {label}
        </NavLink>
      ))}
    </nav>
  );
}

function TabLink({ to, label, Icon }: Tab) {
  return (
    <NavLink
      to={to}
      end
      className={({ isActive }) =>
        `group flex min-h-11 min-w-14 flex-col items-center gap-space-1 text-[11px] leading-none ${
          isActive ? 'font-bold text-ink' : 'text-ink-muted'
        }`
      }
    >
      <span className="flex h-6 w-9 items-center justify-center rounded-pill group-aria-[current=page]:bg-accent group-aria-[current=page]:text-on-accent">
        <Icon aria-hidden="true" size={20} strokeWidth={2} />
      </span>
      {label}
    </NavLink>
  );
}
