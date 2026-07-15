package com.siteblocker.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Data Access Object for allowed sites.
 * Provides reactive queries via LiveData for UI updates.
 */
@Dao
public interface AllowedSiteDao {

    /**
     * Get all active allowed sites, ordered by most recently added.
     */
    @Query("SELECT * FROM allowed_sites WHERE active = 1 ORDER BY addedAt DESC")
    LiveData<List<AllowedSite>> getAllActiveSites();

    /**
     * Get all allowed sites (including inactive), ordered by most recently added.
     */
    @Query("SELECT * FROM allowed_sites ORDER BY addedAt DESC")
    LiveData<List<AllowedSite>> getAllSites();

    /**
     * Get all active domains as a simple list (non-reactive, for service use).
     */
    @Query("SELECT domain FROM allowed_sites WHERE active = 1")
    List<String> getActiveDomainsList();

    /**
     * Check if a domain already exists.
     */
    @Query("SELECT COUNT(*) FROM allowed_sites WHERE domain = :domain")
    int domainExists(String domain);

    /**
     * Get the count of all active sites.
     */
    @Query("SELECT COUNT(*) FROM allowed_sites WHERE active = 1")
    LiveData<Integer> getActiveSiteCount();

    /**
     * Insert a new allowed site. Ignore if domain already exists.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(AllowedSite site);

    /**
     * Delete a specific allowed site.
     */
    @Delete
    void delete(AllowedSite site);

    /**
     * Delete a site by its domain.
     */
    @Query("DELETE FROM allowed_sites WHERE domain = :domain")
    void deleteByDomain(String domain);

    /**
     * Delete all sites.
     */
    @Query("DELETE FROM allowed_sites")
    void deleteAll();
}
