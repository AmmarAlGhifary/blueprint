package io.github.ammaralghifary.blueprint.defaults

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Base64
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Coil has no network support of its own: if the fetcher dependency goes missing, `image` renders nothing
// and no layout test notices. This loads through the same singleton loader AsyncImage uses.
/** @author Ammar Al Ghifary */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real image decoding
class DefaultsImageLoadingTest {
    // A 2x2 red PNG.
    private val png = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAEUlEQVR42mP4z8DwH4QZYAwAR8oH+Rq28akAAAAASUVORK5CYII=",
    )
    private val server = ServerSocket(0, 0, InetAddress.getLoopbackAddress())

    @After
    fun stopServer() = server.close()

    /** Answers one request with [status] and [body]. */
    private fun serveOnce(status: String, body: ByteArray) {
        thread(isDaemon = true) {
            server.accept().use { socket ->
                val request = socket.getInputStream().bufferedReader()
                while (!request.readLine().isNullOrEmpty()) Unit
                socket.getOutputStream().apply {
                    write("HTTP/1.1 $status\r\nContent-Type: image/png\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(body)
                    flush()
                }
            }
        }
    }

    private fun load(): ImageResult {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val url = "http://${server.inetAddress.hostAddress}:${server.localPort}/image.png"
        return runBlocking { SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(url).build()) }
    }

    @Test
    fun anHttpImageLoads() {
        serveOnce("200 OK", png)
        val result = load()
        assertTrue("expected the image to load, got ${(result as? ErrorResult)?.throwable}", result is SuccessResult)
    }

    @Test
    fun aMissingImageIsAnErrorResultNotACrash() {
        serveOnce("404 Not Found", ByteArray(0))
        assertTrue(load() is ErrorResult)
    }
}
