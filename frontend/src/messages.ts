// All UI text lives here (ADR-0032), so a translation can replace this one file later.
export const messages = {
  app: {
    name: 'Quode',
    tagline: 'Practice a little every day.',
  },
  home: {
    duckAlt: 'The Quode duck, wondering',
    serverChecking: 'Checking the server…',
    serverUp: 'Server is up',
    serverDown: 'Server is not reachable',
  },
  notFound: {
    title: 'Page not found',
    backHome: 'Back to Today',
  },
  errors: {
    reference: (ref: string) => `Something went wrong · ref ${ref}`,
  },
} as const;
