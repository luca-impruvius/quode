-- The read-only debugging role (ADR-0027) must not read sessions: a session id is a sign-in.
-- Production creates the role before Flyway runs (deploy/initdb/10-readonly-role.sh) and grants it
-- SELECT on every new table; this takes the session tables back. Where the role doesn't exist (local
-- development, tests) this does nothing.
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'quode_readonly') THEN
        REVOKE ALL ON spring_session, spring_session_attributes FROM quode_readonly;
    END IF;
END
$$;
