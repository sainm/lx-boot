package org.sainm.psy.export.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Field
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class ExportJobStoreTest {

    private lateinit var store: ExportJobStore

    @BeforeEach
    fun setUp() {
        store = ExportJobStore()
    }

    @Test
    fun `create returns PENDING job`() {
        val job = store.create("job-1")

        assertEquals("job-1", job.id)
        assertEquals(ExportJobStatus.PENDING, job.status)
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
    fun `markProcessing transitions PENDING to PROCESSING`() {
        store.create("job-3")
        store.markProcessing("job-3")
        assertEquals(ExportJobStatus.PROCESSING, store.find("job-3")!!.status)
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
    fun `cleanup removes jobs older than 15 minutes`() {
        store.create("old-job")

        // Backdating createdAt via reflection to simulate an expired job
        @Suppress("UNCHECKED_CAST")
        val jobsField: Field = ExportJobStore::class.java.getDeclaredField("jobs")
        jobsField.isAccessible = true
        val jobs = jobsField.get(store) as ConcurrentHashMap<String, ExportJob>
        jobs["old-job"] = jobs["old-job"]!!.copy(createdAt = Instant.now().minusSeconds(1000))

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
}
