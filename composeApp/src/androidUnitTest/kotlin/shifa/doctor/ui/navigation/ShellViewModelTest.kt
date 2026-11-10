@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.navigation

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.badges.BadgesRepository
import shifa.doctor.data.chat.ChatReads
import shifa.doctor.data.clinics.ClinicsRepository
import shifa.doctor.data.model.VisitKind
import shifa.doctor.data.notifications.NotificationReads
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.subscription.DoctorFeature
import shifa.doctor.data.subscription.DoctorTier
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.tasksNotOnPlan
import shifa.doctor.stopAll
import shifa.doctor.ui.notifications.NotificationGo

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    private var startTab = "calendar"
    private var tier = "PREMIUM"

    // What the backend lists for the tier.
    private fun features() =
        DoctorFeature.entries
            .filter { it.minTier <= DoctorTier.valueOf(tier.lowercase().replaceFirstChar(Char::uppercase)) }
            .joinToString(",") { "\"${it.key}\"" }

    private var clinics = """[{"clinicId":1,"name":"Shifa Demo Clinic"}]"""
    private var hold = CompletableDeferred(Unit)
    private val paths = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var chat = 1
    private var notifications = 140
    private val client by lazy {
        createHttpClient(
            { null },
            MockEngine { req ->
                paths += req.url.encodedPath
                val body =
                    if (req.url.encodedPath.endsWith("me/clinics")) {
                        clinics
                    } else if (req.url.encodedPath.endsWith("messages/unread-count")) {
                        """{"count":${chat++}}"""
                    } else if (req.url.encodedPath.endsWith("unread/count")) {
                        """{"count":${notifications++}}"""
                    } else if (req.url.encodedPath.endsWith("/tasks")) {
                        """[{"id":1,"status":"ACTIVE"},{"id":2,"status":"active"},{"id":3,"status":"DRAFT"},{"id":4,"status":"COMPLETED"}]"""
                    } else if (req.url.encodedPath.endsWith("me/profile")) {
                        """{"id":5,"role":"CLINIC_STAFF","firstName":"Dilfuza","lastName":"Y","photoUrl":"https://x/p.jpg"}"""
                    } else {
                        hold.await()
                        """{"profile":{},"settings":{"defaultStartTab":"$startTab"},"subscription":{"tier":"$tier","features":[${features()}]}}"""
                    }
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
    }

    private val main = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(main)

    @After fun tearDown() {
        created.stopAll()
        Dispatchers.resetMain()
    }

    private val profile by lazy { ProfileRepository { client } }

    private var scheduleReads = 0

    private fun vm(
        staff: Boolean = false,
        startPath: String? = null,
    ) = ShellViewModel(
        staff,
        profile,
        ClinicsRepository({
            client
        }, com.russhwolf.settings.MapSettings(), { "u1" }),
        BadgesRepository { client },
        startPath = startPath,
    ) { scheduleReads++ }.also { created += it }

    private fun pushVm(
        pushes: kotlinx.coroutines.flow.Flow<Map<String, String>> = kotlinx.coroutines.flow.emptyFlow(),
    ) = ShellViewModel(
        false,
        profile,
        ClinicsRepository({ client }, com.russhwolf.settings.MapSettings(), { "u1" }),
        BadgesRepository { client },
        notifications = shifa.doctor.data.notifications.NotificationsRepository { client },
        pushes = pushes,
        profileWaitMs = 1_000,
    ) { scheduleReads++ }.also { created += it }

    private fun ShellViewModel.settle() {
        repeat(200) {
            if (state.value.gate.known) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `moving on clears a success notice, a warning stays until closed`() {
        val classToTest = vm()
        classToTest.settle()
        ShellNotice.pending.value = Notice(Res.string.tasksNotOnPlan, success = true)
        classToTest.push(ShellRoute.Language)
        assertThat(ShellNotice.pending.value).isNull()
        ShellNotice.pending.value = Notice(Res.string.tasksNotOnPlan)
        classToTest.select(ShellTab.Chat)
        classToTest.push(ShellRoute.Language)
        assertThat(ShellNotice.pending.value).isEqualTo(Notice(Res.string.tasksNotOnPlan))
        ShellNotice.pending.value = null
    }

    @Test
    fun `the saved start tab opens once the profile is in`() {
        val classToTest = vm()
        classToTest.settle()
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Calendar)
        assertThat(classToTest.state.value.hasClinic).isTrue()
    }

    @Test
    fun `a start tab the doctor can't use falls back to Home`() {
        startTab = "tasks"
        tier = "BASIC"
        val basic = vm()
        basic.settle()
        assertThat(basic.state.value.tab).isEqualTo(ShellTab.Home)
        startTab = "clinic"
        tier = "PREMIUM"
        clinics = "[]"
        val solo = vm()
        solo.settle()
        assertThat(solo.state.value.tab).isEqualTo(ShellTab.Home)
        assertThat(solo.state.value.hasClinic).isFalse()
    }

    @Test
    fun `a web address opens its screen over its tab's list instead of the start tab`() {
        val patient = vm(startPath = "/patients/12")
        patient.settle()
        assertThat(patient.state.value.stack)
            .containsExactly(ShellRoute.Root(ShellTab.Patients), ShellRoute.Patient(12))
            .inOrder()
        assertThat(shellPath(patient.state.value)).isEqualTo("/patients/12")
        patient.push(ShellRoute.Form(12, null, 1)) // a screen with no address of its own keeps the patient's
        assertThat(shellPath(patient.state.value)).isEqualTo("/patients/12")
        patient.back()
        patient.back()
        assertThat(shellPath(patient.state.value)).isEqualTo("/patients")
        patient.push(ShellRoute.Patient(12, clinicId = 1)) // a clinic's scope isn't in the address
        assertThat(shellPath(patient.state.value)).isEqualTo("/patients")

        val thread = vm(startPath = "/chat/3")
        thread.settle()
        assertThat((thread.state.value.route as ShellRoute.Conversation).id).isEqualTo(3)
        assertThat(shellPath(thread.state.value)).isEqualTo("/chat/3")

        val task = vm(startPath = "/tasks/5")
        task.settle()
        assertThat((task.state.value.route as ShellRoute.Task).id).isEqualTo(5)

        val tab = vm(startPath = "/reports")
        tab.settle()
        assertThat(tab.state.value.tab).isEqualTo(ShellTab.Reports)

        val unknown = vm(startPath = "/nothing/here")
        unknown.settle()
        assertThat(unknown.state.value.tab).isEqualTo(ShellTab.Calendar) // the saved start tab

        val staff = vm(staff = true, startPath = "/patients/12") // not a staff tab
        repeat(200) { if (staff.state.value.avatarUrl == null) Thread.sleep(10) }
        assertThat(staff.state.value.tab).isEqualTo(ShellTab.Clinic)
        assertThat(staff.state.value.stack).containsExactly(ShellRoute.Root(ShellTab.Clinic))
    }

    @Test
    fun `a tab picked before the profile arrives wins`() {
        hold = CompletableDeferred()
        val classToTest = vm()
        classToTest.select(ShellTab.Patients)
        hold.complete(Unit)
        classToTest.settle()
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Patients)
    }

    @Test
    fun `each tab keeps its stack, re-tapping goes to its root, Back walks home then out`() {
        val classToTest = vm()
        classToTest.settle()
        classToTest.select(ShellTab.Profile)
        classToTest.push(ShellRoute.Debug)
        assertThat(classToTest.state.value.barVisible).isFalse()
        classToTest.select(ShellTab.Chat)
        classToTest.select(ShellTab.Profile)
        assertThat(classToTest.state.value.route).isEqualTo(ShellRoute.Debug) // kept while away
        classToTest.select(ShellTab.Profile)
        assertThat(classToTest.state.value.route).isEqualTo(ShellRoute.Root(ShellTab.Profile)) // re-tap: root
        classToTest.push(ShellRoute.Language)
        classToTest.openMore()
        assertThat(classToTest.back()).isTrue() // closes More
        assertThat(classToTest.state.value.moreOpen).isFalse()
        assertThat(classToTest.back()).isTrue() // pops Language
        assertThat(classToTest.back()).isTrue() // Profile root → Home
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Home)
        assertThat(classToTest.back()).isFalse() // Home root: the app may close
    }

    @Test
    fun `a search result closes the search and opens the patient over the directory`() {
        val classToTest = vm()
        classToTest.settle()
        classToTest.push(ShellRoute.Search(1))
        classToTest.openPatientFromSearch(16)
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Patients)
        assertThat(classToTest.state.value.route).isEqualTo(ShellRoute.Patient(16))
        assertThat(classToTest.back()).isTrue() // the directory, not the search
        assertThat(classToTest.state.value.route).isEqualTo(ShellRoute.Root(ShellTab.Patients))
        classToTest.select(ShellTab.Home)
        assertThat(classToTest.state.value.route).isEqualTo(ShellRoute.Root(ShellTab.Home))
    }

    @Test
    fun `a push before the profile arrives keeps the doctor where they went`() {
        hold = CompletableDeferred()
        val classToTest = vm()
        classToTest.push(ShellRoute.Language)
        hold.complete(Unit)
        classToTest.settle()
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Home)
        assertThat(classToTest.state.value.route).isEqualTo(ShellRoute.Language)
    }

    @Test
    fun `a gated tab picked early opens once it is allowed`() {
        hold = CompletableDeferred()
        val classToTest = vm()
        classToTest.select(ShellTab.Tasks) // plan unknown yet
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Home)
        hold.complete(Unit)
        classToTest.settle()
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Tasks)
    }

    @Test
    fun `clinic staff start on Clinic with four tabs, never ask for the doctor profile, Back goes to Clinic`() {
        val classToTest = vm(staff = true)
        repeat(200) { if (classToTest.state.value.avatarUrl == null) Thread.sleep(10) }
        val s = classToTest.state.value
        assertThat(s.tab).isEqualTo(ShellTab.Clinic)
        assertThat(
            s.barTabs,
        ).containsExactly(ShellTab.Chat, ShellTab.Clinic, ShellTab.Notifications, ShellTab.Profile).inOrder()
        assertThat(s.avatarUrl).isEqualTo("https://x/p.jpg")
        assertThat(paths.none { it.endsWith("doctors/me") }).isTrue()
        classToTest.select(ShellTab.Tasks) // not a staff tab
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Clinic)
        classToTest.select(ShellTab.Chat)
        assertThat(classToTest.back()).isTrue()
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Clinic)
        assertThat(classToTest.back()).isFalse()
    }

    private fun waitFor(check: () -> Boolean) {
        repeat(200) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `Send message opens the patient's thread over the chat list, the patient stays in Patients`() {
        val classToTest = vm()
        classToTest.settle()
        classToTest.select(ShellTab.Patients)
        classToTest.push(ShellRoute.Patient(16))
        classToTest.select(ShellTab.Chat)
        classToTest.push(ShellRoute.Conversation(3, 1)) // whatever the Chat stack held goes
        classToTest.select(ShellTab.Patients)
        val chat = ShellRoute.PatientChat(16, "Dilnoza", null, 2)
        classToTest.openChatWithPatient(chat)
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Chat)
        assertThat(classToTest.state.value.stack).containsExactly(ShellRoute.Root(ShellTab.Chat), chat).inOrder()
        assertThat(classToTest.back()).isTrue() // the list, not the patient
        assertThat(classToTest.state.value.route).isEqualTo(ShellRoute.Root(ShellTab.Chat))
        classToTest.select(ShellTab.Patients)
        assertThat(classToTest.state.value.route).isEqualTo(ShellRoute.Patient(16))
    }

    @Test
    fun `a task from a notification opens over the Tasks list, without the plan only the note`() {
        val classToTest = vm()
        classToTest.settle()
        classToTest.select(ShellTab.Notifications)
        classToTest.openTask(501, 1)
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Tasks)
        assertThat(
            classToTest.state.value.stack,
        ).containsExactly(ShellRoute.Root(ShellTab.Tasks), ShellRoute.Task(501, 1)).inOrder()
        assertThat(classToTest.back()).isTrue()
        assertThat(classToTest.state.value.route).isEqualTo(ShellRoute.Root(ShellTab.Tasks))
        tier = "PRO"
        val pro = vm()
        pro.settle()
        pro.select(ShellTab.Notifications)
        pro.openTask(501, 1)
        assertThat(pro.state.value.tab).isEqualTo(ShellTab.Notifications)
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.tasksNotOnPlan)
        ShellNotice.pending.value = null
    }

    @Test
    fun `Open Calendar shows the Calendar's root, not what it had open`() {
        val classToTest = vm()
        classToTest.settle()
        classToTest.select(ShellTab.Calendar)
        classToTest.push(ShellRoute.Reports)
        classToTest.select(ShellTab.Notifications)
        classToTest.openCalendar()
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Calendar)
        assertThat(classToTest.state.value.route).isEqualTo(ShellRoute.Root(ShellTab.Calendar))
    }

    @Test
    fun `the bell's count is read again on a read in the inbox, never polled`() {
        val classToTest = vm()
        classToTest.settle()
        classToTest.refreshNotifications()
        waitFor { classToTest.state.value.notificationsKnown }
        val first = classToTest.state.value.notificationsUnread
        NotificationReads.changed.tryEmit(Unit)
        waitFor { classToTest.state.value.notificationsUnread == first + 1 }
        assertThat(classToTest.state.value.notificationsUnread).isEqualTo(first + 1)
    }

    @Test
    fun `a conversation read drops the chat badge at once, not on the next poll`() {
        val classToTest = vm()
        classToTest.settle()
        assertThat(classToTest.state.value.chatUnread).isEqualTo(0)
        ChatReads.changed.tryEmit(Unit)
        waitFor { classToTest.state.value.chatUnread == 1 }
        assertThat(classToTest.state.value.chatUnread).isEqualTo(1)
    }

    @Test
    fun `chat is polled every 10 s only in front, notifications on each return, active tasks once`() {
        val classToTest = vm()
        classToTest.settle()
        waitFor { classToTest.state.value.activeTasks == 2 }
        assertThat(classToTest.state.value.activeTasks).isEqualTo(2)
        assertThat(classToTest.state.value.followUps).isEqualTo(3)
        classToTest.onForeground()
        classToTest.refreshNotifications()
        waitFor { classToTest.state.value.chatUnread == 1 && classToTest.state.value.notificationsUnread == 140 }
        assertThat(classToTest.state.value.notificationsUnread).isEqualTo(140)
        main.scheduler.advanceTimeBy(10_001)
        waitFor { classToTest.state.value.chatUnread == 2 }
        assertThat(classToTest.state.value.chatUnread).isEqualTo(2)
        classToTest.onBackground()
        main.scheduler.advanceTimeBy(60_000)
        Thread.sleep(100)
        assertThat(classToTest.state.value.chatUnread).isEqualTo(2) // nothing polled in the background
    }

    @Test
    fun `a doctor on Tasks who loses Tasks goes Home with a note`() =
        kotlinx.coroutines.test.runTest {
            startTab = "tasks"
            val classToTest = vm()
            classToTest.settle()
            assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Tasks)
            ShellNotice.pending.value = null
            tier = "PRO" // the admin changed the plan; the next profile read brings it
            profile.refresh()
            waitFor { classToTest.state.value.tab == ShellTab.Home }
            assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Home)
            assertThat(ShellNotice.pending.value).isEqualTo(Notice(Res.string.tasksNotOnPlan))
            ShellNotice.pending.value = null
        }

    @Test
    fun `Shifa Intelligence takes over the content, a tab tap or Back leaves it, the plan gates it`() {
        val classToTest = vm()
        classToTest.settle()
        classToTest.select(ShellTab.Patients)
        classToTest.openIntelligence()
        assertThat(classToTest.state.value.intelligence).isTrue()
        assertThat(classToTest.back()).isTrue()
        assertThat(classToTest.state.value.intelligence).isFalse()
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Patients) // the tab underneath is untouched
        classToTest.openIntelligence()
        classToTest.select(ShellTab.Chat)
        assertThat(classToTest.state.value.intelligence).isFalse()
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Chat)
        // A card's "Open calendar" leaves Intelligence for the calendar.
        classToTest.openIntelligence()
        classToTest.openCalendar()
        assertThat(classToTest.state.value.intelligence).isFalse()
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Calendar)
        // Every way out reads the schedule again (Back, a tab tap, the screen's own back from the welcome).
        classToTest.openIntelligence()
        classToTest.leaveIntelligence()
        waitFor { scheduleReads == 4 }
        assertThat(scheduleReads).isEqualTo(4)
        tier = "BASIC"
        val basic = vm()
        basic.settle()
        basic.openIntelligence()
        assertThat(basic.state.value.intelligence).isFalse()
    }

    @Test
    fun `a visit or document opened from Intelligence sits over it, Back returns to the thread`() {
        val classToTest = vm()
        classToTest.settle()
        classToTest.select(ShellTab.Patients)
        val before = classToTest.state.value.stack
        classToTest.openIntelligence()
        classToTest.push(ShellRoute.Document(12, 901, "CBC"))
        classToTest.push(ShellRoute.Visit(102, VisitKind.InPerson, 1))
        assertThat(classToTest.state.value.overIntelligence).hasSize(2)
        assertThat(classToTest.state.value.barVisible).isFalse()
        assertThat(classToTest.state.value.stack).isEqualTo(before) // the tab's own stack is untouched
        assertThat(classToTest.back()).isTrue()
        assertThat(classToTest.back()).isTrue()
        assertThat(classToTest.state.value.intelligence).isTrue()
        assertThat(classToTest.state.value.overIntelligence).isEmpty()
        assertThat(classToTest.state.value.barVisible).isTrue()
        // Leaving with a screen still over Intelligence: the next opening starts at the thread.
        classToTest.push(ShellRoute.Document(12, 901, "CBC"))
        classToTest.select(ShellTab.Home)
        classToTest.openIntelligence()
        assertThat(classToTest.state.value.overIntelligence).isEmpty()
    }

    @Test
    fun `coming back reads the clinics and the profile again`() {
        val classToTest = vm()
        classToTest.settle()
        assertThat(classToTest.state.value.hasClinic).isTrue()
        clinics = "[]"
        tier = "PRO"
        classToTest.onResume()
        waitFor { classToTest.state.value.hasClinic == false && !classToTest.state.value.canTasks }
        assertThat(classToTest.state.value.hasClinic).isFalse()
        assertThat(classToTest.state.value.canTasks).isFalse()
    }

    @Test
    fun `a tapped chat push opens its conversation over Home at its root and reads its row`() {
        val classToTest = pushVm()
        classToTest.settle()
        classToTest.select(ShellTab.Chat)
        classToTest.openPush(mapOf("type" to "CHAT_MESSAGE", "notificationId" to "7", "conversationId" to "42"))
        waitFor { classToTest.state.value.pushGo != null }
        assertThat(classToTest.state.value.pushGo).isEqualTo(NotificationGo.Conversation(42))
        assertThat(paths).contains("/api/notifications/7/read")
        classToTest.pushOpened()
        assertThat(classToTest.state.value.pushGo).isNull()
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Home)
        assertThat(classToTest.state.value.stack).hasSize(1)
    }

    @Test
    fun `a push without its row's id opens the inbox`() {
        val classToTest = pushVm()
        classToTest.settle()
        classToTest.openPush(mapOf("type" to "GENERAL"))
        waitFor { classToTest.state.value.pushGo != null }
        assertThat(classToTest.state.value.pushGo).isEqualTo(NotificationGo.Inbox)
    }

    @Test
    fun `a push while the app is open reads the counts again, and the schedule for a visit`() {
        val pushes = kotlinx.coroutines.flow.MutableSharedFlow<Map<String, String>>(extraBufferCapacity = 4)
        val classToTest = pushVm(pushes)
        classToTest.settle()
        val bell = { paths.count { it.endsWith("notifications/unread/count") } }
        val chat = { paths.count { it.endsWith("messages/unread-count") } }
        val (bell0, chat0) = bell() to chat()
        pushes.tryEmit(mapOf("type" to "APPOINTMENT_BOOKED_BY_PATIENT"))
        waitFor { scheduleReads == 1 && bell() > bell0 }
        assertThat(scheduleReads).isEqualTo(1)
        assertThat(bell()).isGreaterThan(bell0)
        pushes.tryEmit(mapOf("type" to "CHAT_MESSAGE"))
        waitFor { chat() > chat0 }
        assertThat(chat()).isGreaterThan(chat0)
        assertThat(scheduleReads).isEqualTo(1)
    }
}
