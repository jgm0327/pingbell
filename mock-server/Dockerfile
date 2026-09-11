# syntax=docker/dockerfile:1
# Zero dependencies, so no install step is needed — just the Node runtime.
FROM node:22-alpine
WORKDIR /app
COPY server.js ./
EXPOSE 4000
CMD ["node", "server.js"]
