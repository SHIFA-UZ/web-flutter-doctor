package shifa.doctor

import androidx.compose.ui.window.ComposeUIViewController

// Called from Swift, so it keeps the type-like name.
@Suppress("ktlint:standard:function-naming")
fun MainViewController() = ComposeUIViewController { App() }
