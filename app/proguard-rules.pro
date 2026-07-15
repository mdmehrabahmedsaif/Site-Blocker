# Site-Blocker ProGuard Rules

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# Keep data models
-keep class com.siteblocker.app.data.** { *; }

# Keep services
-keep class com.siteblocker.app.accessibility.** { *; }
-keep class com.siteblocker.app.admin.** { *; }

# Material Design
-keep class com.google.android.material.** { *; }

# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
