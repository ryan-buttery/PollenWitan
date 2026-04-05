# Ktor — keep only reflection-required classes (engine + plugin registration)
-keep class io.ktor.serialization.** { *; }
-keep class io.ktor.client.engine.okhttp.** { *; }
-keep class io.ktor.client.plugins.contentnegotiation.** { *; }
-dontwarn io.ktor.**

# SLF4J (referenced by Ktor, not bundled)
-dontwarn org.slf4j.**

# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ryan.pollenwitan.**$$serializer { *; }
-keepclassmembers class com.ryan.pollenwitan.** {
    *** Companion;
}
-keepclasseswithmembers class com.ryan.pollenwitan.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *


# Google Tink (transitive via security-crypto) — compile-time annotations not bundled
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
