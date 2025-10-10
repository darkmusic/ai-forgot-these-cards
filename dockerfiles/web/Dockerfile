## Build the React/Vite SPA and serve it with Nginx
FROM node:23.6.1-alpine AS build
WORKDIR /app
COPY dep/ai-forgot-this-frontend/package.json dep/ai-forgot-this-frontend/package-lock.json* ./dep/ai-forgot-this-frontend/
COPY dep/ai-forgot-this-frontend/ ./dep/ai-forgot-this-frontend/

WORKDIR /app/dep/ai-forgot-this-frontend
RUN npm ci \
	&& npm run build:prod \
	&& ./node_modules/.bin/sass src/scss:src/css || true \
	&& mkdir -p /app/web \
	&& cp -r dist/* /app/web/

# The built assets are now at /app/web

FROM nginx:1.27-alpine
COPY dockerfiles/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/web /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
