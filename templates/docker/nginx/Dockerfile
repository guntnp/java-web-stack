# Build context is public/, so the image carries the site and also runs on its own.
# The nginx config is mounted by the compose files (default.conf locally, prod.conf on the server).
FROM nginx:stable-alpine
COPY . /usr/share/nginx/html
