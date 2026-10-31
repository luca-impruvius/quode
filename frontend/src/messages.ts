// All UI text lives here (ADR-0032), so a translation can replace this one file later.
export const messages = {
  app: {
    name: 'Quode',
    tagline: 'Practice a little every day.',
  },
  login: {
    duckAlt: 'The Quode duck, wondering',
    continueWithGoogle: 'Continue with Google',
    noAccess: 'This account has no access.',
    failed: "Sign-in didn't finish. Please try again.",
  },
  today: {
    title: 'Today',
    greeting: (firstName: string) => `Hello, ${firstName}`,
    duckAlt: 'The Quode duck',
  },
  tabs: {
    label: 'Main',
    today: 'Today',
    library: 'Library',
    add: 'Add question',
    stats: 'Stats',
    settings: 'Settings',
  },
  comingSoon: 'Coming soon',
  loading: 'Loading…',
  notFound: {
    title: 'Page not found',
    backHome: 'Back to Today',
  },
  errors: {
    reference: (ref: string) => `Something went wrong · ref ${ref}`,
    generic: 'Something went wrong. Please try again.',
  },
} as const;
