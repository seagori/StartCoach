# Room / kotlinx.serialization keep rules for release builds.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.seagori.hgr.**$$serializer { *; }
-keepclassmembers class com.seagori.hgr.** {
    *** Companion;
}
-keepclasseswithmembers class com.seagori.hgr.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Backup payloads are serialized by name.
-keep class com.seagori.hgr.data.local.entity.** { *; }
-keep class com.seagori.hgr.data.backup.** { *; }

# Tink (security-crypto) references compile-only errorprone annotations.
-dontwarn com.google.errorprone.annotations.**
