package org.sainm.psy.export.service

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.charset.StandardCharsets

interface ExportArtifactStorage {
    fun store(jobId: String, fileName: String, bytes: ByteArray): String

    fun read(location: String?): ByteArray?

    fun delete(location: String?)
}

enum class ExportArtifactStorageMode {
    LOCAL_PATH,
    KEYED_PATH,
    S3_URI_COMPAT,
    HTTP_OBJECT_STORAGE
}

@Component
@ConfigurationProperties(prefix = "psy.export.jobs.artifact-storage")
data class ExportArtifactStorageProperties(
    var mode: ExportArtifactStorageMode = ExportArtifactStorageMode.LOCAL_PATH,
    var baseDir: String = "",
    var keyPrefix: String = "export-artifacts",
    var bucket: String = "psy-export-artifacts",
    var endpointUrl: String = "",
    var apiKeyHeader: String = "X-Api-Key",
    var apiKey: String = "",
    var connectTimeoutMillis: Long = 5000,
    var requestTimeoutMillis: Long = 30000
)

@Component
class ConfiguredExportArtifactStorage(
    private val properties: ExportArtifactStorageProperties
) : ExportArtifactStorage {

    private val delegate: ExportArtifactStorage
        get() = when (properties.mode) {
            ExportArtifactStorageMode.LOCAL_PATH -> LocalPathExportArtifactStorage(properties.baseDir)
            ExportArtifactStorageMode.KEYED_PATH -> KeyedPathExportArtifactStorage(properties.baseDir, properties.keyPrefix)
            ExportArtifactStorageMode.S3_URI_COMPAT -> S3UriCompatibleExportArtifactStorage(
                storageDir = properties.baseDir,
                bucket = properties.bucket,
                keyPrefix = properties.keyPrefix
            )
            ExportArtifactStorageMode.HTTP_OBJECT_STORAGE -> HttpObjectStorageExportArtifactStorage(
                endpointUrl = properties.endpointUrl,
                bucket = properties.bucket,
                keyPrefix = properties.keyPrefix,
                apiKeyHeader = properties.apiKeyHeader,
                apiKey = properties.apiKey,
                connectTimeoutMillis = properties.connectTimeoutMillis,
                requestTimeoutMillis = properties.requestTimeoutMillis
            )
        }

    override fun store(jobId: String, fileName: String, bytes: ByteArray): String =
        delegate.store(jobId, fileName, bytes)

    override fun read(location: String?): ByteArray? =
        delegate.read(location)

    override fun delete(location: String?) {
        delegate.delete(location)
    }
}

class LocalPathExportArtifactStorage(
    private val storageDir: String = ""
) : ExportArtifactStorage {

    override fun store(jobId: String, fileName: String, bytes: ByteArray): String {
        val directory = storageDirectory()
        Files.createDirectories(directory)
        val safeName = buildSafeFileName(jobId, fileName)
        val path = directory.resolve(safeName).normalize()
        require(path.startsWith(directory)) { "invalid export storage path" }
        Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
        return path.toString()
    }

    override fun read(location: String?): ByteArray? {
        if (location.isNullOrBlank()) {
            return null
        }
        return readBytes(Path.of(location))
    }

    override fun delete(location: String?) {
        if (location.isNullOrBlank()) {
            return
        }
        runCatching { Files.deleteIfExists(Path.of(location)) }
    }

    private fun storageDirectory(): Path =
        resolveBaseDirectory(storageDir)
}

class KeyedPathExportArtifactStorage(
    private val storageDir: String = "",
    private val keyPrefix: String = "export-artifacts"
) : ExportArtifactStorage {

    override fun store(jobId: String, fileName: String, bytes: ByteArray): String {
        val key = buildStorageKey(jobId, fileName)
        val path = resolvePath(key)
        Files.createDirectories(path.parent)
        Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
        return key
    }

    override fun read(location: String?): ByteArray? {
        if (location.isNullOrBlank()) {
            return null
        }
        return readBytes(resolvePath(location))
    }

    override fun delete(location: String?) {
        if (location.isNullOrBlank()) {
            return
        }
        runCatching { Files.deleteIfExists(resolvePath(location)) }
    }

    private fun buildStorageKey(jobId: String, fileName: String): String {
        val prefix = keyPrefix.trim('/').ifBlank { "export-artifacts" }
        return "$prefix/${buildSafeFileName(jobId, fileName)}"
    }

    private fun resolvePath(location: String): Path {
        val normalizedKey = location.replace('\\', '/').trimStart('/')
        val baseDir = resolveBaseDirectory(storageDir)
        val path = baseDir.resolve(normalizedKey).normalize()
        require(path.startsWith(baseDir)) { "invalid export storage location" }
        return path
    }
}

