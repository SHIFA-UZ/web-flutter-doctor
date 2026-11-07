- **[P2] Unsupported pickers remain exposed as dead controls.** [DocumentPicker.wasmJs.kt:10](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/wasmJsMain/kotlin/shifa/doctor/utils/DocumentPicker.wasmJs.kt:10) and [ImagePicker.wasmJs.kt:7](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/wasmJsMain/kotlin/shifa/doctor/utils/ImagePicker.wasmJs.kt:7) immediately report cancellation. `DocumentUpload.kt:536–552` and `ThreadScreen.kt:239–246` still wire these into visible actions, so selecting an attachment silently does nothing. WEB-04 explicitly requires unsupported features to be hidden; gate these controls until implemented.

- **[P2] Successfully opened links report failure.** [Browser.wasmJs.kt:58](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/wasmJsMain/kotlin/shifa/doctor/utils/Browser.wasmJs.kt:58) tests `window.open(..., 'noopener') !== null`, but `noopener` causes a null return even when opening succeeds ([MDN](https://developer.mozilla.org/en-US/docs/Web/API/Window/open#noopener)). Consequently, `ThreadScreen.kt:264` displays “cannot open” for successfully opened attachments. Preserve opener isolation without treating this return value as proof of blocking.

- **[P2] Release builds can enable debug logging.** [Platform.wasmJs.kt:4](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/wasmJsMain/kotlin/shifa/doctor/utils/Platform.wasmJs.kt:4) derives `isDebugBuild` solely from the hostname. Serving a production bundle on localhost enables the debug menu and the console logger in `HttpClient.wasmJs.kt:10`, violating the debug-build-only logging rule. Generate the flag from the build configuration; hostname checks can additionally restrict it.

---

Answers:
- P2 pickers read as a cancel: right; they stay stubs only until the next ticket, WEB-05, which replaces them with
  real `<input type="file">` pickers (documents, chat attachments, photos). Nothing else lands in between.
- P2 `noopener` makes `window.open` return null: fixed. The tab opens without it and the opener is cut by hand
  (`w.opener = null`), so a blocked pop-up is still told apart from an opened tab.
- P2 debug flag from the hostname alone: fixed. `generateWebBuildInfo` writes `WEB_DEBUG`, true only when the build
  was asked for a development task (`wasmJsBrowserDevelopmentRun`, `...DevelopmentWebpack`); `isDebugBuild` is
  `WEB_DEBUG && localhost`. A production bundle served on localhost has no debug menu and no console logger.
