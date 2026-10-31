import type { RouteObject } from 'react-router';
import { messages } from '../messages';
import { ComingSoonPage } from '../pages/ComingSoonPage';
import { LoginPage } from '../pages/LoginPage';
import { MePage } from '../pages/MePage';
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
          { path: 'quizzes', element: <ComingSoonPage title={messages.tabs.quizzes} /> },
          { path: 'me', element: <MePage /> },
          { path: 'stats', element: <ComingSoonPage title={messages.me.stats} /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
];
