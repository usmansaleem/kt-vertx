package info.usmans.blog.vertx

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future

class ForwardingServerVerticle(val redirectSSLPort: Int = 443) : AbstractVerticle() {
    override fun start(startFuture: Future<Void>?) {
        println("Deploying Http Server on port 8080 with redirect to ${redirectSSLPort}")

        vertx.createHttpServer().apply {
            requestHandler({ request ->
                request.redirectToHttps(redirectSSLPort)
            })
            listen(8080, {handler ->
                if(handler.succeeded()) {
                    println("Http Server on port 8080 deployed")
                    startFuture?.complete()
                } else {
                    println("Http Server on port 8080 failed to deploy")
                    startFuture?.fail(handler.cause())
                }
            })
        }

    }
}