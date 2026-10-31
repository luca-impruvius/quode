import type { RouteObject } from 'react-router';
import { messages } from '../messages';
import { ComingSoonPage } from '../pages/ComingSoonPage';
import { LibraryPage } from '../pages/LibraryPage';
import { LoginPage } from '../pages/LoginPage';
import { MePage } from '../pages/MePage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { TodayPage } from '../pages/TodayPage';
import { TopicsPage } from '../pages/TopicsPage';
import { AppShell } from './AppShell';
import { RequireSignIn } from './RequireSignIn';

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireSignIn />,
    children: [
      // Sub-screens with their own header and no tab bar (as in the wireframes).
      { path: 'topics', element: <TopicsPage /> },
      {
        element: <AppShell />,
        children: [
          { index: true, element: <TodayPage /> },
          { path: 'library', element: <LibraryPage /> },
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
