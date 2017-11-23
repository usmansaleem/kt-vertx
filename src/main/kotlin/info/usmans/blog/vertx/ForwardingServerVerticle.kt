package info.usmans.blog.vertx

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future

class ForwardingServerVerticle(val redirectSSLPort: Int = 443) : AbstractVerticle() {
    override fun start(startFuture: Future<Void>?) {
        println("Deploying Http Server on port 8080 with redirect to ${redirectSSLPort}")

        vertx.createHttpServer().apply {
            requestHandler({ request ->
                request.redirectToSecure(redirectSSLPort)
            })
            listen(8080, {handler ->
                if(handler.succeeded()) {
                    startFuture?.complete()
                } else {
                    startFuture?.fail(handler.cause())
                }
            })
        }

    }
}