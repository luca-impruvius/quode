import { BarChart3, BookOpen, House, Plus, Settings, type LucideIcon } from 'lucide-react';
import { NavLink, Outlet } from 'react-router';
import { messages } from '../messages';

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
  { to: '/stats', label: messages.tabs.stats, Icon: BarChart3 },
  { to: '/settings', label: messages.tabs.settings, Icon: Settings },
];

/** The signed-in frame: the screen above, the tab bar with the add button at the bottom. */
export function AppShell() {
  return (
    <div className="mx-auto flex min-h-dvh max-w-md flex-col">
      <div className="flex-1 px-space-4 pt-space-5 pb-28">
        <Outlet />
      </div>
      <nav
        aria-label={messages.tabs.label}
        className="fixed inset-x-0 bottom-0 mx-auto flex h-[72px] max-w-md items-start justify-around border-t border-hair border-line bg-surface pt-2.5 pb-[env(safe-area-inset-bottom)]"
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
    </div>
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
