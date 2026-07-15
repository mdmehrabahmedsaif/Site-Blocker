package com.siteblocker.app.data;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Entity representing an allowed (whitelisted) site.
 * Only sites in this table can be accessed through browsers.
 */
@Entity(
    tableName = "allowed_sites",
    indices = {@Index(value = "domain", unique = true)}
)
public class AllowedSite {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String domain;      // e.g., "google.com"
    private String label;       // Display name, e.g., "Google"
    private long addedAt;       // Timestamp when added
    private boolean active;     // Whether this site is currently allowed

    public AllowedSite(String domain, String label, long addedAt, boolean active) {
        this.domain = domain;
        this.label = label;
        this.addedAt = addedAt;
        this.active = active;
    }

    // --- Getters ---

    public int getId() {
        return id;
    }

    public String getDomain() {
        return domain;
    }

    public String getLabel() {
        return label;
    }

    public long getAddedAt() {
        return addedAt;
    }

    public boolean isActive() {
        return active;
    }

    // --- Setters ---

    public void setId(int id) {
        this.id = id;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setAddedAt(long addedAt) {
        this.addedAt = addedAt;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Returns the display name — label if set, otherwise domain.
     */
    public String getDisplayName() {
        if (label != null && !label.trim().isEmpty()) {
            return label;
        }
        return domain;
    }
}
