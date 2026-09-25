-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.kalorientracker.app.**$$serializer { *; }
-keepclassmembers class com.kalorientracker.app.** { *** Companion; }
-keepclasseswithmembers class com.kalorientracker.app.** { kotlinx.serialization.KSerializer serializer(...); }
