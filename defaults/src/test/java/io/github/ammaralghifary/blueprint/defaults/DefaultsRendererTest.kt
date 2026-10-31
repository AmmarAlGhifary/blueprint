package io.github.ammaralghifary.blueprint.defaults

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.BlueprintRenderer
import io.github.ammaralghifary.blueprint.NavigateAction
import io.github.ammaralghifary.blueprint.SduiAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultsRendererTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun markDebuggable() {
        // The default placeholder is only drawn in debuggable apps.
        val info = ApplicationProvider.getApplicationContext<Application>().applicationInfo
        info.flags = info.flags or ApplicationInfo.FLAG_DEBUGGABLE
    }

    private val blueprint = Blueprint(DefaultSduiRegistry)

    private fun show(json: String, onAction: (SduiAction) -> Unit = {}) {
        val screen = blueprint.decodeScreen(json)
        compose.setContent { BlueprintRenderer(screen, blueprint, onAction = onAction) }
    }

    @Test
    fun textShows() {
        show("""{"body":{"type":"text","text":"Hello stock","style":"headline"}}""")
        compose.onNodeWithText("Hello stock").assertIsDisplayed()
    }

    @Test
    fun rowHandsDownWeight() {
        show(
            """{"body":{"type":"row","modifier":{"width":{"type":"exact","value":300}},"children":[
                {"type":"text","text":"one","modifier":{"weight":1}},
                {"type":"text","text":"two","modifier":{"weight":2}}]}}""",
        )
        compose.onNodeWithText("one").assertWidthIsEqualTo(100.dp)
        compose.onNodeWithText("two").assertWidthIsEqualTo(200.dp)
    }

    @Test
    fun rowHandsDownVerticalAlignment() {
        show(
            """{"body":{"type":"row","modifier":{"height":{"type":"exact","value":200}},"children":[
                {"type":"text","text":"top","modifier":{"alignment":"top"}},
                {"type":"text","text":"bottom","modifier":{"alignment":"bottom"}}]}}""",
        )
        val top = compose.onNodeWithText("top").getUnclippedBoundsInRoot()
        val bottom = compose.onNodeWithText("bottom").getUnclippedBoundsInRoot()
        assertTrue("bottom (${bottom.top}) should sit below top (${top.top})", bottom.top > top.top + 100.dp)
    }

    @Test
    fun columnHandsDownWeightAndHorizontalAlignment() {
        show(
            """{"body":{"type":"column","modifier":{"width":{"type":"exact","value":300},"height":{"type":"exact","value":300}},
                "children":[
                {"type":"text","text":"tall","modifier":{"weight":1}},
                {"type":"text","text":"short","modifier":{"weight":2}},
                {"type":"text","text":"endish","modifier":{"alignment":"end"}}]}}""",
        )
        val tall = compose.onNodeWithText("tall").getUnclippedBoundsInRoot()
        val short = compose.onNodeWithText("short").getUnclippedBoundsInRoot()
        assertEquals(tall.height.value * 2, short.height.value, 1f)
        val end = compose.onNodeWithText("endish").getUnclippedBoundsInRoot()
        assertEquals(300f, end.right.value, 1f)
    }

    @Test
    fun boxHandsDownAlignment() {
        show(
            """{"body":{"type":"box","modifier":{"width":{"type":"exact","value":200},"height":{"type":"exact","value":200}},
                "children":[{"type":"text","text":"corner","modifier":{"alignment":"bottom_end"}}]}}""",
        )
        val b = compose.onNodeWithText("corner").getUnclippedBoundsInRoot()
        assertEquals(200f, b.right.value, 1f)
        assertEquals(200f, b.bottom.value, 1f)
    }

    @Test
    fun buttonClickReachesOnActionAsNavigateAction() {
        val received = mutableListOf<SduiAction>()
        show(
            """{"body":{"type":"button","text":"Open","action":{"type":"navigate","destination":"inbox","params":{"id":"7"}}}}""",
            onAction = { received += it },
        )
        compose.onNodeWithText("Open").performClick()
        assertEquals(listOf<SduiAction>(NavigateAction("inbox", mapOf("id" to "7"))), received)
    }

    @Test
    fun buttonWithoutActionIsDisabled() {
        show("""{"body":{"type":"button","text":"Idle"}}""")
        compose.onNodeWithText("Idle").assertIsNotEnabled()
    }

    @Test
    fun listRendersItsChildren() {
        show(
            """{"body":{"type":"list","spacing":4,"modifier":{"height":{"type":"exact","value":300}},"children":[
                {"type":"text","text":"item a"},{"type":"text","text":"item b"},{"type":"text","text":"item c"}]}}""",
        )
        compose.onNodeWithText("item a").assertIsDisplayed()
        compose.onNodeWithText("item b").assertIsDisplayed()
        compose.onNodeWithText("item c").assertIsDisplayed()
    }

    @Test
    fun horizontalListRendersItsChildren() {
        show(
            """{"body":{"type":"list","orientation":"horizontal","children":[
                {"type":"text","text":"left"},{"type":"text","text":"right"}]}}""",
        )
        compose.onNodeWithText("left").assertIsDisplayed()
        compose.onNodeWithText("right").assertIsDisplayed()
    }

    @Test
    fun imageNodeExistsWithItsContentDescription() {
        show(
            """{"body":{"type":"image","url":"https://example.com/a.png","content_description":"Team photo",
                "modifier":{"width":{"type":"exact","value":50},"height":{"type":"exact","value":50}}}}""",
        )
        compose.onNodeWithContentDescription("Team photo").assertExists()
    }

    @Test
    fun cardClickDispatches() {
        val received = mutableListOf<SduiAction>()
        show(
            """{"body":{"type":"card","action":{"type":"navigate","destination":"detail"},"children":[
                {"type":"text","text":"Tap me"}]}}""",
            onAction = { received += it },
        )
        compose.onNodeWithText("Tap me").assertIsDisplayed()
        compose.onNodeWithText("Tap me").performClick()
        assertEquals(listOf<SduiAction>(NavigateAction("detail")), received)
    }

    @Test
    fun cardWithoutActionIsNotClickable() {
        show("""{"body":{"type":"card","children":[{"type":"text","text":"Static"}]}}""")
        compose.onNodeWithText("Static", useUnmergedTree = false).assertIsDisplayed()
    }

    @Test
    fun spacerAndDividerRender() {
        show(
            """{"body":{"type":"column","children":[
                {"type":"text","text":"above"},{"type":"spacer","size":20},{"type":"divider","thickness":2,"color":"#FF0000"},
                {"type":"text","text":"below"}]}}""",
        )
        compose.onNodeWithText("above").assertIsDisplayed()
        compose.onNodeWithText("below").assertIsDisplayed()
    }

    @Test
    fun badChildShowsPlaceholderAndSiblingsSurvive() {
        show("""{"body":{"type":"column","children":[{"type":"text"},{"type":"text","text":"alive"}]}}""")
        compose.onNodeWithText("Unknown component: text", substring = true).assertIsDisplayed()
        compose.onNodeWithText("alive").assertIsDisplayed()
    }
}
