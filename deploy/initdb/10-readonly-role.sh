#!/bin/bash
# Creates the read-only role for debugging (ADR-0027). The postgres image runs this once, when the
# data volume is empty. V5__readonly_role_no_session_access.sql then takes away the session tables.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v readonly_password="$QUODE_READONLY_PASSWORD" <<'SQL'
CREATE ROLE quode_readonly LOGIN PASSWORD :'readonly_password';
GRANT CONNECT ON DATABASE quode TO quode_readonly;
GRANT USAGE ON SCHEMA public TO quode_readonly;
-- Tables this owner creates later (Flyway, on the backend's first start) are readable too.
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO quode_readonly;
SQL
