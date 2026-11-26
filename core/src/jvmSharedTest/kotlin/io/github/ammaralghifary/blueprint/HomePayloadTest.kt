package io.github.ammaralghifary.blueprint

import io.github.ammaralghifary.blueprint.model.UnknownComponent
import io.github.ammaralghifary.blueprint.registry.sduiRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** @author Ammar Al Ghifary */
class HomePayloadTest {

    @Test
    fun realWorldHomePayloadSurvivesWithHostSpecificTypesAsUnknown() {
        val bp = Blueprint(sduiRegistry { component<TestColumn>("column") { _, _, _ -> } })
        val json = javaClass.classLoader!!.getResource("home_dashboard.json")!!.readText()
        val screen = bp.decodeScreen(json)
        assertEquals("home_dashboard", screen.id)
        val children = (screen.body as TestColumn).children
        assertEquals(6, children.size)
        assertTrue(children.all { it is UnknownComponent })
        val banner = children[2] as UnknownComponent
        assertEquals("warning_banner", banner.type)
        assertEquals("#930b20", banner.modifier?.backgroundColor)
        assertEquals(16, banner.modifier?.margin?.resolvedStart)
    }
}
