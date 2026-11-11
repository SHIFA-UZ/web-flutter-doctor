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
    fun `an exception keeps its type and frames, never its message`() {
        val frames = mapOf("type" to "raw", "frames" to listOf(mapOf("function" to "load", "in_app" to true)))
        val exception =
            PostHogEvent(
                "\$exception",
                "anon",
                mutableMapOf(
                    "\$exception_level" to "error",
                    "\$exception_message" to "GET https://api.shifa.uz/api/patients/12 failed",
                    "\$exception_list" to
                        listOf(
                            mapOf(
                                "type" to "SerializationException",
                                "value" to "Unexpected JSON token at offset 3: {\"name\":\"Aziza\"}",
                                "module" to "kotlinx.serialization",
                                "stacktrace" to frames,
                            ),
                        ),
                ),
            )

        val sent = withApp(exception).properties!!

        assertThat(sent).doesNotContainKey("\$exception_message")
        assertThat(sent["\$exception_list"])
            .isEqualTo(
                listOf(
                    mapOf(
                        "type" to "SerializationException",
                        "module" to "kotlinx.serialization",
                        "stacktrace" to frames,
                    ),
                ),
            )
        assertThat(sent["\$exception_level"]).isEqualTo("error")
        assertThat(sent["app"]).isEqualTo(TRACKING_APP)
    }

    @Test
    fun `the sdk still has the queue field reset clears`() {
        val field = postHogQueueField

        assertThat(field).isNotNull()
        assertThat(PostHogQueueInterface::class.java.isAssignableFrom(field!!.type)).isTrue()
    }
}
