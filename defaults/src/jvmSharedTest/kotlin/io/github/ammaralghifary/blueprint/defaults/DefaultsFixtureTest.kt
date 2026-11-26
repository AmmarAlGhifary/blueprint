package io.github.ammaralghifary.blueprint.defaults

import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.UnknownComponent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** @author Ammar Al Ghifary */
class DefaultsFixtureTest {
    private val blueprint = Blueprint(DefaultSduiRegistry)

    private fun walk(c: SduiComponent): List<SduiComponent> = listOf(c) + when (c) {
        is SduiColumn -> c.children.flatMap(::walk)
        is SduiRow -> c.children.flatMap(::walk)
        is SduiBox -> c.children.flatMap(::walk)
        is SduiList -> c.children.flatMap(::walk)
        is SduiCard -> c.children.flatMap(::walk)
        else -> emptyList()
    }

    @Test
    fun stockScreenDecodesWithZeroUnknownComponents() {
        val json = javaClass.classLoader!!.getResource("stock_screen.json")!!.readText()
        val screen = blueprint.decodeScreen(json)
        assertNotNull(screen.id)
        val nodes = walk(screen.body)
        assertTrue(nodes.none { it is UnknownComponent })
        val types = nodes.map { it::class }.toSet()
        listOf(
            SduiText::class, SduiImage::class, SduiButton::class, SduiCard::class, SduiColumn::class,
            SduiRow::class, SduiBox::class, SduiList::class, SduiSpacer::class, SduiDivider::class,
        ).forEach { assertTrue("fixture misses ${it.simpleName}", it in types) }
    }

    @Test
    fun homeDashboardFixtureDecodesWithTheStockPackWithoutThrowing() {
        // Lives in :core's test resources, so it is not on this module's classpath.
        val file = listOf("../core/src/jvmSharedTest/resources/home_dashboard.json", "core/src/jvmSharedTest/resources/home_dashboard.json")
            .map(::File).first { it.exists() }
        val screen = blueprint.decodeScreen(file.readText())
        assertEquals("home_dashboard", screen.id)
        // Its spacer sends a string token ("medium") where the neutral schema wants a number:
        // that node degrades, everything else is unaffected.
        val children = (screen.body as SduiColumn).children
        assertEquals("space_between", (screen.body as SduiColumn).arrangement)
        val spacer = children[0] as UnknownComponent
        assertEquals("spacer", spacer.type)
        assertNotNull(spacer.error)
        // The other types are not in the stock vocabulary, so they are plain unregistered nodes.
        assertTrue(children.all { it is UnknownComponent })
    }
}
