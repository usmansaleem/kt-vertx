package info.usmans.blog.vertx

import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.nio.charset.StandardCharsets
import java.util.*

internal const val OAUTH_SITE = "https://uzi.au.auth0.com"
internal const val OAUTH_TOKEN_PATH = "/oauth/token"
internal const val OAUTH_AUTHZ_PATH = "/authorize"
internal const val OAUTH_USERINFO_PATH = "/userinfo"
internal const val GIST_REPO_URL = "https://gist.github.com/9bb0e98d05caa0afcc649b6593733edf.git"

fun gitCredentialProvider(gistToken: String = System.getenv("GITHUB_GIST_TOKEN")) = UsernamePasswordCredentialsProvider(gistToken, "")

/**
 * We are expecting Base64 single line encoded of PEM certificates
 */
fun getSSLCertValue(): String? {
    val certValueEncoded: String? = System.getenv("BLOG_CERT_BASE64")
    return if (certValueEncoded != null) try {
        Base64.getDecoder().decode(certValueEncoded).toString(StandardCharsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        null
    }
    else
        null
}

fun getSSLKeyValue(): String? {
    val keyValueEncoded: String? = System.getenv("BLOG_KEY_BASE64")
    return if (keyValueEncoded != null) try {
        Base64.getDecoder().decode(keyValueEncoded).toString(StandardCharsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        null
    }
    else
        null
}

fun getOAuthClientId() = System.getenv("OAUTH_CLIENT_ID")

fun getOAuthClientSecret() = System.getenv("OAUTH_CLIENT_SECRET")

fun getDataJsonUrl(tag: String): String =
        "https://cdn.rawgit.com/usmansaleem/9bb0e98d05caa0afcc649b6593733edf/raw/$tag/data.json"

fun deployUnsecureServer() = System.getenv("DEPLOY_UNSECURE_SERVER") != null
