package shifa.doctor.ui.widgets

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// One image per preview in WorkspacePreviews.kt; record with :composeApp:recordRoborazziDebug.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h1600dp-xhdpi")
class WorkspaceScreenshotTest {
    @Test
    fun table() = captureRoboImage("screenshots/Workspace_table.png") { TablePreview() }

    @Test
    fun table_loading() = captureRoboImage("screenshots/Workspace_table_loading.png") { TableLoadingPreview() }

    @Test
    fun table_empty() = captureRoboImage("screenshots/Workspace_table_empty.png") { TableEmptyPreview() }

    @Test
    fun agenda() = captureRoboImage("screenshots/Workspace_agenda.png") { AgendaPreview() }

    @Test
    fun month_calendar() = captureRoboImage("screenshots/Workspace_month_calendar.png") { MonthCalendarPreview() }

    @Test
    fun dental_chart() = captureRoboImage("screenshots/Workspace_dental_chart.png") { DentalChartPreview() }

    @Test
    fun form_and_action_bar() =
        captureRoboImage("screenshots/Workspace_form_and_action_bar.png") {
            FormAndActionBarPreview()
        }

    @Test
    fun plans() = captureRoboImage("screenshots/Workspace_plans.png") { PlansPreview() }

    @Test
    fun workspace_chrome() = captureRoboImage("screenshots/Workspace_workspace_chrome.png") { WorkspaceChromePreview() }
}
