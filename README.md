# kt-vertx
Blogging Software by Usman Saleem - Kotlin and Vertx

It can be run directly in your IDE by creating a run configuration that uses the main class `io.vertx.core.Launcher`
and passes in the arguments `run info.usmans.blog.vertx.ServerVerticle`.

The build.gradle uses the Gradle shadowJar plugin to assemble the application and all it's dependencies into a single "fat" jar.

To build the "fat jar"

    ./gradlew shadowJar

To run the fat jar:

    java -jar build/libs/uzi-vertx-shadow.jar

(This jar can be run anywhere there is a Java 8+ JDK. It contains all the dependencies it needs so you don't need to 
install Vert.x on the target machine).
