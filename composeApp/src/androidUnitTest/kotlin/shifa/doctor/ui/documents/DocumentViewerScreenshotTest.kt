package shifa.doctor.ui.documents

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class DocumentViewerScreenshotTest {
    @Test
    fun loading() = captureRoboImage("screenshots/DocumentViewer_loading.png") { ViewerLoadingPreview() }

    @Test
    fun noAccess() = captureRoboImage("screenshots/DocumentViewer_noAccess.png") { ViewerNoAccessPreview() }

    @Test
    fun cantPreview() = captureRoboImage("screenshots/DocumentViewer_cantPreview.png") { ViewerCantPreviewPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1500dp-xhdpi")
    fun uploadDetails() = captureRoboImage("screenshots/Upload_details.png") { UploadDetailsPreview() }

    @Test
    fun uploadStatus() = captureRoboImage("screenshots/Upload_status.png") { UploadStatusPreview() }
}
