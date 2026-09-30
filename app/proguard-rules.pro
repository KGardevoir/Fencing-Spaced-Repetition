# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signature for Kotlin coroutines and reflection
-keepattributes Signature
-keepattributes *Annotation*

# ===== Room Database =====
# Room creates each generated <Name>_Impl reflectively through its no-argument
# constructor, so the constructor has to be kept by name; keeping the class
# alone does not keep it under R8's full mode. Two Room lines are here:
# androidx.room3 for the app's own database, and androidx.room -- Room 2.5,
# which WorkManager brings for its internal WorkDatabase. Room 2.5 predates
# full mode and ships no constructor rule, so WorkDatabase_Impl lost its
# constructor and every release build crashed at startup, in
# androidx.startup.InitializationProvider, before any app code ran.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class * extends androidx.room3.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# Keep Room entity fields
-keepclassmembers class * {
    @androidx.room.ColumnInfo <fields>;
}

# ===== Kotlin Coroutines =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ===== Jetpack Compose =====
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep all Composable functions
-keep @androidx.compose.runtime.Composable class * { *; }
-keep @androidx.compose.runtime.Composable interface * { *; }

# ===== Google Play Billing =====
-keep class com.android.billingclient.** { *; }
-keep interface com.android.billingclient.** { *; }

# Keep billing product details
-keepclassmembers class com.android.billingclient.api.ProductDetails** {
    *;
}
-keepclassmembers class com.android.billingclient.api.Purchase** {
    *;
}

# ===== Kotlin Serialization =====
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# kotlinx-serialization-json specific
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ===== Data Classes =====
# Keep data classes used in the app
-keep class com.fencing.spacedrepetition.data.model.** { *; }
-keep class com.fencing.spacedrepetition.billing.** { *; }

# ===== WorkManager =====
# A job's input merger is stored by class name and created reflectively when
# the job runs -- the same failure as WorkDatabase_Impl above, only later.
-keep class * extends androidx.work.InputMerger { <init>(); }

# ===== AndroidX =====
-keep class androidx.lifecycle.** { *; }
-keep class androidx.datastore.** { *; }

# ===== Coil Image Loading =====
-keep class coil.** { *; }
-dontwarn coil.**

# ===== General Android =====
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
