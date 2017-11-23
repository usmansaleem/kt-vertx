package info.usmans.blog.vertx

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File


fun checkoutGist(gistUrl: String = GIST_REPO_URL): File {
    val tmpDir = createTempDir()
    Git.cloneRepository().
            setURI(gistUrl).
            setDirectory(tmpDir).
            call().use {
                println("Git cloned at: $tmpDir")
            }
    return tmpDir
}

fun commitGist(checkoutDir: File, msg:String="commit from jgit"): RevCommit {
    //open existing repo and commit and push data.json
    return Git.open(File(checkoutDir, ".git")).use {
        it.add().addFilepattern("data.json").call()
        it.commit().setMessage(msg).call()
    }
}

fun pushGist(checkoutDir: File, credentialProvider: UsernamePasswordCredentialsProvider = gitCredentialProvider()) {
    //open existing repo and commit and push data.json
    Git.open(File(checkoutDir, ".git")).use {
        it.push().setCredentialsProvider(credentialProvider).call()
    }
}