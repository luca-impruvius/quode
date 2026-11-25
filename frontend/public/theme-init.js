// The theme chosen on Me, applied before first paint so there's no flash of the other theme.
// Same key and values as src/app/theme.ts. A file, not an inline script, so the production
// Content-Security-Policy can stay at script-src 'self' (deploy/Caddyfile).
try {
  var theme = localStorage.getItem('quode.theme');
  if (theme === 'light' || theme === 'dark') {
    document.documentElement.dataset.theme = theme;
  }
} catch (e) {}