class S3UriCompatibleExportArtifactStorage(
    private val storageDir: String = "",
    private val bucket: String = "psy-export-artifacts",
    private val keyPrefix: String = "export-artifacts"
) : ExportArtifactStorage {

    override fun store(jobId: String, fileName: String, bytes: ByteArray): String {
        val key = buildObjectKey(jobId, fileName)
        val path = resolvePath(key)
        Files.createDirectories(path.parent)
        Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
        return "s3://${normalizedBucket()}/$key"
    }

    override fun read(location: String?): ByteArray? {
        if (location.isNullOrBlank()) {
            return null
        }
        val key = parseLocation(location) ?: return null
        return readBytes(resolvePath(key))
    }

    override fun delete(location: String?) {
        if (location.isNullOrBlank()) {
            return
        }
        val key = parseLocation(location) ?: return
        runCatching { Files.deleteIfExists(resolvePath(key)) }
    }

    private fun parseLocation(location: String): String? {
        val prefix = "s3://${normalizedBucket()}/"
        if (!location.startsWith(prefix)) {
            return null
        }
        return location.removePrefix(prefix).trimStart('/').takeIf { it.isNotBlank() }
    }

    private fun buildObjectKey(jobId: String, fileName: String): String {
        val prefix = keyPrefix.trim('/').ifBlank { "export-artifacts" }
        return "$prefix/${buildSafeFileName(jobId, fileName)}"
    }

    private fun resolvePath(key: String): Path {
        val normalizedKey = key.replace('\\', '/').trimStart('/')
        val baseDir = resolveBaseDirectory(storageDir).resolve(normalizedBucket()).normalize()
        val path = baseDir.resolve(normalizedKey).normalize()
        require(path.startsWith(baseDir)) { "invalid export object key" }
        return path
    }

    private fun normalizedBucket(): String =
        bucket.trim().ifBlank { "psy-export-artifacts" }
}

class HttpObjectStorageExportArtifactStorage(
    private val endpointUrl: String,
    private val bucket: String = "psy-export-artifacts",
    private val keyPrefix: String = "export-artifacts",
    private val apiKeyHeader: String = "X-Api-Key",
    private val apiKey: String = "",
    connectTimeoutMillis: Long = 5000,
    private val requestTimeoutMillis: Long = 30000
) : ExportArtifactStorage {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofMillis(connectTimeoutMillis.coerceAtLeast(1000)))
        .build()

    override fun store(jobId: String, fileName: String, bytes: ByteArray): String {
        val key = buildObjectKey(jobId, fileName)
        val location = "s3://${normalizedBucket()}/$key"
        val response = httpClient.send(
            requestBuilder(key)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build(),
            HttpResponse.BodyHandlers.discarding()
        )
        require(response.statusCode() in 200..299) {
            "http object storage store failed: ${response.statusCode()}"
        }
        return location
    }

    override fun read(location: String?): ByteArray? {
        if (location.isNullOrBlank()) {
            return null
        }
        val key = parseLocation(location) ?: return null
        val response = httpClient.send(
            requestBuilder(key)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray()
        )
        return when (response.statusCode()) {
            in 200..299 -> response.body()
            404 -> null
            else -> throw IllegalStateException("http object storage read failed: ${response.statusCode()}")
        }
    }

    override fun delete(location: String?) {
        if (location.isNullOrBlank()) {
            return
        }
        val key = parseLocation(location) ?: return
        val response = httpClient.send(
            requestBuilder(key)
                .DELETE()
                .build(),
            HttpResponse.BodyHandlers.discarding()
        )
        require(response.statusCode() in 200..299 || response.statusCode() == 404) {
            "http object storage delete failed: ${response.statusCode()}"
        }
    }

    private fun requestBuilder(key: String): HttpRequest.Builder {
        val normalizedEndpoint = endpointUrl.trim().trimEnd('/')
        require(normalizedEndpoint.isNotBlank()) { "endpointUrl must not be blank for HTTP object storage mode" }
        val uri = URI.create(
            "$normalizedEndpoint/${encodePathSegment(normalizedBucket())}/${encodePath(key)}"
        )
        return HttpRequest.newBuilder(uri)
            .timeout(java.time.Duration.ofMillis(requestTimeoutMillis.coerceAtLeast(1000)))
            .apply {
            if (apiKey.isNotBlank()) {
                header(apiKeyHeader, apiKey)
            }
        }
    }

    private fun parseLocation(location: String): String? {
        val prefix = "s3://${normalizedBucket()}/"
        if (!location.startsWith(prefix)) {
            return null
        }
        return location.removePrefix(prefix).trimStart('/').takeIf { it.isNotBlank() }
    }

    private fun buildObjectKey(jobId: String, fileName: String): String {
        val prefix = keyPrefix.trim('/').ifBlank { "export-artifacts" }
        return "$prefix/${buildSafeFileName(jobId, fileName)}"
    }

    private fun normalizedBucket(): String =
        bucket.trim().ifBlank { "psy-export-artifacts" }
}

private fun buildSafeFileName(jobId: String, fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").takeIf { it.matches(Regex("[A-Za-z0-9]{1,16}")) }
    return if (extension == null) jobId else "$jobId.$extension"
}

private fun resolveBaseDirectory(storageDir: String): Path {
    val configured = storageDir.trim().takeIf { it.isNotBlank() }
        ?: "${System.getProperty("java.io.tmpdir")}/psy-export-jobs"
    return Path.of(configured).toAbsolutePath().normalize()
}

private fun readBytes(path: Path): ByteArray? =
    runCatching {
        if (Files.exists(path) && Files.isRegularFile(path)) Files.readAllBytes(path) else null
    }.getOrNull()

private fun encodePath(path: String): String =
    path.split('/')
        .filter { it.isNotBlank() }
        .joinToString("/") { encodePathSegment(it) }

private fun encodePathSegment(segment: String): String =
    URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")
