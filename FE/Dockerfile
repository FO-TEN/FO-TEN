# 1단계: 빌드
FROM node:20-alpine AS build
WORKDIR /app
# package*.json 만 먼저 복사해 의존성 레이어 캐시를 살린다.
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Vite 는 빌드 시점에 환경변수를 번들에 굽는다. 런타임 주입이 아니므로 여기서 받아야 한다.
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# 2단계: 정적 파일 서빙 + /api 프록시
FROM nginx:1.27-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
