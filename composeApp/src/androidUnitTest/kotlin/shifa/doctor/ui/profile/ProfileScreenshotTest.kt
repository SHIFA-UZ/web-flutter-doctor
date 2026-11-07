package shifa.doctor.ui.profile

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class ProfileScreenshotTest {
    @Test
    @Config(qualifiers = "w402dp-h1200dp-xhdpi")
    fun root() = captureRoboImage("screenshots/Profile_root.png") { ProfileRootPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1200dp-xhdpi")
    fun incomplete() = captureRoboImage("screenshots/Profile_incomplete.png") { ProfileIncompletePreview() }

    @Test
    fun staff() = captureRoboImage("screenshots/Profile_staff.png") { ProfileStaffPreview() }

    @Test
    fun error() = captureRoboImage("screenshots/Profile_error.png") { ProfileErrorPreview() }

    @Test
    fun personalInfo() = captureRoboImage("screenshots/PersonalInfo_edited.png") { PersonalInfoPreview() }

    @Test
    fun personalInfoErrors() = captureRoboImage("screenshots/PersonalInfo_errors.png") { PersonalInfoErrorsPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1300dp-xhdpi")
    fun contact() = captureRoboImage("screenshots/Contact_phoneError.png") { ContactPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1500dp-xhdpi")
    fun billing() = captureRoboImage("screenshots/Billing_ibanError.png") { BillingPreview() }

    @Test
    fun security() = captureRoboImage("screenshots/Security_password.png") { SecurityPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1300dp-xhdpi")
    fun extended() = captureRoboImage("screenshots/ExtendedProfile_tags.png") { ExtendedProfilePreview() }

    @Test
    @Config(qualifiers = "w402dp-h1200dp-xhdpi")
    fun services() = captureRoboImage("screenshots/Services_list.png") { ServicesListPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1500dp-xhdpi")
    fun serviceEditor() = captureRoboImage("screenshots/Services_editor.png") { ServiceEditorPreview() }

    @Test
    fun serviceGroups() = captureRoboImage("screenshots/Services_groups.png") { ServiceGroupsPreview() }

    @Test
    fun settings() = captureRoboImage("screenshots/Settings_doctor.png") { SettingsPreview() }

    @Test
    fun language() = captureRoboImage("screenshots/Language_settings.png") { LanguagePreview() }

    @Test
    fun about() = captureRoboImage("screenshots/About.png") { AboutPreview() }

    @Test
    fun privacy() = captureRoboImage("screenshots/Privacy.png") { PrivacyPreview() }

    @Test
    fun privacyCode() = captureRoboImage("screenshots/Privacy_code.png") { PrivacyCodePreview() }
}
