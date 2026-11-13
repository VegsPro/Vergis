# Keep Firebase and models
-keep class com.vergis.vegs.data.** { *; }
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
}
