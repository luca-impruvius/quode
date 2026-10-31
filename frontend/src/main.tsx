import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
// Self-hosted fonts (SIL OFL 1.1): browsers never contact Google, and they work offline.
import '@fontsource/bricolage-grotesque/700.css';
import '@fontsource/ibm-plex-sans/400.css';
import '@fontsource/ibm-plex-sans/600.css';
import '@fontsource/ibm-plex-sans/700.css';
import '@fontsource/ibm-plex-mono/400.css';
import './styles/index.css';
import { App } from './app/App';
import { applyTheme, readTheme } from './app/theme';

// index.html already did this before paint; repeated here in case that script was blocked.
applyTheme(readTheme());

const root = document.getElementById('root');
if (!root) {
  throw new Error('index.html has no #root element');
}

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
