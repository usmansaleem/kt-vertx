package info.usmans.blog.vertx

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future

class HttpServerVerticleWithForwardToSecure(val deployPort: Int = 80, val redirectSSLPort: Int = 443) : AbstractVerticle() {
    override fun start(startFuture: Future<Void>?) {
        println("Deploying Http Server on port ${deployPort} with redirect to ${redirectSSLPort}")

        vertx.createHttpServer().apply {
            requestHandler({ request ->
                request.redirectToSecure(redirectSSLPort)
            })
            listen(deployPort, {handler ->
                if(handler.succeeded()) {
                    startFuture?.complete()
                } else {
                    startFuture?.fail(handler.cause())
                }
            })
        }

    }
}