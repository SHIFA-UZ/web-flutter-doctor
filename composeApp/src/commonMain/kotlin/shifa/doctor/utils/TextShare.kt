package shifa.doctor.utils

import androidx.compose.runtime.Composable

/** The system share sheet for plain text (Shifa AI's Share on a reply). */
@Composable
expect fun rememberTextShare(): (text: String) -> Unit
