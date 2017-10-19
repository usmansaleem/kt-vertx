package info.usmans.blog.vertx

import io.vertx.core.AbstractVerticle

class ForwardingServerVerticle(val redirectSSLPort: Int = 443) : AbstractVerticle() {
    override fun start() {
        println("Deploying Http Server on port 8080 with redirect to ${redirectSSLPort}")

        vertx.createHttpServer().requestHandler({ request ->
           request.redirectToHttps(redirectSSLPort)
        }).listen(8080)

    }
}