package com.siteblocker.app.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Room database for Site Blocker.
 * Singleton pattern ensures only one instance exists.
 */
@Database(entities = {AllowedSite.class}, version = 1, exportSchema = false)
public abstract class SiteDatabase extends RoomDatabase {

    public abstract AllowedSiteDao allowedSiteDao();

    private static volatile SiteDatabase INSTANCE;
    private static final String DATABASE_NAME = "site_blocker_db";

    /**
     * Get the singleton database instance.
     * Uses double-checked locking for thread safety.
     */
    public static SiteDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SiteDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            SiteDatabase.class,
                            DATABASE_NAME
                    )
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
