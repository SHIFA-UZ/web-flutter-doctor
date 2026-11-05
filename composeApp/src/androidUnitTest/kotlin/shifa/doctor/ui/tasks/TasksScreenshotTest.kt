package shifa.doctor.ui.tasks

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class TasksScreenshotTest {
    @Test
    fun list() = captureRoboImage("screenshots/Tasks_list.png") { TasksPreview() }

    @Test
    fun loading() = captureRoboImage("screenshots/Tasks_loading.png") { TasksLoadingPreview() }

    @Test
    fun empty() = captureRoboImage("screenshots/Tasks_empty.png") { TasksEmptyPreview() }

    @Test
    fun filterEmpty() = captureRoboImage("screenshots/Tasks_filterEmpty.png") { TasksFilterEmptyPreview() }

    @Test
    fun failed() = captureRoboImage("screenshots/Tasks_failed.png") { TasksFailedPreview() }

    @Test
    fun detail() = captureRoboImage("screenshots/Task_detail.png") { TaskDetailPreview() }

    @Test
    fun checkIn() = captureRoboImage("screenshots/Task_checkIn.png") { TaskCheckInSheetPreview() }

    @Test
    fun cancel() = captureRoboImage("screenshots/Task_cancel.png") { TaskCancelPreview() }

    @Test
    fun notFound() = captureRoboImage("screenshots/Task_notFound.png") { TaskNotFoundPreview() }
}
