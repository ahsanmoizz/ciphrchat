# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep Libsignal Protocol interfaces and state classes
-keep class org.whispersystems.libsignal.** { *; }

# Keep Rust JNI bridge functions
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclassmembers class * {
    native <methods>;
}

# Keep SQLCipher and Room generated classes
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class androidx.room.** { *; }
