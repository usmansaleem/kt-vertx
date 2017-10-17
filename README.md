# kt-vertx
Blogging Software built using Kotlin and Vertx.

**Author: Usman Saleem**

**IDE Run Instructions**
The project can be run directly in IDE by creating a run configuration that uses the main class `io.vertx.core.Launcher`
and passes in the arguments `run info.usmans.blog.vertx.ServerVerticle`.

**Gradle Build Instructions**
The build.gradle uses the Gradle shadowJar plugin to assemble the application and all it's dependencies into a single "fat" jar.

To build the "fat jar"

    ./gradlew shadowJar

To run the fat jar:

    java -jar build/libs/uzi-vertx-shadow.jar
    
(This jar can be run anywhere there is a Java 8+ JDK. It contains all the dependencies it needs hence no need to maintain
 any libraries on the target machine).    
    
**Launching SSL (Https) Server**
Https server (on port 8443) can be launched by specifying keyValue and certValue environment variables which contain public key
in PKCS8 format and unencrypted private key in PK8 format.

Following command can be used to convert PKCS8 to PK8 format if required (Change the filename accordingly).

    openssl pkcs8 -topk8 -inform PEM -outform PEM -in domain.key -out domain.pk8.key -nocrypt
    
Following command can be used to export contents of certificate and private key into environment variables:    
    
    export BLOG_CERT_BASE64="$(cat domain.cer | base64 --wrap=0)"
    export BLOG_KEY_BASE64="$(cat domain.pk8.key | base64 --wrap=0)"    


The above can also be passed to docker run command with -e certValue and -e keyValue     

_If SSL Http server is launched, all the traffic to http (port 8080) will be redirected to https (to port 443 by default, 
unless redirectSSLPort system property is specified). The redirectPort can be used in development, for instance_

**Docker build**
The docker build can be used to compile the project ready to run:

     docker build -t <sometag> .
     
To run the docker image (both non-SSL and SSL), following command can be used

    docker run -itd -e BLOG_CERT_BASE64 -e BLOG_KEY_BASE64 -p 8443:8443 -p 8080:8080 <sometag>
