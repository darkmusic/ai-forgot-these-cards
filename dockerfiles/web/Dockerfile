## Build the React/Vite SPA and serve it with Nginx
FROM node:24-slim AS build
WORKDIR /app

# Optional: Use an external Maven mirror (e.g., Nexus)
ARG NEXUS_APT_MIRROR_DEBIAN_BOOKWORM_URL
ARG NEXUS_APT_MIRROR_SECURITY_DEBIAN_BOOKWORM_URL

COPY dockerfiles/web/configure-apt-mirrors.sh /configure-apt-mirrors.sh
COPY dep/ai-forgot-this-frontend/package.json dep/ai-forgot-this-frontend/package-lock.json* ./dep/ai-forgot-this-frontend/
COPY dep/ai-forgot-this-frontend/ ./dep/ai-forgot-this-frontend/

WORKDIR /app/dep/ai-forgot-this-frontend

# Configure APT to use Nexus mirrors if provided
RUN sh /configure-apt-mirrors.sh

# Update and upgrade OS packages in the build stage to mitigate known vulnerabilities,
# then run the frontend build. Keep cache cleaned to keep the image small.
RUN apt-get update && apt-get upgrade -y && apt-get clean && rm -rf /var/lib/apt/lists/* \
	&& npm ci \
	&& npm run build:prod \
	&& ./node_modules/.bin/sass src/scss:src/css \
	&& mkdir -p /app/web \
	&& cp -r dist/* /app/web/

# The built assets are now at /app/web

# Use a Debian-based nginx image (non-Alpine) to avoid Alpine package vulnerabilities
FROM nginx:1.29.2

COPY dockerfiles/web/configure-apt-mirrors.sh /configure-apt-mirrors.sh

# Configure APT to use Nexus mirrors if provided
RUN export NEXUS_APT_MIRROR_ARCHIVE_UBUNTU_NOBLE_URL=$NEXUS_APT_MIRROR_ARCHIVE_UBUNTU_NOBLE_URL && \
    export NEXUS_APT_MIRROR_SECURITY_UBUNTY_NOBLE_URL=$NEXUS_APT_MIRROR_SECURITY_UBUNTU_NOBLE_URL && \
    sh /configure-apt-mirrors.sh

# Update packages in the final image to address known vulnerabilities.
# This upgrades available packages and cleans apt caches to keep the image small.
RUN apt-get update && apt-get upgrade -y && apt-get clean && rm -rf /var/lib/apt/lists/*

COPY dockerfiles/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/web /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
