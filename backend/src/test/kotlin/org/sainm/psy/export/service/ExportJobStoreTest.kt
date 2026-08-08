package org.sainm.psy.export.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.sainm.psy.common.exception.BizException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.lang.reflect.Field
import java.nio.file.Files
import java.time.Instant

class ExportJobStoreTest {

    private lateinit var store: ExportJobStore

    @BeforeEach
    fun setUp() {
        store = ExportJobStore()
    }

    @Test
    fun `create returns PENDING job`() {
        val job = store.create(
            id = "job-1",
            reportId = 10L,
            resultId = 20L,
            exportFormat = "PDF",
            localeTag = "zh-CN"
        )

        assertEquals("job-1", job.id)
        assertEquals(ExportJobStatus.PENDING, job.status)
        assertEquals(10L, job.reportId)
        assertEquals(20L, job.resultId)
        assertEquals("PDF", job.exportFormat)
        assertEquals("zh-CN", job.localeTag)
        assertNull(job.fileName)
        assertNull(job.bytes)
        assertNull(job.error)
        assertNull(job.completedAt)
    }

    @Test
    fun `find returns null for unknown jobId`() {
        assertNull(store.find("does-not-exist"))
    }

    @Test
    fun `find returns created job`() {
        store.create("job-2")
        val found = store.find("job-2")
        assertNotNull(found)
        assertEquals("job-2", found!!.id)
    }

    @Test
    fun `listRecent returns newest jobs first and honors status filter`() {
        store.create("job-a")
        store.create("job-b")
        store.markFailed("job-a", "failed")

        val allJobs = store.listRecent(limit = 10)
        val failedJobs = store.listRecent(limit = 10, status = ExportJobStatus.FAILED)

        assertEquals(listOf("job-b", "job-a"), allJobs.map { it.id })
        assertEquals(listOf("job-a"), failedJobs.map { it.id })
    }

    @Test
    fun `markProcessing transitions PENDING to PROCESSING`() {
        store.create("job-3")
        store.markProcessing("job-3")
        assertEquals(ExportJobStatus.PROCESSING, store.find("job-3")!!.status)
    }

    @Test
    fun `claimPending transitions only pending job to PROCESSING`() {
        store.create("claim-job")

        val claimed = store.claimPending("claim-job")

        assertNotNull(claimed)
        assertEquals(ExportJobStatus.PROCESSING, claimed!!.status)
        assertEquals(ExportJobStatus.PROCESSING, store.find("claim-job")!!.status)
        assertNull(store.claimPending("claim-job"))
    }

    @Test
    fun `markDone sets DONE status with file data`() {
        val bytes = "hello".toByteArray()
        store.create("job-4")
        store.markDone("job-4", "report.txt", "text/plain", bytes)

        val job = store.find("job-4")!!
        assertEquals(ExportJobStatus.DONE, job.status)
        assertEquals("report.txt", job.fileName)
        assertEquals("text/plain", job.contentType)
        assertArrayEquals(bytes, job.bytes)
        assertNotNull(job.completedAt)
    }

    @Test
    fun `markDone marks job as FAILED when file exceeds in-memory limit`() {
        store = ExportJobStore(maxInMemoryFileBytes = 3)
        store.create("job-too-large")

        store.markDone("job-too-large", "report.txt", "text/plain", "hello".toByteArray())

        val job = store.find("job-too-large")!!
        assertEquals(ExportJobStatus.FAILED, job.status)
        assertNull(job.bytes)
        assertEquals("Export file is too large to keep in memory", job.error)
        assertNotNull(job.completedAt)
    }

    @Test
    fun `markFailed sets FAILED status with error message`() {
        store.create("job-5")
        store.markFailed("job-5", "PDF generation failed")

        val job = store.find("job-5")!!
        assertEquals(ExportJobStatus.FAILED, job.status)
        assertEquals("PDF generation failed", job.error)
        assertNotNull(job.completedAt)
    }

    @Test
    fun `markProcessing on unknown jobId is a no-op`() {
        store.markProcessing("ghost-job") // should not throw
        assertNull(store.find("ghost-job"))
    }

    @Test
    fun `markDone on unknown jobId is a no-op`() {
        store.markDone("ghost-job", "f.pdf", "application/pdf", ByteArray(0))
        assertNull(store.find("ghost-job"))
    }

    @Test
    fun `markFailed on unknown jobId is a no-op`() {
        store.markFailed("ghost-job", "err")
        assertNull(store.find("ghost-job"))
    }

