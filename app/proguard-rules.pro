# MyHealth ProGuard/R8 rules.
# Release minification is configured in P8.7; these are the baseline keeps.
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable

# Room generated implementations are referenced reflectively by Room.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# kotlinx.serialization keeps its generated serializers on the companion.
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# P7.1 — the Garmin FIT SDK builds its message profiles from generated tables and reads them
# reflectively; R8 must not rename or strip any of it.
-keep class com.garmin.fit.** { *; }
-dontwarn com.garmin.fit.**
