# ProGuard rules for AE_POS
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
