package com.siteblocker.app.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Constants used throughout the app.
 * Contains Firefox browser mapping and hardcoded allowed domains.
 */
public final class Constants {

        private Constants() {
                // Prevent instantiation
        }

        // --- Notification ---
        public static final String NOTIFICATION_CHANNEL_ID = "site_blocker_status";
        public static final int NOTIFICATION_ID = 1001;

        // --- SharedPreferences ---
        public static final String PREFS_NAME = "site_blocker_prefs";
        public static final String PREF_BLOCKED_COUNT = "blocked_count";
        public static final String PREF_PROTECTION_ENABLED = "protection_enabled";
        public static final String PREF_MULTI_RUN_BLOCK_ENABLED = "multi_run_block_enabled";

        // --- Intent Extras ---
        public static final String EXTRA_BLOCKED_DOMAIN = "blocked_domain";
        public static final String EXTRA_BLOCKED_URL = "blocked_url";

        // --- Request Codes ---
        public static final int REQUEST_DEVICE_ADMIN = 1001;
        public static final int REQUEST_ACCESSIBILITY = 1002;

        // --- Multi Run App ---
        public static final String MULTI_RUN_PACKAGE = "com.dong.multirun";

        /**
         * Browsers to monitor: Firefox, Chrome, Brave, and Edge.
         * Maps package name to known URL bar view ID.
         */
        public static final Map<String, String> MONITORED_BROWSERS = createBrowserMap();

        private static Map<String, String> createBrowserMap() {
                Map<String, String> map = new HashMap<>();

                // Firefox
                map.put("org.mozilla.firefox",
                                "org.mozilla.firefox:id/mozac_browser_toolbar_url_view");

                // Chrome
                map.put("com.android.chrome",
                                "com.android.chrome:id/url_bar");

                // Brave (Chrome-based)
                map.put("com.brave.browser",
                                "com.android.chrome:id/url_bar"); // Brave uses same ID as Chrome

                // Edge
                map.put("com.microsoft.emmx",
                                "com.microsoft.emmx:id/url_bar");

                return Collections.unmodifiableMap(map);
        }

        /**
         * Hardcoded list of allowed domains.
         * No other domains can be added from the app UI.
         */
        public static final List<String> ALLOWED_DOMAINS = Collections.unmodifiableList(
                        Arrays.asList(
                                        "accounts.google.com",
                                        "englishboli.com",
                                        "familylink.google.com",
                                        "families.google.com",
                                        "myaccount.google.com"));
}
