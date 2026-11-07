package shifa.doctor.ui.widgets

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * WEB-06/07: true when the shell shows the sidebar or the icon rail (768 wide and more) instead of the phone's floating
 * bar. Screens drop the bar's bottom clearance and use their desktop arrangement (DaApp `web`); how many columns fit
 * is each screen's own measure.
 */
val LocalWideLayout = staticCompositionLocalOf { false }
