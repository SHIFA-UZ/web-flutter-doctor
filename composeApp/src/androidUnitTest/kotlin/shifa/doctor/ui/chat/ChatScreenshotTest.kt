package shifa.doctor.ui.chat

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class ChatScreenshotTest {
    @Test
    fun list() = captureRoboImage("screenshots/Chat_list.png") { ChatListPreview() }

    @Test
    fun loading() = captureRoboImage("screenshots/Chat_loading.png") { ChatListLoadingPreview() }

    @Test
    fun empty() = captureRoboImage("screenshots/Chat_empty.png") { ChatListEmptyPreview() }

    @Test
    fun search() = captureRoboImage("screenshots/Chat_search.png") { ChatListSearchPreview() }

    @Test
    fun thread() = captureRoboImage("screenshots/Chat_thread.png") { ThreadPreview() }

    @Test
    fun threadEmpty() = captureRoboImage("screenshots/Chat_threadEmpty.png") { ThreadEmptyPreview() }

    @Test
    fun threadLoading() = captureRoboImage("screenshots/Chat_threadLoading.png") { ThreadLoadingPreview() }

    @Test
    fun threadImages() = captureRoboImage("screenshots/Chat_threadImages.png") { ThreadImagesPreview() }
}
