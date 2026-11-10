package shifa.doctor.data.tracking

import com.google.common.truth.Truth.assertThat
import com.posthog.PostHogEvent
import com.posthog.internal.PostHogQueueInterface
import org.junit.Test

class PostHogSinkTest {
    @Test
    fun `the sdk's own events get app doctor and keep their properties`() {
        val installed = PostHogEvent("Application Installed", "anon", mutableMapOf("version" to "1.0.1"))

        val sent = withApp(installed)

        assertThat(sent.properties).containsExactly("version", "1.0.1", "app", TRACKING_APP)
        assertThat(sent.event).isEqualTo("Application Installed")
        assertThat(sent.uuid).isEqualTo(installed.uuid)
    }

    @Test
    fun `an event without properties gets app doctor`() {
        assertThat(withApp(PostHogEvent("Application Opened", "anon")).properties).containsExactly("app", TRACKING_APP)
    }

    @Test
    fun `the sdk still has the queue field reset clears`() {
        val field = postHogQueueField

        assertThat(field).isNotNull()
        assertThat(PostHogQueueInterface::class.java.isAssignableFrom(field!!.type)).isTrue()
    }
}
