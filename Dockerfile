FROM anapsix/alpine-java:8_server-jre_unlimited

RUN apk --no-cache upgrade
RUN apk add --no-cache tini paxctl && paxctl -c /opt/jdk/bin/* && paxctl -m /opt/jdk/bin/*

WORKDIR /opt/blog

COPY ./build/libs/uzi-vertx-shadow.jar /opt/blog/

# for hyper.sh comaptibility, specified -s
ENTRYPOINT ["/sbin/tini", "-s", "-g", "--"]

CMD ["/opt/jdk/bin/java", "-Xmx124M", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-XX:+UseG1GC", "-jar", "/opt/blog/uzi-vertx-shadow.jar"]

EXPOSE 8080 8443





