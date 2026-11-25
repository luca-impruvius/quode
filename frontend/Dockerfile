# syntax=docker/dockerfile:1
# Frontend image (M0-10): Caddy serving the built React app. Build from the repository root, because
# the theme is generated from ../design-system: docker build -f frontend/Dockerfile .
# The Caddyfile is not baked in: deploy/compose.yml mounts deploy/Caddyfile.

FROM node:26-alpine AS build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY design-system /workspace/design-system
COPY frontend ./
RUN npm run build

FROM caddy:2-alpine
COPY --from=build /workspace/frontend/dist /srv