    @Test
    fun `create rejects new job when in-memory job limit is reached`() {
        store = ExportJobStore(maxInMemoryJobs = 1)
        store.create("job-1")

        val ex = assertThrows<BizException> {
            store.create("job-2")
        }

        assertEquals("EXPORT_JOB_LIMIT_EXCEEDED", ex.code)
        assertNull(store.find("job-2"))
    }

    @Test
    fun `cleanup removes jobs older than 15 minutes`() {
        store.create("old-job")

        // Backdate the stored job via reflection to simulate an expired entry.
        val jobsField: Field = ExportJobStore::class.java.getDeclaredField("jobs")
        jobsField.isAccessible = true
        val jobs = jobsField.get(store)
        val oldJob = store.find("old-job")!!
        jobs.javaClass
            .getMethod("put", Any::class.java, Any::class.java)
            .invoke(jobs, "old-job", oldJob.copy(createdAt = Instant.now().minusSeconds(1000)))

        store.create("recent-job")
        store.cleanup()

        assertNull(store.find("old-job"))
        assertNotNull(store.find("recent-job"))
    }

    @Test
    fun `full lifecycle PENDING to PROCESSING to DONE`() {
        val id = "lifecycle-job"
        store.create(id)
        assertEquals(ExportJobStatus.PENDING, store.find(id)!!.status)

        store.markProcessing(id)
        assertEquals(ExportJobStatus.PROCESSING, store.find(id)!!.status)

        store.markDone(id, "out.pdf", "application/pdf", byteArrayOf(1, 2, 3))
        val done = store.find(id)!!
        assertEquals(ExportJobStatus.DONE, done.status)
        assertNotNull(done.completedAt)
    }

    @Test
    fun `resetFailedForRetry returns failed job to PENDING and keeps retry context`() {
        store.create(
            id = "retry-job",
            reportId = 10L,
            resultId = null,
            exportFormat = "TEXT",
            localeTag = "en-US"
        )
        store.markFailed("retry-job", "PDF generation failed")

        val retried = store.resetFailedForRetry("retry-job")!!

        assertEquals(ExportJobStatus.PENDING, retried.status)
        assertEquals(10L, retried.reportId)
        assertEquals("TEXT", retried.exportFormat)
        assertEquals("en-US", retried.localeTag)
        assertEquals(1, retried.retryCount)
        assertNull(retried.error)
        assertNull(retried.completedAt)
    }

    @Test
    fun `jdbc store persists full lifecycle`() {
        val jdbcStore = createJdbcStore()
        val bytes = byteArrayOf(1, 2, 3)

        jdbcStore.create("db-job", reportId = 10L, resultId = 20L, exportFormat = "PDF", localeTag = "zh-CN")
        jdbcStore.markProcessing("db-job")
        jdbcStore.markDone("db-job", "report.pdf", "application/pdf", bytes)

        val job = jdbcStore.find("db-job")!!
        assertEquals(ExportJobStatus.DONE, job.status)
        assertEquals(10L, job.reportId)
        assertEquals(20L, job.resultId)
        assertEquals("PDF", job.exportFormat)
        assertEquals("zh-CN", job.localeTag)
        assertEquals("report.pdf", job.fileName)
        assertEquals("application/pdf", job.contentType)
        assertNotNull(job.filePath)
        assertEquals(3L, job.fileSize)
        assertArrayEquals(bytes, job.bytes)
        assertNull(job.error)
        assertNotNull(job.completedAt)
    }

    @Test
    fun `jdbc store stores file outside database when file storage is enabled`() {
        val jdbcStore = createJdbcStore()
        val bytes = "stored on disk".toByteArray()

        jdbcStore.create("db-file-job", reportId = 10L)
        jdbcStore.markDone("db-file-job", "report.txt", "text/plain", bytes)

        val job = jdbcStore.find("db-file-job")!!
        assertEquals(ExportJobStatus.DONE, job.status)
        assertNotNull(job.filePath)
        assertTrue(Files.exists(java.nio.file.Path.of(job.filePath!!)))
        assertArrayEquals(bytes, job.bytes)
    }

    @Test
    fun `jdbc store can use custom artifact storage adapter`() {
        val saved = mutableMapOf<String, ByteArray>()
        val storage = object : ExportArtifactStorage {
            override fun store(jobId: String, fileName: String, bytes: ByteArray): String {
                val key = "memory://$jobId/$fileName"
                saved[key] = bytes
                return key
            }

            override fun read(location: String?): ByteArray? = location?.let(saved::get)

            override fun delete(location: String?) {
                if (location != null) {
                    saved.remove(location)
                }
            }
        }
        val jdbcStore = createJdbcStore(exportArtifactStorage = storage)
        val bytes = "stored by adapter".toByteArray()

        jdbcStore.create("db-adapter-job", reportId = 10L)
        jdbcStore.markDone("db-adapter-job", "report.txt", "text/plain", bytes)

        val job = jdbcStore.find("db-adapter-job")!!
        assertEquals("memory://db-adapter-job/report.txt", job.filePath)
        assertArrayEquals(bytes, job.bytes)
    }

