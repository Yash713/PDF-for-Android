# PDFBox-Android does a lot of reflection-based class lookups internally.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.**

# Tesseract4Android JNI bridge
-keep class cz.adaptech.tesseract4android.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Retrofit / OkHttp / Gson models used for CloudConvert DTOs
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.pdfmaster.app.data.remote.dto.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Hilt-generated components
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
