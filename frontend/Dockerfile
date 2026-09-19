# Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
# Project : scrm
# File : Dockerfile
# Date : 2026/09/18 00:00:00
# Author : Hsi Chu
# Contact : hiylo@live.com
#
# 前端运行镜像 (多阶段构建):
#   阶段 1 node   编译出 dist 静态资源
#   阶段 2 nginx  托管静态资源并反向代理 /scrm 与 /ws

# ---------- 阶段 1: 构建 ----------
FROM node:22-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------- 阶段 2: 运行 ----------
FROM nginx:1.27-alpine

RUN rm -f /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
