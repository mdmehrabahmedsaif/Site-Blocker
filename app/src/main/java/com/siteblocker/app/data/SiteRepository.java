package com.siteblocker.app.data;

import android.content.Context;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository for managing allowed sites data.
 * Provides a clean API to the rest of the app and handles
 * background thread execution for database operations.
 */
public class SiteRepository {

    private final AllowedSiteDao allowedSiteDao;
    private final ExecutorService executor;

    // Cached LiveData
    private final LiveData<List<AllowedSite>> allSites;
    private final LiveData<Integer> activeSiteCount;

    private static volatile SiteRepository INSTANCE;

    private SiteRepository(Context context) {
        SiteDatabase db = SiteDatabase.getInstance(context);
        this.allowedSiteDao = db.allowedSiteDao();
        this.executor = Executors.newFixedThreadPool(2);
        this.allSites = allowedSiteDao.getAllSites();
        this.activeSiteCount = allowedSiteDao.getActiveSiteCount();
    }

    /**
     * Get the singleton repository instance.
     */
    public static SiteRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SiteRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SiteRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    // --- Reactive Queries (LiveData) ---

    public LiveData<List<AllowedSite>> getAllSites() {
        return allSites;
    }

    public LiveData<Integer> getActiveSiteCount() {
        return activeSiteCount;
    }

    // --- Synchronous Queries (for Services) ---

    /**
     * Get all active domains as a simple list.
     * Must be called from a background thread.
     */
    public List<String> getActiveDomainsList() {
        return allowedSiteDao.getActiveDomainsList();
    }

    /**
     * Check if a domain exists in the database.
     * Must be called from a background thread.
     */
    public boolean domainExists(String domain) {
        return allowedSiteDao.domainExists(domain) > 0;
    }

    // --- Write Operations (async) ---

    /**
     * Add a new allowed site.
     *
     * @param domain The domain name (e.g., "google.com")
     * @param label  Optional display label
     * @param callback Callback with result: true if added, false if already exists
     */
    public void addSite(String domain, String label, AddSiteCallback callback) {
        executor.execute(() -> {
            // Normalize domain
            String normalizedDomain = normalizeDomain(domain);

            // Check for duplicates
            if (allowedSiteDao.domainExists(normalizedDomain) > 0) {
                if (callback != null) {
                    callback.onResult(false, "Domain already exists");
                }
                return;
            }

            AllowedSite site = new AllowedSite(
                    normalizedDomain,
                    label != null && !label.trim().isEmpty() ? label.trim() : normalizedDomain,
                    System.currentTimeMillis(),
                    true
            );

            long id = allowedSiteDao.insert(site);
            if (callback != null) {
                callback.onResult(id > 0, id > 0 ? "Site added" : "Failed to add site");
            }
        });
    }

    /**
     * Remove an allowed site.
     */
    public void removeSite(AllowedSite site) {
        executor.execute(() -> allowedSiteDao.delete(site));
    }

    /**
     * Remove all sites.
     */
    public void removeAllSites() {
        executor.execute(allowedSiteDao::deleteAll);
    }

    // --- Helpers ---

    /**
     * Normalize a domain by removing protocols, www prefix, trailing slashes, and paths.
     */
    private String normalizeDomain(String domain) {
        String normalized = domain.trim().toLowerCase();

        // Remove protocol
        if (normalized.startsWith("https://")) {
            normalized = normalized.substring(8);
        } else if (normalized.startsWith("http://")) {
            normalized = normalized.substring(7);
        }

        // Remove www prefix
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }

        // Remove path and query
        int slashIndex = normalized.indexOf('/');
        if (slashIndex > 0) {
            normalized = normalized.substring(0, slashIndex);
        }

        // Remove port
        int colonIndex = normalized.indexOf(':');
        if (colonIndex > 0) {
            normalized = normalized.substring(0, colonIndex);
        }

        return normalized;
    }

    // --- Callback Interface ---

    public interface AddSiteCallback {
        void onResult(boolean success, String message);
    }
}
