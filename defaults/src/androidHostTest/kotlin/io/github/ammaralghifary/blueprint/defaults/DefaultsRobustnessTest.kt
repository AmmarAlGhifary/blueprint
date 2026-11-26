package io.github.ammaralghifary.blueprint.defaults

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.render.BlueprintRenderer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Server payloads the stock pack must survive: a bad payload should degrade, never crash the host.
/** @author Ammar Al Ghifary */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultsRobustnessTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun markDebuggable() {
        val info = ApplicationProvider.getApplicationContext<Application>().applicationInfo
        info.flags = info.flags or ApplicationInfo.FLAG_DEBUGGABLE
    }

    private val blueprint = Blueprint(DefaultSduiRegistry)

    /** Renders [json] and fails with a readable message if composition or layout throws. */
    private fun render(label: String, json: String) {
        try {
            val screen = blueprint.decodeScreen(json)
            compose.setContent { BlueprintRenderer(screen, blueprint) }
            compose.waitForIdle()
        } catch (t: Throwable) {
            throw AssertionError("$label crashed the host: ${t::class.simpleName}: ${t.message?.take(160)}", t)
        }
    }

    private fun list(vararg items: String) =
        """{"type":"list","children":[${items.joinToString(",")}]}"""

    @Test
    fun verticalListInsideAScrollableColumn() {
        render(
            "scrollable column > list",
            """{"body":{"type":"column","scrollable":true,"children":[
                {"type":"text","text":"above"},
                ${list("""{"type":"text","text":"item"}""")}]}}""",
        )
        compose.onNodeWithText("item").assertIsDisplayed()
    }

    @Test
    fun verticalListNestedInsideAnotherVerticalList() {
        render(
            "list > card > list",
            """{"body":${list("""{"type":"card","children":[${list("""{"type":"text","text":"inner"}""")}]}""")}}""",
        )
        compose.onNodeWithText("inner").assertIsDisplayed()
    }

    @Test
    fun aBoundedListStillFillsTheWidthItWasGiven() {
        // The list sits in a BoxWithConstraints now; min constraints must still reach the lazy list,
        // so it is 300dp wide even though its only child is a few characters of text.
        render(
            "bounded fill",
            """{"body":{"type":"list","modifier":{"width":{"type":"exact","value":300}},"children":[
                {"type":"text","text":"hi"}]}}""",
        )
        compose.onNode(hasScrollAction()).assertWidthIsEqualTo(300.dp)
    }

    @Test
    fun theStackFallbackKeepsEveryItemVisible() {
        render(
            "fallback items",
            """{"body":{"type":"column","scrollable":true,"children":[
                {"type":"list","spacing":4,"children":[{"type":"text","text":"one"},{"type":"text","text":"two"},{"type":"text","text":"three"}]}]}}""",
        )
        listOf("one", "two", "three").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun horizontalListInsideAVerticalListIsFine() {
        render(
            "list > horizontal list",
            """{"body":${list("""{"type":"list","orientation":"horizontal","children":[{"type":"text","text":"cell"}]}""")}}""",
        )
        compose.onNodeWithText("cell").assertIsDisplayed()
    }

    @Test
    fun enormousWeightsDoNotCrashLayout() {
        render(
            "weights 3e38",
            """{"body":{"type":"row","modifier":{"width":{"type":"exact","value":300}},"children":[
                {"type":"text","text":"a","modifier":{"weight":3.0e38}},
                {"type":"text","text":"b","modifier":{"weight":3.0e38}}]}}""",
        )
    }

    @Test
    fun hugeSizesDoNotCrashLayout() {
        render(
            "huge sizes",
            """{"body":{"type":"column","children":[
                {"type":"spacer","size":2147483647},
                {"type":"divider","thickness":2147483647},
                {"type":"text","text":"wide","modifier":{"width":{"type":"exact","value":2147483647},"height":{"type":"exact","value":2147483647}}}]}}""",
        )
    }

    @Test
    fun hugeFontSizeAndPaddingDoNotCrash() {
        render(
            "huge text",
            """{"body":{"type":"text","text":"big","modifier":{"font_size":2000000000,
                "padding":{"all":2000000000},"margin":{"all":2000000000},"corner_radius":2000000000,
                "border_width":2000000000,"border_color":"#FF0000","elevation":2000000000}}}""",
        )
    }

    @Test
    fun negativeEverythingDoesNotCrash() {
        render(
            "negatives",
            """{"body":{"type":"column","children":[
                {"type":"text","text":"neg","max_lines":-4,"modifier":{"font_size":-9,"font_weight":-100,
                    "weight":-1,"padding":{"all":-8},"margin":{"all":-8},"corner_radius":-3,"elevation":-2,
                    "width":{"type":"exact","value":-50},"height":{"type":"exact","value":-50}}},
                {"type":"list","spacing":-10,"children":[{"type":"text","text":"x"}]}]}}""",
        )
    }

    @Test
    fun zeroWeightAndZeroLinesDoNotCrash() {
        render(
            "zero",
            """{"body":{"type":"row","children":[
                {"type":"text","text":"z","max_lines":0,"modifier":{"weight":0}}]}}""",
        )
    }

    @Test
    fun garbageImageUrlsDoNotCrash() {
        val urls = listOf("", "not a url", "http://", "file:///nonexistent", "data:,", "\u0000")
        val images = urls.joinToString(",") { """{"type":"image","url":${kotlinx.serialization.json.JsonPrimitive(it)}}""" }
        render("garbage image urls", """{"body":{"type":"column","children":[$images]}}""")
    }

    @Test
    fun allEnumFieldsWithGarbageValuesDoNotCrash() {
        render(
            "garbage enums",
            """{"body":{"type":"column","arrangement":"sideways","children":[
                {"type":"row","arrangement":"💥","children":[
                    {"type":"text","text":"t","style":"gigantic","align":"diagonal","modifier":{"alignment":"nowhere"}}]},
                {"type":"box","children":[{"type":"text","text":"b","modifier":{"alignment":"top_nowhere"}}]},
                {"type":"image","url":"x","scale":"stretchy"},
                {"type":"button","text":"go","variant":"neon"},
                {"type":"list","orientation":"diagonal","children":[]}]}}""",
        )
    }
}
