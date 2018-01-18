package info.usmans.blog.vertx

import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.util.*

private val envLogger = LoggerFactory.getLogger("info.usmans.blog.Environment")

//environment variables used throughout this module

internal val ENV_BLOG_CERT_PATH = System.getenv("BLOG_CERT_PATH")
internal val ENV_BLOG_KEY_PATH = System.getenv("BLOG_KEY_PATH")
internal val ENV_CERT_BASE64 = System.getenv("BLOG_CERT_BASE64")
internal val ENV_BLOG_KEY_BASE64 = System.getenv("BLOG_KEY_BASE64")
internal val ENV_OAUTH_CLIENT_ID = System.getenv("OAUTH_CLIENT_ID")
internal val ENV_OAUTH_CLIENT_SECRET = System.getenv("OAUTH_CLIENT_SECRET")
internal val ENV_GITHUB_GIST_TOKEN = System.getenv("GITHUB_GIST_TOKEN")
internal val ENV_BLOG_DEPLOY_HTTP = System.getenv("BLOG_DEPLOY_HTTP")
internal val ENV_BLOG_CUSTOM_NET_PASSWORD = System.getenv("BLOG_CUSTOM_NET_PASSWORD")
internal val ENV_BLOG_ENABLE_OPENSSL = System.getenv("BLOG_ENABLE_OPENSSL")?.toBoolean() ?: false

//system properties used throughout this module
internal val SYS_BLOG_FALLBACK_REDIRECT_HOST = System.getProperty("fallbackRedirectDefaultHost") ?: "usmans.info"
internal val SYS_REDIRECT_SSL_PORT = System.getProperty("redirectSSLPort", "443").toIntOrNull() ?: 443
internal val SYS_DEPLOY_SSL_PORT = System.getProperty("deploySSLPort", "443").toIntOrNull() ?: 443
internal val SYS_DEPLOY_PORT = System.getProperty("deployPort", "80").toIntOrNull() ?: 80
internal val SYS_NET_SERVER_DEPLOY_PORT = System.getProperty("netServerDeployPort", "8888").toIntOrNull() ?: 8888

/**
 * Read PEM encoded SSL Certificate from path defined by BLOG_CERT_PATH environment variable. If BLOG_CERT_PATH is not
 * defined, read the contents of the certificate from BLOG_CERT_BASE64 which are base64 encoded without line breaks.
 *
 * Returns null if unable to read/decode the contents.
 *
 * Note: BLOG_CERT_BASE64 is a workaround for some docker hosting environment which cannot read multi-line environment
 * variables
 */
internal fun getSSLCertValue(): String? {
    val filePath = ENV_BLOG_CERT_PATH
    if (filePath != null) {
        //attempt to read contents from file
        try {
            return File(filePath).readText()
        } catch (e: FileNotFoundException) {
            envLogger.debug("Cert not found, continue with value of BLOG_CERT_BASE64")
        }
    }

    //attempt to read base64 encoded value ...
    val certValueEncoded = ENV_CERT_BASE64
    return if (certValueEncoded != null) try {
        Base64.getDecoder().decode(certValueEncoded).toString(StandardCharsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        envLogger.debug("Issues when decoding ENV_CERT_BASE64")
        null
    }
    else
        null
}

/**
 * Returns PEM encoded SSL Private Key from path defined by BLOG_KEY_PATH environment variable. If BLOG_KEY_PATH is not
 * defined, read the contents of the certificate from BLOG_KEY_BASE64 which are base64 encoded without line breaks.
 *
 * Returns null if unable to read/decode the contents.
 *
 * Note: BLOG_KEY_BASE64 is a workaround for some docker hosting environment which cannot read multi-line environment
 * variables
 */
internal fun getSSLKeyValue(): String? {
    val filePath = ENV_BLOG_KEY_PATH
    if (filePath != null) {
        //attempt to read contents from file
        try {
            return File(filePath).readText()
        } catch (e: FileNotFoundException) {
            envLogger.debug("Private key not found, continue with value of BLOG_KEY_BASE64")
        }
    }
    val keyValueEncoded = ENV_BLOG_KEY_BASE64
    return if (keyValueEncoded != null) try {
        Base64.getDecoder().decode(keyValueEncoded).toString(StandardCharsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        envLogger.debug("Issues when decoding ENV_BLOG_KEY_BASE64")
        null
    }
    else
        null
}

