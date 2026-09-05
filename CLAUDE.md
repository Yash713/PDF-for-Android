# PDF Master — Android Developer Task

> **Status:** Phases 1-4 have been scaffolded (see [README.md](README.md) for what's
> real vs. placeholder, and required setup steps: Gradle wrapper jar, tessdata asset,
> CloudConvert API key). Package is `com.pdfmaster.app`, minSdk 26. This file is kept
> as the original spec for reference; treat README.md as the current-state doc.

## Role
You are a Senior Android Developer specializing in large-scale utility apps. You write clean, modular, maintainable code following modern Android practices. You always consider memory management, threading, and user experience for heavy file-processing tasks.

## Context
Building an Android app called "PDF Master" that replicates all the features from the following list:
Organize PDF, Optimize PDF, Convert PDF, Edit PDF, PDF Security, PDF Intelligence, Merge, Split, Compress, PDF to Word, PDF to PowerPoint, PDF to Excel, Word to PDF, PowerPoint to PDF, Excel to PDF, Edit (text/images/shapes), PDF to JPG, JPG to PDF, Sign, Watermark, Rotate, HTML to PDF, Unlock, Protect, Organize (reorder/delete), PDF/A, Repair, Page Numbers, Scan to PDF, OCR, Compare.

## Technical Constraints & Stack
- **Language**: Kotlin
- **UI Toolkit**: Jetpack Compose (with Material 3)
- **Architecture**: Clean Architecture (Data → Domain → Presentation) + MVVM
- **DI**: Dagger Hilt
- **Concurrency**: Kotlin Coroutines + Flow
- **Background Processing**: WorkManager (for large files) + Foreground Service with a notification progress bar.
- **PDF Core**: `com.tom-roush:pdfbox-android:2.0.27.0` (Apache 2.0)
- **OCR**: `com.rmtheis:tess-two:9.1.0` (Tesseract for Android)
- **Networking**: Retrofit + OkHttp (for cloud-based Office conversions)
- **File Access**: Use Android's Storage Access Framework (SAF) – `Intent.ACTION_OPEN_DOCUMENT` and `DocumentFile` – to handle Android 11+ scoped storage properly without requesting `MANAGE_EXTERNAL_STORAGE`.
- **Camera**: CameraX for the "Scan to PDF" feature.

## Deliverables (Step-by-Step)

### Phase 1: Project Scaffolding & Core Engine
1. Generate the full `build.gradle.kts` (Module :app) including all necessary dependencies.
2. Create the base package structure: `com.yourcompany.pdfmaster.data`, `com.yourcompany.pdfmaster.domain`, `com.yourcompany.pdfmaster.presentation`.
3. Create a **Core PDF Engine** interface (`PdfEngine`) with the following methods:
   - `suspend fun mergePdfs(inputUris: List<Uri>, outputUri: Uri): Result<Unit>`
   - `suspend fun splitPdf(inputUri: Uri, pageRanges: List<IntRange>, outputDir: Uri): Result<List<Uri>>`
   - `suspend fun compressPdf(inputUri: Uri, outputUri: Uri, quality: CompressQuality): Result<Unit>`
   - `suspend fun protectPdf(inputUri: Uri, outputUri: Uri, password: String): Result<Unit>`
   - `suspend fun unlockPdf(inputUri: Uri, outputUri: Uri, password: String): Result<Unit>`
4. Implement this interface using **PDFBox-Android** (`PdfEngineImpl`). Ensure all operations are performed on `Dispatchers.IO` and emit progress via `Flow<Float>`.

### Phase 2: Dashboard & Navigation
1. Design a modern Home Screen (`HomeScreen.kt`) using a LazyVerticalGrid (2 columns) of feature cards based on the list above.
2. Implement a `NavigationHost` using Compose Navigation. Create empty placeholder screens for all features (e.g., `MergeScreen`, `SplitScreen`, `ConvertScreen`) so the navigation works.

### Phase 3: Top 3 User-Facing Features
1. **Merge Feature**: UI that allows selecting multiple PDFs via SAF, reordering them via drag-and-drop (using `LazyColumn` with drag handles), and a "Merge" button that calls `PdfEngine.mergePdfs` and shows a progress dialog.
2. **Compress Feature**: UI to pick a PDF, select compression level (Low/Medium/High) via a slider, and execute `compressPdf`.
3. **PDF to Word Conversion**: Since on-device conversion is heavy, implement a Remote Conversion repository (`CloudConvertRepository`) using Retrofit to interact with the CloudConvert API (or a mock endpoint for now). The UI should have a file picker, a "Convert" button, and handle the download of the resulting DOCX file to the Downloads folder.

### Phase 4: OCR Implementation
1. Create an `OcrEngine` interface.
2. Implement `TesseractOcrEngine` that initializes Tesseract with English language data (download it from the assets on first run).
3. Build a "Scan to PDF" / "OCR PDF" screen that allows picking an image from the gallery or capturing one via CameraX, runs the OCR to extract text, and generates a new searchable PDF (using PDFBox to overlay the text invisibly onto the image layer).

## Code Quality Rules
- **Do NOT ignore threading**: Every heavy operation must be wrapped in `viewModelScope.launch` and executed on a background thread.
- **Error Handling**: Use a sealed class `Resource<T>` (Success, Loading, Error) for all UI states. Display user-friendly Snackbar messages for errors.
- **Memory**: When dealing with large PDFs, always close `PDDocument` instances in `finally` blocks or use `.use { }` scope functions to prevent memory leaks.
- **Permissions**: Handle `READ_EXTERNAL_STORAGE` (for older devices) and `CAMERA` properly using `rememberLauncherForActivityResult`. For Android 11+, rely on SAF exclusively.

## Deliverables Format
Actual Kotlin code files with their full package paths, in this order:
1. `app/build.gradle.kts`
2. `AndroidManifest.xml`
3. `di/AppModule.kt` (Hilt modules)
4. `data/repository/PdfEngineImpl.kt`
5. `presentation/screens/home/HomeScreen.kt`
6. `presentation/screens/merge/MergeScreen.kt` & `MergeViewModel.kt`
7. `presentation/screens/compress/CompressScreen.kt` & `CompressViewModel.kt`
8. `data/remote/CloudConvertApi.kt`

Produce compilable, production-ready code for the requested files. If a piece of code is too long, write the full implementation — do not use `// ... rest of code` placeholders for logic.
