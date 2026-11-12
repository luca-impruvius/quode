import { z } from 'zod';

// Import Zod from here, not from 'zod', so this runs before any schema is built. Without it, Zod
// probes for eval with `new Function`. The production Content-Security-Policy (script-src 'self',
// deploy/Caddyfile) blocks that probe, and the browser reports a CSP violation on every page with a
// form. Jitless validation is a little slower, which doesn't matter at our form sizes.
z.config({ jitless: true });

export { z };