    @Test
    fun `jdbc store recovers stale processing jobs as failed`() {
        val jdbcStore = createJdbcStore(processingTimeoutMinutes = 1)
        jdbcStore.create("stale-job", reportId = 10L)
        jdbcStore.markProcessing("stale-job")

        val recovered = jdbcStore.recoverStaleProcessingJobs(Instant.now().plusSeconds(120))

        assertEquals(1, recovered)
        val job = jdbcStore.find("stale-job")!!
        assertEquals(ExportJobStatus.FAILED, job.status)
        assertEquals("Export job timed out while processing; reset it for retry.", job.error)
        assertNotNull(job.completedAt)
    }

    @Test
    fun `jdbc store claimPendingJobs claims oldest pending jobs once`() {
        val jdbcStore = createJdbcStore()
        jdbcStore.create("job-a", reportId = 10L)
        jdbcStore.create("job-b", reportId = 11L)
        jdbcStore.create("job-c", reportId = 12L)

        val claimed = jdbcStore.claimPendingJobs(2)

        assertEquals(2, claimed.size)
        assertEquals(listOf("job-a", "job-b"), claimed.map { it.id })
        assertTrue(claimed.all { it.status == ExportJobStatus.PROCESSING })
        assertEquals(ExportJobStatus.PROCESSING, jdbcStore.find("job-a")!!.status)
        assertEquals(ExportJobStatus.PROCESSING, jdbcStore.find("job-b")!!.status)
        assertEquals(ExportJobStatus.PENDING, jdbcStore.find("job-c")!!.status)
        assertTrue(jdbcStore.claimPendingJobs(2).map { it.id }.contains("job-c"))
    }

    @Test
    fun `jdbc store listRecent returns newest jobs first and filters by status`() {
        val jdbcStore = createJdbcStore()
        jdbcStore.create("job-a", reportId = 10L)
        jdbcStore.create("job-b", reportId = 11L)
        jdbcStore.markFailed("job-a", "failed")

        val allJobs = jdbcStore.listRecent(limit = 10)
        val failedJobs = jdbcStore.listRecent(limit = 10, status = ExportJobStatus.FAILED)

        assertEquals(listOf("job-b", "job-a"), allJobs.map { it.id })
        assertEquals(listOf("job-a"), failedJobs.map { it.id })
    }

    @Test
    fun `jdbc store resets failed job for retry`() {
        val jdbcStore = createJdbcStore()
        jdbcStore.create("db-retry-job", reportId = 30L, exportFormat = "TEXT", localeTag = "en-US")
        jdbcStore.markFailed("db-retry-job", "failed")

        val retried = jdbcStore.resetFailedForRetry("db-retry-job")!!

        assertEquals(ExportJobStatus.PENDING, retried.status)
        assertEquals(30L, retried.reportId)
        assertEquals("TEXT", retried.exportFormat)
        assertEquals("en-US", retried.localeTag)
        assertEquals(1, retried.retryCount)
        assertNull(retried.error)
        assertNull(retried.completedAt)
    }

    private fun createJdbcStore(
        processingTimeoutMinutes: Long = 30,
        exportArtifactStorage: ExportArtifactStorage? = null
    ): ExportJobStore {
        val dataSource = DriverManagerDataSource(
            "jdbc:h2:mem:export_job_store_${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        )
        JdbcTemplate(dataSource).execute(
            """
            create table psy_export_job (
                id varchar(64) primary key,
                status varchar(32) not null,
                created_by bigint,
                tenant_id bigint,
                report_id bigint,
                result_id bigint,
                source_type varchar(32) not null default 'REPORT',
                request_json text,
                retry_count integer not null default 0,
                export_format varchar(32),
                locale_tag varchar(64),
                desensitized_flag boolean not null default true,
                file_name varchar(255),
                content_type varchar(128),
                file_path varchar(1024),
                file_size bigint,
                file_bytes bytea,
                error_message text,
                created_at timestamp not null default current_timestamp,
                completed_at timestamp,
                updated_at timestamp not null default current_timestamp
            )
            """.trimIndent()
        )
        return ExportJobStore(
            jdbcTemplate = NamedParameterJdbcTemplate(dataSource),
            exportArtifactStorage = exportArtifactStorage,
            storageDir = Files.createTempDirectory("export-job-store").toString(),
            processingTimeoutMinutes = processingTimeoutMinutes
        )
    }
}
