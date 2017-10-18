FROM anapsix/alpine-java:8_server-jre_unlimited

COPY build/libs/uzi-vertx-shadow.jar /opt/blog/

WORKDIR /opt/blog

RUN apk --no-cache add tini paxctl && \
paxctl -c /opt/jdk/bin/java && \
paxctl -m /opt/jdk/bin/java

# for hyper.sh comaptibility, specified -s
ENTRYPOINT ["/sbin/tini", "-s", "-g", "--"]

CMD ["/opt/jdk/bin/java", "-XX:+UnlockExperimentalVMOptions","-XX:+UseG1GC", "-jar", "/opt/blog/uzi-vertx-shadow.jar"]

EXPOSE 8080 8443





