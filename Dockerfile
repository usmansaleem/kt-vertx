FROM anapsix/alpine-java:8_jdk_unlimited

COPY . /opt/blog/build

WORKDIR /opt/blog/build

RUN apk --no-cache add tini paxctl && \
paxctl -c /opt/jdk/bin/* && paxctl -m /opt/jdk/bin/* || true && \
/opt/blog/build/gradlew --no-daemon shadowJar && \
cp /opt/blog/build/build/libs/uzi-vertx-shadow.jar /opt/blog/ && \
rm -rf /opt/blog/build/ && \
rm -rf /root/.gradle/ && \
rm -rf ./.gradle/

WORKDIR /opt/blog

# for hyper.sh comaptibility, specified -s
ENTRYPOINT ["/sbin/tini", "-s", "-g", "--"]

CMD ["/opt/jdk/bin/java", "-Xmx124M", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-XX:+UseG1GC", "-jar", "/opt/blog/uzi-vertx-shadow.jar"]

EXPOSE 8080 8443





