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
 
**GitHub gist Access**
The data.json is read from a private GitHub gist. The GitHub Personal Access Token (for gist scope) must be specified 
as environment variable 

    GITHUB_GIST_TOKEN=xxx
    
**Enabling Protected Page**
The /protected routes uses Auth0 OAuth service. Following environment variables are required to enable the access.

    OAUTH_CLIENT_ID=xxx
    OAUTH_CLIENT_SECRET=xxx          
    
**Launching SSL (Https) Server**
By default only https server is launched (on port 8443). Due to hosting environment restriction, the certificate
and unencrypted private key is required to be further encoded in base 64 with no wrapping.

Following command can be used to convert PKCS8 to PK8 format if required (Change the filename accordingly).

    openssl pkcs8 -topk8 -inform PEM -outform PEM -in domain.key -out domain.pk8.key -nocrypt
    
Following command can be used to export contents of certificate and private key into environment variables:    
    
    export BLOG_CERT_BASE64="$(cat domain.cer | base64 --wrap=0)"
    export BLOG_KEY_BASE64="$(cat domain.pk8.key | base64 --wrap=0)"    


If both http (8080) and https (8443) servers are required, following environment variable should be defined.

    DEPLOY_UNSECURE_SERVER
    
All the traffic to http (port 8080) will be redirected to https (to port 443 by default, 
unless publicSSLPort system property is specified). The publicSSLPort can be used in development to forward requests
to 8443 instead of default 443.

**Docker build**
The docker build can be used to compile the project ready to run:

     docker build -t <sometag> .
     
To run the docker image, following command can be used.

    docker run -itd -e GITHUB_GIST_TOKEN -e OAUTH_CLIENT_ID -e OAUTH_CLIENT_SECRET -e BLOG_CERT_BASE64 -e BLOG_KEY_BASE64 -p 8443:8443 -p 8080:8080 <sometag>
	
To limit the JVM memory parameters (specially required in container environment) additional (optional) environment variable MEM_OPTS can be passed
	
    docker run -itd -e MEM_OPTS="-Xmx120m -XX:MaxRAM=128m" ...