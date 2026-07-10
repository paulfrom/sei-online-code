FROM rddhub.changhong.com/library/dragonwell:21.0.5.0.5-bookworm

LABEL maintainer="seiadmin@changhong.com"

ARG user=app
ARG group=app
ARG uid=1000
ARG gid=1000

RUN mkdir /usr/app; \
    chown ${uid}:${gid} /usr/app; \
    groupadd -g ${gid} ${group}; \
    useradd -m -u ${uid} -g ${gid} -s /usr/sbin/nologin ${user}

ENV LANG C.UTF-8
ENV APP_NAME="2668088422724877313"

# 默认GC参数，可定义变量覆盖，如: -XX:+UseG1GC
ENV JAVA_OPTS="-XX:+UseZGC"

# 默认的容器参数，可定义变量覆盖
ENV JAVA_JVM_OPTS="-XX:+UseContainerSupport -XX:InitialRAMPercentage=70.0 -XX:MaxRAMPercentage=70.0"

COPY $APP_NAME-service/build/libs/$APP_NAME.jar /usr/app/$APP_NAME.jar

USER ${user}

EXPOSE 8080

WORKDIR /usr/app

ENTRYPOINT ["/usr/bin/tini","--"]

CMD ["/bin/bash","-c","java $JAVA_OPTS $JAVA_JVM_OPTS -Duser.timezone=Asia/Shanghai -Dserver.port=8080 -jar $APP_NAME.jar"]