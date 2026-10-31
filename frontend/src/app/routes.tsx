import type { RouteObject } from 'react-router';
import { messages } from '../messages';
import { ComingSoonPage } from '../pages/ComingSoonPage';
import { LoginPage } from '../pages/LoginPage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { TodayPage } from '../pages/TodayPage';
import { AppShell } from './AppShell';
import { RequireSignIn } from './RequireSignIn';

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireSignIn />,
    children: [
      {
        element: <AppShell />,
        children: [
          { index: true, element: <TodayPage /> },
          { path: 'library', element: <ComingSoonPage title={messages.tabs.library} /> },
          { path: 'add', element: <ComingSoonPage title={messages.tabs.add} /> },
          { path: 'stats', element: <ComingSoonPage title={messages.tabs.stats} /> },
          { path: 'settings', element: <ComingSoonPage title={messages.tabs.settings} /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
];
