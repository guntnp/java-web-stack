# Shared site rules, included inside server {} by default.conf (local) and prod.conf (server).
# The server that includes this must set $proto (the scheme the visitor used) before the include.

server_tokens off;
root /usr/share/nginx/html;
client_max_body_size 1m;

# Security headers on every response. Careful: nginx drops ALL of these in a location that has its own
# add_header, so never use add_header inside a location below (use "expires" for caching instead).
add_header X-Content-Type-Options nosniff always;
add_header X-Frame-Options SAMEORIGIN always;
add_header Referrer-Policy strict-origin-when-cross-origin always;
add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;
# Scripts, styles, images and fetches only from this site and nothing inline: keep JS/CSS in files.
add_header Content-Security-Policy "default-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'self'" always;

location /api/ {
    limit_req zone=api burst=50 nodelay;
    limit_req zone=auth_write burst=5 nodelay;
    limit_req_status 429;

    # Resolve "app" per request through Docker's DNS, so nginx starts without app
    # and keeps working after app restarts with a new IP (a fixed proxy_pass host is resolved once).
    resolver 127.0.0.11 valid=10s ipv6=off;
    set $app_upstream http://app:8080;
    proxy_pass $app_upstream;

    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $proto;
    proxy_connect_timeout 5s;
    proxy_read_timeout 30s;
}

location ~* \.(css|js|png|jpg|jpeg|webp|svg|ico|woff2)$ {
    expires 7d;
}

location / {
    try_files $uri $uri.html $uri/ =404;
    expires -1;
}
