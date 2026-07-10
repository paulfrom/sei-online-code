FROM rddhub.changhong.com/library/nginx:stable-alpine

LABEL maintainer="seiadmin@changhong.com"

ENV APP_NAME="2668088422724877313-web"

COPY nginx.conf /etc/nginx/nginx.conf

COPY dist /usr/share/nginx/html/$APP_NAME
