package org.sainm.psy.respondent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringResourceContractTest {

    private val appDirectory = listOf(File("."), File("app")).first {
        File(it, "src/main/res/values/strings.xml").isFile
    }
    private val resourceFiles = listOf(
        File(appDirectory, "src/main/res/values/strings.xml"),
        File(appDirectory, "src/main/res/values-ja/strings.xml"),
        File(appDirectory, "src/main/res/values-zh/strings.xml")
    )

    @Test
    fun `english japanese and chinese string catalogs have identical keys and placeholders`() {
        val catalogs = resourceFiles.associateWith(::readCatalog)
        val baseline = catalogs.getValue(resourceFiles.first())
        assertTrue("Android catalog should contain the full respondent UI", baseline.size >= 160)

        catalogs.forEach { (file, catalog) ->
            assertEquals("String keys differ in ${file.path}", baseline.keys, catalog.keys)
            baseline.forEach { (key, value) ->
                assertEquals(
                    "Format placeholders differ for $key in ${file.path}",
                    placeholders(value),
                    placeholders(catalog.getValue(key))
                )
            }
        }
    }

    @Test
    fun `respondent UI uses Android string resources instead of inline language helper`() {
        val source = File(appDirectory, "src/main/java/org/sainm/psy/respondent/ui/PsyRespondentApp.kt").readText()
        assertFalse("Inline l(zh, ja, en) calls are forbidden", Regex("(?<![A-Za-z0-9_])l\\(").containsMatchIn(source))
        val declaredKeys = readCatalog(resourceFiles.first()).keys
        val referencedKeys = Regex("R\\.string\\.([A-Za-z0-9_]+)").findAll(source).map { it.groupValues[1] }.toSet()
        assertTrue("UI must reference Android string resources", referencedKeys.isNotEmpty())
        assertTrue("UI references missing string keys: ${referencedKeys - declaredKeys}", declaredKeys.containsAll(referencedKeys))
    }

    private fun readCatalog(file: File): Map<String, String> {
        assertTrue("Missing resource file: ${file.path}", file.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        return (0 until document.documentElement.childNodes.length)
            .map { document.documentElement.childNodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.tagName == "string" }
            .associate { it.getAttribute("name") to it.textContent }
    }

    private fun placeholders(value: String): Set<String> =
        Regex("""%(\d+)\${'$'}s""").findAll(value).map { it.groupValues[1] }.toSet()
}
