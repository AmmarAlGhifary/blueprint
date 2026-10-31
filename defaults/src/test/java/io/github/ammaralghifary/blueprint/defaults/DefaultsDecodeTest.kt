package io.github.ammaralghifary.blueprint.defaults

import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.model.NavigateAction
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.UnknownComponent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultsDecodeTest {
    private val blueprint = Blueprint(DefaultSduiRegistry)

    private fun body(json: String): SduiComponent = blueprint.decodeScreen("""{"body":$json}""").body

    private fun childrenOf(json: String): List<SduiComponent> = (body(json) as SduiColumn).children

    private fun assertDegraded(node: SduiComponent) {
        assertTrue("expected UnknownComponent but was $node", node is UnknownComponent)
        assertNotNull((node as UnknownComponent).error)
    }

    private fun walk(c: SduiComponent): List<SduiComponent> = listOf(c) + when (c) {
        is SduiColumn -> c.children.flatMap(::walk)
        is SduiRow -> c.children.flatMap(::walk)
        is SduiBox -> c.children.flatMap(::walk)
        is SduiList -> c.children.flatMap(::walk)
        is SduiCard -> c.children.flatMap(::walk)
        else -> emptyList()
    }

    @Test
    fun textDecodes() {
        val t = body("""{"type":"text","text":"hi","style":"title","max_lines":2,"align":"center"}""") as SduiText
        assertEquals(SduiText("hi", "title", 2, "center"), t)
    }

    @Test
    fun imageDecodes() {
        val i = body("""{"type":"image","url":"https://x/y.png","content_description":"d","scale":"crop"}""") as SduiImage
        assertEquals(SduiImage("https://x/y.png", "d", "crop"), i)
    }

    @Test
    fun buttonDecodesWithNavigateAction() {
        val b = body(
            """{"type":"button","text":"Go","variant":"outlined",
                "action":{"type":"navigate","destination":"home","params":{"a":"b"}}}""",
        ) as SduiButton
        assertEquals("Go", b.text)
        assertEquals("outlined", b.variant)
        assertEquals(NavigateAction("home", mapOf("a" to "b")), b.action)
    }

    @Test
    fun cardAndLayoutsDecode() {
        val col = body(
            """{"type":"column","arrangement":"center","scrollable":true,"children":[
                {"type":"row","arrangement":"space_between","children":[{"type":"spacer","size":4}]},
                {"type":"box","children":[{"type":"divider","thickness":2,"color":"#FF0000"}]},
                {"type":"list","orientation":"horizontal","spacing":6,"children":[]},
                {"type":"card","action":{"type":"navigate","destination":"d"},"children":[]}]}""",
        ) as SduiColumn
        assertEquals("center", col.arrangement)
        assertTrue(col.scrollable)
        val row = col.children[0] as SduiRow
        assertEquals("space_between", row.arrangement)
        assertEquals(4, (row.children[0] as SduiSpacer).size)
        val div = (col.children[1] as SduiBox).children[0] as SduiDivider
        assertEquals(2, div.thickness)
        assertEquals("#FF0000", div.color)
        val list = col.children[2] as SduiList
        assertEquals("horizontal", list.orientation)
        assertEquals(6, list.spacing)
        assertTrue((col.children[3] as SduiCard).action is NavigateAction)
    }

    @Test
    fun defaultsApplyWhenOptionalFieldsAreOmitted() {
        val col = body("""{"type":"column"}""") as SduiColumn
        assertTrue(col.children.isEmpty())
        assertFalse(col.scrollable)
        assertNull(col.arrangement)
        assertNull((body("""{"type":"spacer"}""") as SduiSpacer).size)
        assertNull((body("""{"type":"divider"}""") as SduiDivider).thickness)
        assertNull((body("""{"type":"button","text":"x"}""") as SduiButton).action)
        assertTrue((body("""{"type":"card"}""") as SduiCard).children.isEmpty())
    }

    @Test
    fun missingRequiredTextDegradesAloneAndSiblingSurvives() {
        val kids = childrenOf("""{"type":"column","children":[{"type":"text"},{"type":"text","text":"ok"}]}""")
        assertDegraded(kids[0])
        assertEquals("ok", (kids[1] as SduiText).text)
    }

    @Test
    fun missingRequiredImageUrlDegradesAlone() {
        val kids = childrenOf("""{"type":"column","children":[{"type":"image"},{"type":"spacer"}]}""")
        assertDegraded(kids[0])
        assertTrue(kids[1] is SduiSpacer)
    }

    @Test
    fun missingRequiredButtonTextDegradesAlone() {
        val kids = childrenOf("""{"type":"column","children":[{"type":"button"},{"type":"divider"}]}""")
        assertDegraded(kids[0])
        assertTrue(kids[1] is SduiDivider)
    }

    @Test
    fun unknownEnumStringsDecodeWithoutErrorAndFallBackWhenRendered() {
        val kids = childrenOf(
            """{"type":"column","arrangement":"zigzag","children":[
                {"type":"text","text":"a","style":"gigantic","align":"sideways"},
                {"type":"image","url":"u","scale":"stretchy"},
                {"type":"button","text":"b","variant":"neon"},
                {"type":"list","orientation":"diagonal"}]}""",
        )
        assertTrue(kids.none { it is UnknownComponent })
        assertEquals(androidx.compose.ui.layout.ContentScale.Fit, contentScaleOf("stretchy"))
        assertNull(textAlignOf("sideways"))
        assertEquals(androidx.compose.foundation.layout.Arrangement.Top, verticalArrangementOf("zigzag"))
        assertEquals(androidx.compose.foundation.layout.Arrangement.Start, horizontalArrangementOf("zigzag"))
        assertNull(horizontalAlignmentOf("x"))
        assertNull(verticalAlignmentOf("x"))
        assertNull(boxAlignmentOf("x"))
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
        val file = listOf("../core/src/test/resources/home_dashboard.json", "core/src/test/resources/home_dashboard.json")
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
