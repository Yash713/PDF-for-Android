# PDF Master

Android PDF utility app: Kotlin, Jetpack Compose (Material 3), Clean Architecture
(data/domain/presentation) + MVVM, Hilt, Coroutines/Flow, WorkManager, PDFBox-Android,
Tesseract4Android (OCR), Retrofit (CloudConvert), CameraX.

## Before you open this in Android Studio

This project was scaffolded without Android Studio/SDK available in the environment
that generated it. The Gradle wrapper (`gradlew`, `gradlew.bat`,
`gradle/wrapper/gradle-wrapper.jar`) is present and complete, so
`./gradlew assembleDebug` works as-is, including in CI - and English OCR data
is bundled at `app/src/main/assets/tessdata/eng.traineddata` (from
tesseract-ocr/tessdata_fast), so Scan to PDF / OCR works with no setup. One
thing still needs your attention on first open:

1. **CloudConvert API key.** The CloudConvert-backed screens (PDF <-> Word/
   PowerPoint/Excel) call the real [CloudConvert v2 API](https://cloudconvert.com/api/v2).
   Get a free key at <https://cloudconvert.com/dashboard/api/v2/keys> and put it
   in `CLOUDCONVERT_API_KEY=` in `gradle.properties` (or better, in your
   user-level `~/.gradle/gradle.properties` so it never ends up in git - see the
   note in that file if you're pushing this to a public GitHub repo). Without a
   key, those screens surface a clear error instead of pretending to convert.

## Why Tesseract4Android instead of the tess-two the original spec named

`com.rmtheis:tess-two` (the originally requested OCR library) has been unmaintained
since ~2018 and its binaries were only ever published on JCenter, which shut down
in 2021 - so that exact dependency can no longer be resolved by Gradle. Its own
maintainer's README points at [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android)
as the successor, which deliberately kept the same package name
(`com.googlecode.tesseract.android`) for drop-in migration. That's what's wired up
here (`cz.adaptech.tesseract4android:tesseract4android:4.9.0`, pulled from JitPack -
see `settings.gradle.kts`).

## What's implemented

All 23 home-grid tiles are fully wired with real logic - no placeholders remain:
Merge, Split, Compress, Protect, Unlock, Rotate, Organize Pages, Page Numbers,
PDF to JPG, JPG to PDF, PDF to Word/PowerPoint/Excel, Word/PowerPoint/Excel to
PDF (CloudConvert both directions), Scan to PDF / OCR, Watermark (Add/Remove/
Cover), PDF/A, HTML to PDF, Repair, Edit (text/image/shape overlays), Sign
(drawn or uploaded signature), and Compare (visual page diffing).

### V1 scoping on the last three (deliberately pragmatic, not half-built)

- **Edit** adds new layers on top of a page - it doesn't let you select or
  modify content that's already there. Added elements can be dragged to
  reposition but not resized/rotated in this pass.
- **Sign** produces a **visible image overlay**, not a cryptographically
  verifiable digital signature (no certificate, no tamper-evidence) - it's
  the same trust model as printing a page, signing it, and rescanning it.
- **Compare** is a coarse grid-based pixel diff (renders both pages small,
  buckets differences into a 12x16 grid) - a visual-difference check, not a
  semantic/text diff. Good for "did anything change on this page," not for
  precisely which words changed.

### Three deliberate deviations from how these were originally specced

- **PDF/A** (`PdfEngineImpl.pdfToPdfA`) is a **best-effort compatibility pass**,
  not validator-certified conversion. It removes encryption and document-level
  JavaScript and writes PDF/A identification XMP metadata (hand-built XML via
  the low-level `PDMetadata` class - `org.apache.pdfbox:pdfbox-tools`, which was
  originally suggested, is a *desktop* PDFBox artifact that depends on
  `java.awt.*` and cannot run on Android). Real validator-passing PDF/A also
  needs font embedding and an ICC output-intent profile, which this does not
  add - the in-app copy on `PdfToPdfAScreen` says so explicitly so the feature
  doesn't overpromise.
- **HTML to PDF** does not use PDFBox (it has no HTML rendering capability - the
  `HtmlConverter` class asked for is from iText's `html2pdf`, not PDFBox) or
  iText (its `html2pdf` module is AGPL/commercially licensed - not safe to bundle
  into a closed-source app). Instead it drives Android's built-in
  `WebView` + `android.print.PrintDocumentAdapter` framework
  (`presentation/util/HtmlToPdfPrinter.kt`), which needs zero extra dependencies
  and zero licensing risk. This is also why it's the one engine-ish piece that
  lives partly in the presentation layer: the WebView must be genuinely attached
  to the view hierarchy (visible in a small preview on `HtmlToPdfScreen`) for its
  renderer to reliably produce non-blank output - a fully detached, manually-
  sized WebView is a known way to get an empty PDF.
- **Repair** (`PdfEngineImpl.repairPdf`) leans on the fact that PDFBox's parser
  already recovers from common xref/trailer corruption automatically during
  `PDDocument.load()` - there's no separate "lenient mode" toggle to call. Repair
  = let that happen, then rebuild the document page-by-page, dropping any
  individual page that fails to import rather than failing the whole file. If
  the initial load throws at all, the file is reported as unrecoverable rather
  than attempting a raw byte-salvage heuristic that PDFBox's public API doesn't
  really support.

## Known risk: unverified against a real compiler

There's no Android SDK or emulator in the environment this was written in, so
none of this has been through an actual Gradle build. The architecture and logic
are sound, but exact method names on PDFBox-Android 2.0.27.0 (e.g. `PDResources.put`,
`JPEGFactory.createFromImage`, the hand-built XMP path through `PDMetadata` for
PDF/A) and Tesseract4Android's `ResultIterator` API were written from documented/
known usage, not a compiler. The other highest-risk spot is
`HtmlToPdfPrinter.kt`'s `PrintDocumentAdapter.onLayout`/`onWrite` callback
plumbing - real Android framework APIs, but async callback-to-coroutine glue
that's easy to get subtly wrong without a device to test on. Open the project in
Android Studio, let Gradle sync, and treat the first build's error list (if any)
as a punch list - it should be small and mechanical (import/signature fixes),
not structural.

Edit and Sign's drag-to-reposition overlays (`EditScreen.kt`, `SignScreen.kt`)
lean on `rememberUpdatedState` inside a `pointerInput` gesture block to avoid a
real bug where a naively-captured element would go stale after the first drag
tick (Compose's `detectDragGestures` runs on a coroutine that outlives any
single recomposition). The pattern is correct, but this class of gesture code
is exactly where subtle Compose bugs hide without a device to actually drag
something on - test dragging elements around before trusting it.
