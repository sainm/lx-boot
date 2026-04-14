package org.sainm.psy.export.service

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class ExportArtifactStorageTest {

    @Test
    fun `local path storage stores artifact using absolute path`() {
        val baseDir = Files.createTempDirectory("export-local-storage")
        val storage = LocalPathExportArtifactStorage(baseDir.toString())
        val bytes = "local".toByteArray()

        val location = storage.store("job-1", "report.pdf", bytes)

        assertTrue(Path.of(location).isAbsolute)
        assertArrayEquals(bytes, storage.read(location))
        storage.delete(location)
        assertNull(storage.read(location))
    }

    @Test
    fun `keyed path storage stores artifact using opaque key`() {
        val baseDir = Files.createTempDirectory("export-keyed-storage")
        val storage = KeyedPathExportArtifactStorage(baseDir.toString(), "exports/reports")
        val bytes = "keyed".toByteArray()

        val location = storage.store("job-2", "report.txt", bytes)

        assertTrue(location.startsWith("exports/reports/"))
        assertArrayEquals(bytes, storage.read(location))
        assertNotNull(Files.list(baseDir).findFirst().orElse(null))
        storage.delete(location)
        assertNull(storage.read(location))
    }

    @Test
    fun `s3 uri compatible storage stores artifact using bucket uri`() {
        val baseDir = Files.createTempDirectory("export-s3-storage")
        val storage = S3UriCompatibleExportArtifactStorage(
            storageDir = baseDir.toString(),
            bucket = "psy-exports",
            keyPrefix = "reports/async"
        )
        val bytes = "object".toByteArray()

        val location = storage.store("job-3", "report.pdf", bytes)

        assertTrue(location.startsWith("s3://psy-exports/reports/async/"))
        assertArrayEquals(bytes, storage.read(location))
        storage.delete(location)
        assertNull(storage.read(location))
    }

    @Test
    fun `s3 uri compatible storage ignores foreign bucket uri`() {
        val baseDir = Files.createTempDirectory("export-s3-storage-foreign")
        val storage = S3UriCompatibleExportArtifactStorage(
            storageDir = baseDir.toString(),
            bucket = "psy-exports",
            keyPrefix = "reports/async"
        )

        assertNull(storage.read("s3://other-bucket/reports/async/job-4.pdf"))
        storage.delete("s3://other-bucket/reports/async/job-4.pdf")
        assertFalse(Files.list(baseDir).findAny().isPresent)
    }

    @Test
    fun `http object storage stores and reads artifact through gateway`() {
        val stored = ConcurrentHashMap<String, ByteArray>()
        val server = startObjectStorageServer(stored, requiredApiKey = "secret-token")
        try {
            val storage = HttpObjectStorageExportArtifactStorage(
                endpointUrl = "http://127.0.0.1:${server.address.port}/objects",
                bucket = "psy-exports",
                keyPrefix = "reports/async",
                apiKey = "secret-token"
            )
            val bytes = "http-object".toByteArray()

            val location = storage.store("job-5", "report.pdf", bytes)

            assertTrue(location.startsWith("s3://psy-exports/reports/async/"))
            assertArrayEquals(bytes, storage.read(location))
            storage.delete(location)
            assertNull(storage.read(location))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `http object storage ignores foreign bucket uri`() {
        val stored = ConcurrentHashMap<String, ByteArray>()
        val server = startObjectStorageServer(stored)
        try {
            val storage = HttpObjectStorageExportArtifactStorage(
                endpointUrl = "http://127.0.0.1:${server.address.port}/objects",
                bucket = "psy-exports",
                keyPrefix = "reports/async"
            )

            assertNull(storage.read("s3://other-bucket/reports/async/job-6.pdf"))
            storage.delete("s3://other-bucket/reports/async/job-6.pdf")
            assertTrue(stored.isEmpty())
        } finally {
            server.stop(0)
        }
    }

    private fun startObjectStorageServer(
        stored: MutableMap<String, ByteArray>,
        requiredApiKey: String? = null
    ): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/objects") { exchange ->
            handleObjectStorageExchange(exchange, stored, requiredApiKey)
        }
        server.start()
        return server
    }

    private fun handleObjectStorageExchange(
        exchange: HttpExchange,
        stored: MutableMap<String, ByteArray>,
        requiredApiKey: String?
    ) {
        exchange.use {
            if (requiredApiKey != null && exchange.requestHeaders.getFirst("X-Api-Key") != requiredApiKey) {
                send(exchange, 401)
                return
            }
            val key = exchange.requestURI.path.removePrefix("/objects/").trimStart('/')
            when (exchange.requestMethod) {
                "PUT" -> {
                    stored[key] = exchange.requestBody.readAllBytes()
                    send(exchange, 200)
                }
                "GET" -> {
                    val bytes = stored[key]
                    if (bytes == null) {
                        send(exchange, 404)
                    } else {
                        send(exchange, 200, bytes)
                    }
                }
                "DELETE" -> {
                    stored.remove(key)
                    send(exchange, 204)
                }
                else -> send(exchange, 405)
            }
        }
    }

    private fun send(exchange: HttpExchange, status: Int, bytes: ByteArray = ByteArray(0)) {
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { output ->
            output.write(bytes)
        }
    }
}
