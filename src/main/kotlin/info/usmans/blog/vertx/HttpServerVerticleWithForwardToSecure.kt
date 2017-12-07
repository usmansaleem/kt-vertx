package info.usmans.blog.vertx

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import org.slf4j.LoggerFactory

class HttpServerVerticleWithForwardToSecure : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger("info.usmans.blog.vertx.HttpServerVerticleWithForwardToSecure")

    override fun start(startFuture: Future<Void>?) {
        logger.info("Deploying Http Server on port {} with redirect to {}", SYS_DEPLOY_PORT, SYS_REDIRECT_SSL_PORT)

        val httpServer = vertx.createHttpServer()

        httpServer.requestHandler({ request ->
            request.redirectToSecure()
        })

        httpServer.listen(SYS_DEPLOY_PORT, {handler ->
            if(handler.succeeded()) {
                startFuture?.complete()
            } else {
                startFuture?.fail(handler.cause())
            }
        })
    }
}