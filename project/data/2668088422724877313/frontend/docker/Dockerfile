FROM rddhub.changhong.com/library/nginx-unprivileged:stable-alpine

LABEL maintainer="seiadmin@changhong.com"

ENV APP_NAME="2668088422724877313-web"

COPY dist /usr/share/nginx/html/$APP_NAME

COPY default.conf /etc/nginx/conf.d/default.conf
