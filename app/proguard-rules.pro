# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class org.tarish.adhan.**$$serializer { *; }
-keepclassmembers class org.tarish.adhan.** {
    *** Companion;
}
-keep @kotlinx.serialization.Serializable class org.tarish.adhan.** { *; }

# Retrofit
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepclasseswithmembers interface * { @retrofit2.http.* <methods>; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Glance receiver kept by manifest; keep legacy name explicitly
-keep class org.tarish.adhan.AdhanHomeWidget { *; }
