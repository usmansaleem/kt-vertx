package info.usmans.blog.vertx

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.net.NetServerOptions
import io.vertx.core.net.OpenSSLEngineOptions
import io.vertx.core.net.PemKeyCertOptions

/**
 * A secure TCP server serving on port 8888.
 */
class NetServerVerticle(val sslCertValue: String?, val sslKeyValue: String?) : AbstractVerticle() {
    private val clientPassword = System.getenv("BLOG_CUSTOM_NET_PASSWORD")

    override fun start(startFuture: Future<Void>?) {
        println("Starting NetServerVerticle...")
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
            port = 8888
            sslEngineOptions = OpenSSLEngineOptions()
        }

        val server = vertx.createNetServer(netServerOptions)

        server.connectHandler({ socket ->
            val clientsMap = vertx.sharedData().getLocalMap<String, String>("clientsMap")
            
            //we only want one authenticated client to be connected
            if (clientsMap.containsKey("client")) {
                println("A client is already connected, closing client socket")
                //disconnect socket
                socket.write("You are not the first one")
                socket.close()
            } else {
                val timerId = vertx.setTimer(5000, {
                    //test whether client has been added to map or not.
                    if (!clientsMap.containsKey("client") || !clientsMap.get("client").equals(socket.writeHandlerID())) {
                        //close this client ...
                        println("No message received from client on start up, closing socket. ${socket.writeHandlerID()}")
                        socket.write("No password received.")
                        socket.close();
                    }
                })

                socket.handler({ buffer ->
                    //we want to add the client once we receive secret password from it
                    if (clientPassword != null && buffer.toString() == clientPassword) {
                        println("Client connected, adding to map ${socket.writeHandlerID()}")
                        clientsMap.put("client", socket.writeHandlerID())
                    } else {
                        socket.write("Invalid password")
                        socket.close()
                    }
                    vertx.cancelTimer(timerId)
                })


            }

            val consumer = vertx.eventBus().consumer<String>("action-feed", { message ->
                println("sending out message on action-feed to ${socket.writeHandlerID()}")
                socket.write(message.body())
            })


            socket.closeHandler({
                println("Client Socket ${socket.writeHandlerID()} closed, clearing client map")
                clientsMap.removeIfPresent("client", socket.writeHandlerID())

                println("Unregistering eventbus")
                consumer.unregister()
            })
        })

        server.listen({ event ->
            if (event.succeeded()) {
                println("NetServerVerticle server started on port ${netServerOptions.port}")
                startFuture?.complete()
            } else {
                println("NetServerVerticle server failed to start on port ${netServerOptions.port}")
                startFuture?.fail(event.cause())
            }
        })
    }
}