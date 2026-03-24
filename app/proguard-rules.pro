# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# SLF4J (referenced by Ktor CIO, not bundled)
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
