#! /bin/sh

# If required, -e MEM_OPTS="-Xmx250m -XX:MaxRAM=256m" to docker run command
JAVA_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+UseSerialGC ${MEM_OPTS:-}"

echo "Running /opt/jdk/bin/java $JAVA_OPTS -jar /opt/blog/uzi-vertx-shadow.jar"

/opt/jdk/bin/java $JAVA_OPTS -jar /opt/blog/uzi-vertx-shadow.jar
