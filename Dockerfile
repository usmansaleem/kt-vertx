FROM anapsix/alpine-java:8_server-jre_unlimited

COPY entrypoint.sh build/libs/uzi-vertx-shadow.jar /opt/blog/

WORKDIR /opt/blog

RUN apk --no-cache add tini paxctl && \
paxctl -c /opt/jdk/bin/java && \
paxctl -m /opt/jdk/bin/java && \
chmod 777 /opt/blog/entrypoint.sh

# for hyper.sh comaptibility, specified -s
ENTRYPOINT ["/sbin/tini", "-s", "-g", "--"]

CMD ["/opt/blog/entrypoint.sh"]

EXPOSE 8080 8443





