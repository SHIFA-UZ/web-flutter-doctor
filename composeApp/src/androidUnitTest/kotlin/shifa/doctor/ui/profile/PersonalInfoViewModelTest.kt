@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.profile

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.catalog.CatalogRepository
import shifa.doctor.data.catalog.Profession
import shifa.doctor.data.profile.Gender
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.enterFirstName
import shifa.doctor.generated.resources.nameInvalid
import shifa.doctor.generated.resources.photoUpdated
import shifa.doctor.generated.resources.profileInformationSaved
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.utils.PickedImage
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalInfoViewModelTest {
    private val me = File("src/commonMain/composeResources/files/demo/doctors-me.json").readText()
    private var fail: String? = null
    private var slowCatalog = false
    private val sent = mutableListOf<String>()
    private val engine =
        MockEngine { req ->
            val path = req.url.encodedPath
            val failing = fail
            when {
                failing != null && req.method != HttpMethod.Get ->
                    respond(
                        failing,
                        HttpStatusCode.InternalServerError,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                path.endsWith("public/professions") && slowCatalog -> {
                    kotlinx.coroutines.delay(60_000)
                    respond("[]", HttpStatusCode.OK)
                }
                path.endsWith("public/professions") ->
                    respond(
                        """[{"english":"Cardiologist","uzbek":"Kardiolog"},{"english":"Neurologist","uzbek":"Nevrolog"}]""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                path.endsWith("doctors/me/photo") -> {
                    sent += "photo"
                    respond(
                        """{"photoUrl":"https://files.example/doctors/a-1.jpg"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                req.method == HttpMethod.Patch -> {
                    val body = req.body.toByteArray().decodeToString()
                    sent += body
                    // The backend answers with the whole profile; this one echoes a name change.
                    respond(
                        """{"firstName":"Zarina","lastName":"Karimova","profession":"Cardiologist, Neurologist","photoUrl":"https://files.example/doctors/a-1.jpg"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> respond(me, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
    private val repo = ProfileRepository { createHttpClient({ null }, engine) }
    private val catalog =
        CatalogRepository(
            { createHttpClient({ null }, engine) },
            { """[{"english":"Cardiologist","uzbek":"Kardiolog"},{"english":"Dentist","uzbek":"Stomatolog"}]""" },
        )

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking { repo.refresh() }
    }

    @After fun tearDown() {
        ShellNotice.pending.value = null
        Dispatchers.resetMain()
    }

    private fun settle(check: () -> Boolean) {
        repeat(200) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun opened(): PersonalInfoViewModel {
        val vm = PersonalInfoViewModel(repo, catalog)
        vm.open()
        settle { vm.state.value.catalog.isNotEmpty() }
        return vm
    }

    @Test
    fun `the form starts from the saved profile, professions with their Uzbek labels, nothing changed`() {
        val classToTest = opened()
        val s = classToTest.state.value
        assertThat(s.form.firstName).isEqualTo("Aziza")
        assertThat(s.form.lastName).isEqualTo("Karimova")
        assertThat(s.form.professions).containsExactly(Profession("Cardiologist", "Kardiolog"))
        assertThat(s.dirty).isFalse()
    }

    @Test
    fun `Save sends only the changed keys, then closes with the saved notice`() {
        val classToTest = opened()
        classToTest.edit { it.copy(firstName = "  Zarina  ", dob = LocalDate(1990, 5, 2), gender = Gender.Female) }
        classToTest.toggleProfession(Profession("Neurologist", "Nevrolog"))
        classToTest.save()
        settle { classToTest.state.value.done }
        assertThat(sent.single())
            .isEqualTo(
                """{"firstName":"Zarina","dob":"1990-05-02","gender":"Female","profession":"Cardiologist, Neurologist"}""",
            )
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.profileInformationSaved)
        assertThat(ShellNotice.pending.value?.success).isTrue()
        assertThat(repo.me.value?.profile?.firstName).isEqualTo("Zarina")
        classToTest.closed() // the screen consumed "done" as it closed
        classToTest.open() // opened again later: a clean form from the saved profile
        assertThat(classToTest.state.value.done).isFalse()
        assertThat(classToTest.state.value.form.firstName).isEqualTo("Zarina")
    }

    @Test
    fun `names are checked before anything is sent`() {
        val classToTest = opened()
        classToTest.edit { it.copy(firstName = " ", lastName = "K4rimova") }
        classToTest.save()
        assertThat(classToTest.state.value.firstNameError).isEqualTo(Res.string.enterFirstName)
        assertThat(classToTest.state.value.lastNameError).isEqualTo(Res.string.nameInvalid)
        classToTest.edit { it.copy(firstName = "Oʼgʼiloy", lastName = "Karimova-Saidova") }
        assertThat(classToTest.state.value.firstNameError).isNull()
        classToTest.save()
        settle { classToTest.state.value.done }
        assertThat(sent.single()).isEqualTo("""{"firstName":"Oʼgʼiloy","lastName":"Karimova-Saidova"}""")
    }

    @Test
    fun `Back with changes asks first, Discard drops them, a tab switch keeps them`() {
        val classToTest = opened()
        assertThat(classToTest.back()).isTrue()
        classToTest.edit { it.copy(lastName = "Saidova") }
        classToTest.open() // back from another tab: the edit stays
        assertThat(classToTest.state.value.form.lastName).isEqualTo("Saidova")
        assertThat(classToTest.back()).isFalse()
        assertThat(classToTest.state.value.askDiscard).isTrue()
        assertThat(classToTest.back()).isFalse() // Back on the question closes the question
        assertThat(classToTest.state.value.askDiscard).isFalse()
        classToTest.discard()
        assertThat(classToTest.state.value.done).isTrue()
        classToTest.closed()
        classToTest.open()
        assertThat(classToTest.state.value.form.lastName).isEqualTo("Karimova")
        assertThat(sent).isEmpty()
    }

    @Test
    fun `a failed save keeps the form and shows the request id`() {
        val classToTest = opened()
        fail = """{"status":500,"code":"INTERNAL","requestId":"req-31"}"""
        classToTest.edit { it.copy(firstName = "Zarina") }
        classToTest.save()
        settle { !classToTest.state.value.saving }
        assertThat(classToTest.state.value.banner?.requestId).isEqualTo("req-31")
        assertThat(classToTest.state.value.done).isFalse()
        assertThat(classToTest.state.value.dirty).isTrue()
        assertThat(ShellNotice.pending.value).isNull()
    }

    @Test
    fun `a picked photo uploads at once and shows with a fresh version, a failure keeps the old one`() {
        val classToTest = opened()
        classToTest.upload(PickedImage("photo.jpg", byteArrayOf(1, 2, 3)))
        settle { !classToTest.state.value.uploading }
        val url = classToTest.state.value.photoUrl
        assertThat(url).startsWith("https://files.example/doctors/a-1.jpg?v=")
        assertThat(repo.me.value?.profile?.photoUrl).isEqualTo(url)
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.photoUpdated)
        assertThat(classToTest.state.value.dirty).isFalse() // the photo isn't part of Save
        fail = """{"status":500,"code":"INTERNAL","requestId":"req-32"}"""
        classToTest.upload(PickedImage("photo.jpg", byteArrayOf(4)))
        settle { !classToTest.state.value.uploading }
        assertThat(classToTest.state.value.photoUrl).isEqualTo(url)
        assertThat(classToTest.state.value.banner?.requestId).isEqualTo("req-32")
        classToTest.upload(null) // picker cancelled: nothing happens
        assertThat(sent).containsExactly("photo")
    }

    @Test
    fun `a later save keeps the uploaded photo's version, so the old cached picture doesn't come back`() {
        val classToTest = opened()
        classToTest.upload(PickedImage("photo.jpg", byteArrayOf(1)))
        settle { !classToTest.state.value.uploading }
        val versioned = repo.me.value?.profile?.photoUrl
        classToTest.edit { it.copy(firstName = "Zarina") }
        classToTest.save()
        settle { classToTest.state.value.done }
        assertThat(repo.me.value?.profile?.photoUrl).isEqualTo(versioned)
        assertThat(versioned).contains("?v=")
    }

    @Test
    fun `a refused camera closes the sheet and asks for the permission in Settings`() {
        val classToTest = opened()
        classToTest.ui { it.copy(photoSheet = true) }
        classToTest.cameraDenied()
        assertThat(classToTest.state.value.photoSheet).isFalse()
        assertThat(classToTest.state.value.cameraDenied).isTrue()
    }

    @Test
    fun `the bundled professions show at once while the backend is slow`() {
        slowCatalog = true
        val classToTest = PersonalInfoViewModel(repo, catalog)
        classToTest.open()
        settle { classToTest.state.value.catalog.isNotEmpty() }
        assertThat(classToTest.state.value.catalog.map { it.english }).containsExactly("Cardiologist", "Dentist")
        assertThat(classToTest.state.value.catalogLoading).isTrue()
        assertThat(classToTest.state.value.form.professions).containsExactly(Profession("Cardiologist", "Kardiolog"))
    }

    @Test
    fun `Save waits while a photo uploads`() {
        val classToTest = opened()
        classToTest.edit { it.copy(firstName = "Zarina") }
        classToTest.ui { it.copy(uploading = true) }
        classToTest.save()
        assertThat(classToTest.state.value.saving).isFalse()
        assertThat(sent).isEmpty()
    }
}
