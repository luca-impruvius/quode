import type { RouteObject } from 'react-router';
import { messages } from '../messages';
import { ComingSoonPage } from '../pages/ComingSoonPage';
import { LibraryPage } from '../pages/LibraryPage';
import { LoginPage } from '../pages/LoginPage';
import { MePage } from '../pages/MePage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { TodayPage } from '../pages/TodayPage';
import { AppShell } from './AppShell';
import { RequireSignIn } from './RequireSignIn';

// The sub-screens load on first use (React Router lazy routes), keeping the start-up bundle small.
const editor = () => import('../pages/EditorPage').then((m) => ({ Component: m.EditorPage }));

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireSignIn />,
    children: [
      // Sub-screens with their own header and no tab bar (as in the wireframes).
      {
        path: 'topics',
        lazy: () => import('../pages/TopicsPage').then((m) => ({ Component: m.TopicsPage })),
      },
      { path: 'add', lazy: editor },
      {
        path: 'questions/:id',
        lazy: () =>
          import('../pages/QuestionDetailPage').then((m) => ({ Component: m.QuestionDetailPage })),
      },
      { path: 'questions/:id/edit', lazy: editor },
      {
        element: <AppShell />,
        children: [
          { index: true, element: <TodayPage /> },
          { path: 'library', element: <LibraryPage /> },
          { path: 'quizzes', element: <ComingSoonPage title={messages.tabs.quizzes} /> },
          { path: 'me', element: <MePage /> },
          { path: 'stats', element: <ComingSoonPage title={messages.me.stats} /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
];
