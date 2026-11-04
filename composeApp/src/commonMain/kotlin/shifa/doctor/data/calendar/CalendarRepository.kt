package shifa.doctor.data.calendar

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.api.rows
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.model.CalendarEntryDto
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** A cached day: the date in the practice zone, whose calendar (null = the signed-in doctor's) and which location. */
data class DayKey(
    val date: LocalDate,
    val doctorId: Long? = null,
    val locationId: Long? = null,
)

/**
 * The whole cache in one value, so every change is atomic: [days] for the screens, when each was read, the newest
 * request per day (an older answer never overwrites a newer one) and the sign-out generation.
 */
data class CalendarCache(
    val days: Map<DayKey, List<CalendarEntry>> = emptyMap(),
    internal val loadedAt: Map<DayKey, TimeMark> = emptyMap(),
    internal val requests: Map<DayKey, Long> = emptyMap(),
    internal val generation: Int = 0,
)

/**
 * The calendar's days (CAL-1, 03 §5–6): `GET /calendar?day=YYYY-MM-DD[&doctorId=][&locationId=]`, cached per [DayKey]
 * and read again only after [throttle] unless forced (a mutation forces the days it touched). Reads of the same day in
 * flight share one request. [state] is what the screens observe; [clear] on sign-out drops the cache and every answer
 * still in flight.
 */
class CalendarRepository(
    private val client: () -> HttpClient = { httpClient },
    private val time: TimeSource = TimeSource.Monotonic,
    private val throttle: Duration = 60.seconds,
) {
    private val _state = MutableStateFlow(CalendarCache())
    val state: StateFlow<CalendarCache> = _state.asStateFlow()
    private val inFlight = MutableStateFlow<Map<DayKey, CompletableDeferred<Result<List<CalendarEntry>>>>>(emptyMap())
    private val nextRequest = atomic(0L)

    /** The day from cache when fresh, else from the backend (and cached). */
    suspend fun day(
        key: DayKey,
        force: Boolean = false,
    ): Result<List<CalendarEntry>> {
        val cached = _state.value.days[key]
        if (cached != null && fresh(key) && !force) return Result.success(cached)
        // A plain read joins the one already running for this day; a forced one always asks again.
        if (!force) inFlight.value[key]?.let { return it.await() }
        val mine = CompletableDeferred<Result<List<CalendarEntry>>>()
        inFlight.update { it + (key to mine) }
        val request = nextRequest.incrementAndGet()
        val generation = _state.value.generation
        _state.update { it.copy(requests = it.requests + (key to request)) }
        val result = fetch(key)
        result.onSuccess { entries ->
            _state.update {
                // Signed out meanwhile, or a newer request for this day started: this answer is stale.
                if (it.generation != generation || it.requests[key] != request) {
                    it
                } else {
                    it.copy(days = it.days + (key to entries), loadedAt = it.loadedAt + (key to time.markNow()))
                }
            }
        }
        mine.complete(result)
        inFlight.update { if (it[key] === mine) it - key else it }
        return result
    }

    /** The days being read right now, first reads and refreshes alike (a change re-reads them: they may predate it). */
    fun pending(): Set<DayKey> = inFlight.value.keys

    /** Every day of [dates] not cached or not fresh, four at a time (03 §5); failures stay unread for the next try. */
    suspend fun month(
        dates: List<LocalDate>,
        doctorId: Long? = null,
        locationId: Long? = null,
    ) {
        val stale = dates.map { DayKey(it, doctorId, locationId) }.filter { !fresh(it) }
        stale.chunked(BATCH).forEach { batch ->
            coroutineScope { batch.map { async { day(it) } }.awaitAll() }
        }
    }

    /** A colleague's day for a quick look, not cached (the staff switcher keeps its own). */
    suspend fun previewDay(
        date: LocalDate,
        doctorId: Long,
    ): Result<List<CalendarEntry>> = fetch(DayKey(date, doctorId))

    /** Forget when [keys] were read, so the next read (or month) goes to the backend. */
    fun invalidate(vararg keys: DayKey) = _state.update { it.copy(loadedAt = it.loadedAt - keys.toSet()) }

    fun clear() = _state.update { CalendarCache(generation = it.generation + 1) }

    /**
     * Every day is stale (the schedule changed): cached days are read again when shown, and answers still on their way
     * (asked before the change) are dropped instead of cached; the days shown keep their entries until then.
     */
    fun invalidateAll() {
        inFlight.value = emptyMap()
        _state.update {
                s ->
            s.copy(loadedAt = emptyMap(), requests = s.requests.mapValues { nextRequest.incrementAndGet() })
        }
    }

    private fun fresh(key: DayKey): Boolean {
        val s = _state.value
        return key in s.days && s.loadedAt[key]?.let { it.elapsedNow() < throttle } == true
    }

    private suspend fun fetch(key: DayKey): Result<List<CalendarEntry>> =
        apiCall<JsonElement> {
            client().get(ApiConfig.Endpoints.CALENDAR) {
                parameter("day", key.date.toString())
                key.doctorId?.let { parameter("doctorId", it) }
                key.locationId?.let { parameter("locationId", it) }
            }
        }.map(::entries)

    private companion object {
        const val BATCH = 4

        /** An array, or one wrapped in `content` / `data` / `entries` (03 §6); each row read on its own. */
        fun entries(json: JsonElement): List<CalendarEntry> {
            val rows =
                (json as? JsonArray)
                    ?: (json as? JsonObject)?.let {
                            o ->
                        listOf("content", "data", "entries").firstNotNullOfOrNull { o[it] as? JsonArray }
                    }
                    ?: JsonArray(emptyList())
            return rows.toList().rows(
                CalendarEntryDto.serializer(),
                "calendar",
                CalendarEntry::from,
            ).sortedBy { it.start }
        }
    }
}

val calendarRepository by lazy { CalendarRepository() }
