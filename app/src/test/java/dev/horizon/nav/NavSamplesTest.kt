package dev.horizon.nav

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs the parser over real Google Maps notifications captured in N0 (docs/nav/N0-report.md),
 * stored under src/test/resources/nav/samples/ with street names redacted.
 */
class NavSamplesTest {
    private class Sample(val file: String, val json: JSONObject) {
        val extras: JSONObject? = json.optJSONObject("extras")

        fun fields() = NavFields(
            packageName = json.getString("package"),
            category = json.stringOrNull("category"),
            isOngoing = json.optBoolean("ongoing"),
            title = extras?.stringOrNull("android.title"),
            subText = extras?.stringOrNull("android.subText"),
            shortCriticalText = extras?.stringOrNull("android.shortCriticalText"),
            progress = extras?.optInt("android.progress") ?: 0,
            progressMax = extras?.optInt("android.progressMax") ?: 0,
        )

        override fun toString() = "$file ${extras?.stringOrNull("android.title")}"
    }

    /** Posted and already-active notifications; removals carry no content. */
    private val samples: List<Sample> by lazy {
        val dir = File(requireNotNull(javaClass.getResource("/nav/samples")) { "fixtures missing" }.toURI())
        dir.listFiles { f -> f.name.endsWith(".jsonl") }!!.sortedBy { it.name }.flatMap { file ->
            file.readLines().filter { it.isNotBlank() }.map { Sample(file.name, JSONObject(it)) }
        }.filter { it.json.getString("event") != "removed" }
    }

    private fun parsed(fileSuffix: String) =
        samples.filter { it.file.endsWith(fileSuffix) }.mapNotNull { NavParser.parse(it.fields()) }

    @Test fun `fixtures are present`() = assertTrue(samples.size > 100)

    @Test fun `every turn-by-turn notification parses`() {
        val nav = samples.filter { it.json.stringOrNull("category") == "navigation" }
        assertTrue(nav.isNotEmpty())
        nav.forEach { sample ->
            val info = NavParser.parse(sample.fields())
            assertNotNull("not parsed: $sample", info)
            assertTrue("blank instruction: $sample", info!!.instruction.isNotBlank())
        }
    }

    @Test fun `traffic alerts and group summaries are ignored`() {
        val others = samples.filter { it.json.stringOrNull("category") != "navigation" }
        assertTrue(others.isNotEmpty())
        others.forEach { assertNull("should be ignored: $it", NavParser.parse(it.fields())) }
    }

    @Test fun `metric turn with a road`() {
        val info = parsed("S1.jsonl").first { it.road != null }
        assertEquals("100 m", info.distanceToTurn)
        assertEquals("Turn left", info.instruction)
        assertEquals("toward Street A", info.road)
        assertEquals("7:53 pm", info.etaTime)
        assertFalse(info.imperial)
        assertEquals(5212, info.remainingMeters)
    }

    @Test fun `starting navigation has no route yet`() {
        val info = parsed("S1.jsonl").first()
        assertEquals("Starting navigation…", info.instruction)
        assertTrue(info.starting)
    }

    @Test fun `miles samples are imperial`() {
        val withDistance = parsed("S5-miles.jsonl").filter { it.distanceToTurn != null }
        assertTrue(withDistance.isNotEmpty())
        withDistance.forEach { assertTrue("not imperial: ${it.distanceToTurn}", it.imperial) }
    }

    @Test fun `24h clock arrival time`() =
        assertTrue(parsed("S6-24h.jsonl").any { it.etaTime == "19:59" })
}

private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
