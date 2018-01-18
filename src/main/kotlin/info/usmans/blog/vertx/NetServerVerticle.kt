package info.usmans.blog.vertx

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.net.NetServerOptions
import io.vertx.core.net.OpenSSLEngineOptions
import io.vertx.core.net.PemKeyCertOptions
import org.slf4j.LoggerFactory

/**
 * A secure TCP server serving on port 8888.
 */
class NetServerVerticle(private val sslCertValue: String?, private val sslKeyValue: String?) : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger("info.usmans.blog.vertx.NetServerVerticle")

    override fun start(startFuture: Future<Void>?) {
        logger.info("Starting NetServerVerticle...")
        if (sslCertValue == null || sslKeyValue == null) {
            startFuture?.fail("SSL Certificates are required to start Net Server")
            return
        }

        val netServerOptions = NetServerOptions().apply {
            isSsl = true
            pemKeyCertOptions = PemKeyCertOptions().apply {
                certValue = Buffer.buffer(sslCertValue)
                keyValue = Buffer.buffer(sslKeyValue)
            }
            port = SYS_NET_SERVER_DEPLOY_PORT
            addEnabledSecureTransportProtocol("TLSv1.1")
            addEnabledSecureTransportProtocol("TLSv1.2")
            if(ENV_BLOG_ENABLE_OPENSSL)
                sslEngineOptions = OpenSSLEngineOptions()
        }

        val server = vertx.createNetServer(netServerOptions)

        server.exceptionHandler { t ->
            logger.error("Unexpected exception in NetServer: ${t.message}", t)
        }

        server.connectHandler({ socket ->
            val clientsMap = vertx.sharedData().getLocalMap<String, String>("clientsMap")

            //the one-shot timer to wait for client to send password.
            val timerId = vertx.setTimer(5000, {
                //test whether client has been added to map or not.
                if (socket.writeHandlerID() != clientsMap.get("client")) {
                    //close this client ...
                    logger.info("No message received from client in 5 seconds, closing socket. ${socket.writeHandlerID()}")
                    socket.close()
                }
            })

            //we only want one authenticated client to be connected
            if (clientsMap.containsKey("client")) {
                logger.info("A client is already connected, closing client socket")

                vertx.cancelTimer(timerId)

                //close client socket
                socket.close()
            } else {
                socket.handler({ buffer ->
                    //client send message, timer is not required anymore
                    vertx.cancelTimer(timerId)

                    //we want to add the client once we receive secret password from it
                    if (ENV_BLOG_CUSTOM_NET_PASSWORD != null && buffer.toString() == ENV_BLOG_CUSTOM_NET_PASSWORD) {
                        logger.info("Client connected, adding to map ${socket.writeHandlerID()}")
                        clientsMap.put("client", socket.writeHandlerID())

                        //we are good to send messages now.
                    } else {
                        logger.info("Invalid password $buffer received, closing socket")
                        socket.close()
                    }
                })
            }

            val consumer = vertx.eventBus().consumer<String>("action-feed", { message ->
                if(socket.writeHandlerID() == clientsMap.get("client")) {
                    logger.info("sending out message on action-feed to ${socket.writeHandlerID()}")
                    socket.write(message.body())
                }
            })


            socket.closeHandler({
                //in case client closed socket before sending us message
                vertx.cancelTimer(timerId)

                logger.info("Client Socket ${socket.writeHandlerID()} closed, clearing client map")
                clientsMap.removeIfPresent("client", socket.writeHandlerID())

                logger.debug("Unregistering eventbus")
                consumer.unregister()
            })
        })

        server.listen({ event ->
            if (event.succeeded()) {
                logger.info("NetServerVerticle server started on port ${netServerOptions.port}")
                startFuture?.complete()
            } else {
                logger.info("NetServerVerticle server failed to start on port ${netServerOptions.port}")
                startFuture?.fail(event.cause())
            }
        })
    }
}