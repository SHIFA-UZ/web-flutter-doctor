package shifa.doctor.data.ai

import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.model.ApiException
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.aiRateLimit
import shifa.doctor.generated.resources.aiUnavailable
import shifa.doctor.generated.resources.intelTookTooLong

/**
 * The message for a failed AI call (02 §3.2 `AiStreamException`, flow 03 README §4): the daily limit, a reply that took
 * too long (the model, not the connection), the AI service down (`AI_UNAVAILABLE`), else the usual code mapping with
 * the call's own [fallback].
 */
fun Throwable.toAiMessage(fallback: StringResource): StringResource =
    when {
        this is ApiException.RateLimit -> Res.string.aiRateLimit
        this is ApiException.NetworkError && timeout -> Res.string.intelTookTooLong
        this is ApiException && code == "AI_UNAVAILABLE" -> Res.string.aiUnavailable
        else -> toUserMessage(fallback)
    }
