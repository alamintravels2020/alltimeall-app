package com.alltimeall.app

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object WebCacheInterceptor {

    private const val CACHE_DIR_NAME = "web_asset_cache"

    /**
     * Intercepts static web resources (.js, .css, images, fonts) and serves them from local disk cache.
     */
    fun interceptRequest(context: Context, urlString: String): WebResourceResponse? {
        val lowerUrl = urlString.lowercase()

        // Only cache static assets
        if (!isStaticAsset(lowerUrl)) return null

        try {
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val fileName = urlString.hashCode().toString() + getExtension(lowerUrl)
            val cachedFile = File(cacheDir, fileName)

            val mimeType = getMimeType(lowerUrl)

            if (cachedFile.exists() && cachedFile.length() > 0) {
                val inputStream: InputStream = FileInputStream(cachedFile)
                val response = WebResourceResponse(mimeType, "UTF-8", inputStream)
                response.responseHeaders = mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Cache-Control" to "max-age=31536000"
                )
                return response
            }

            // Asynchronously cache for next time
            Thread {
                try {
                    val url = URL(urlString)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.requestMethod = "GET"
                    conn.connect()

                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        conn.inputStream.use { input ->
                            FileOutputStream(cachedFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                } catch (_: Exception) {
                    if (cachedFile.exists()) cachedFile.delete()
                }
            }.start()

        } catch (_: Exception) {
            return null
        }

        return null
    }

    private fun isStaticAsset(url: String): Boolean {
        return url.contains("/assets/") ||
                url.endsWith(".js") ||
                url.endsWith(".css") ||
                url.endsWith(".png") ||
                url.endsWith(".jpg") ||
                url.endsWith(".jpeg") ||
                url.endsWith(".svg") ||
                url.endsWith(".woff2") ||
                url.endsWith(".ttf") ||
                url.contains("fonts.googleapis.com") ||
                url.contains("fonts.gstatic.com")
    }

    private fun getMimeType(url: String): String {
        return when {
            url.endsWith(".js") -> "application/javascript"
            url.endsWith(".css") -> "text/css"
            url.endsWith(".png") -> "image/png"
            url.endsWith(".jpg") || url.endsWith(".jpeg") -> "image/jpeg"
            url.endsWith(".svg") -> "image/svg+xml"
            url.endsWith(".woff2") -> "font/woff2"
            url.endsWith(".ttf") -> "font/ttf"
            else -> "text/html"
        }
    }

    private fun getExtension(url: String): String {
        val dotIndex = url.lastIndexOf('.')
        return if (dotIndex != -1 && dotIndex > url.lastIndexOf('/')) {
            url.substring(dotIndex)
        } else ""
    }
}
