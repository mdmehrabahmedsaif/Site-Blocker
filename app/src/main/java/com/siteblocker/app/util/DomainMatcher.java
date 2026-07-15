package com.siteblocker.app.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for matching URLs/domains against the whitelist.
 * Handles subdomain matching, domain normalization, and URL extraction.
 */
public final class DomainMatcher {

    private DomainMatcher() {
        // Prevent instantiation
    }

    // Pattern to validate domain format
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)*\\.[a-zA-Z]{2,}$"
    );

    // Pattern to detect URL-like strings
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?://)?([a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}(/.*)?$"
    );

    /**
     * Check if a URL or domain matches any of the allowed domains.
     * Supports subdomain matching: if "google.com" is allowed,
     * "mail.google.com" and "docs.google.com" are also allowed.
     *
     * @param urlOrDomain The URL or domain to check
     * @param allowedDomains List of allowed domains
     * @return true if the domain is allowed, false if it should be blocked
     */
    public static boolean isAllowed(String urlOrDomain, List<String> allowedDomains) {
        if (urlOrDomain == null || urlOrDomain.isEmpty() || allowedDomains == null) {
            return false;
        }

        String domain = extractDomain(urlOrDomain);
        if (domain == null || domain.isEmpty()) {
            // If we can't extract a domain, allow it (could be about:blank, chrome://settings, etc.)
            return true;
        }

        // Check against each allowed domain
        for (String allowedDomain : allowedDomains) {
            if (domainMatchesRule(domain, allowedDomain)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a specific domain matches an allowed domain rule.
     * Supports exact match and subdomain matching.
     *
     * Examples:
     *   domainMatchesRule("google.com", "google.com") → true
     *   domainMatchesRule("mail.google.com", "google.com") → true
     *   domainMatchesRule("notgoogle.com", "google.com") → false
     */
    public static boolean domainMatchesRule(String domain, String rule) {
        if (domain == null || rule == null) {
            return false;
        }

        String normalizedDomain = domain.toLowerCase().trim();
        String normalizedRule = rule.toLowerCase().trim();

        // Exact match
        if (normalizedDomain.equals(normalizedRule)) {
            return true;
        }

        // Subdomain match: domain ends with ".rule"
        if (normalizedDomain.endsWith("." + normalizedRule)) {
            return true;
        }

        return false;
    }

    /**
     * Extract the domain from a URL string.
     * Handles various formats: full URL, domain only, with/without protocol.
     *
     * @param input URL or domain string
     * @return Extracted domain, or null if invalid
     */
    public static String extractDomain(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        String cleaned = input.trim().toLowerCase();

        // Skip non-HTTP URLs (about:, chrome://, file://, etc.)
        if (cleaned.startsWith("about:") || cleaned.startsWith("chrome://") ||
                cleaned.startsWith("file://") || cleaned.startsWith("content://") ||
                cleaned.startsWith("javascript:") || cleaned.startsWith("data:")) {
            return null;
        }

        // Remove protocol
        if (cleaned.startsWith("https://")) {
            cleaned = cleaned.substring(8);
        } else if (cleaned.startsWith("http://")) {
            cleaned = cleaned.substring(7);
        }

        // Remove www prefix
        if (cleaned.startsWith("www.")) {
            cleaned = cleaned.substring(4);
        }

        // Remove path, query, and fragment
        int slashIndex = cleaned.indexOf('/');
        if (slashIndex > 0) {
            cleaned = cleaned.substring(0, slashIndex);
        }
        int queryIndex = cleaned.indexOf('?');
        if (queryIndex > 0) {
            cleaned = cleaned.substring(0, queryIndex);
        }
        int hashIndex = cleaned.indexOf('#');
        if (hashIndex > 0) {
            cleaned = cleaned.substring(0, hashIndex);
        }

        // Remove port number
        int colonIndex = cleaned.indexOf(':');
        if (colonIndex > 0) {
            cleaned = cleaned.substring(0, colonIndex);
        }

        // Validate it looks like a domain (must contain a dot, no spaces, and length > 3)
        if (cleaned.contains(".") && !cleaned.contains(" ") && cleaned.length() > 3) {
            return cleaned;
        }

        return null;
    }

    /**
     * Validate that a string is a valid domain name format.
     */
    public static boolean isValidDomain(String domain) {
        if (domain == null || domain.isEmpty()) {
            return false;
        }
        String cleaned = extractDomain(domain);
        return cleaned != null && DOMAIN_PATTERN.matcher(cleaned).matches();
    }

    /**
     * Check if a string looks like a URL (has domain-like structure).
     */
    public static boolean looksLikeUrl(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String trimmed = text.trim();

        // Quick checks
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return true;
        }

        // Check if it matches URL pattern
        return URL_PATTERN.matcher(trimmed).matches();
    }

    /**
     * Check if the URL text looks like a search query (not a real URL).
     * Search queries in the address bar should not trigger blocking.
     */
    public static boolean isSearchQuery(String text) {
        if (text == null || text.isEmpty()) {
            return true; // Empty is not a URL
        }

        String trimmed = text.trim();

        // If it contains spaces and no dots, it's likely a search query
        if (trimmed.contains(" ") && !trimmed.contains(".")) {
            return true;
        }

        // Very short text without dots is likely a search
        if (trimmed.length() < 4 && !trimmed.contains(".")) {
            return true;
        }

        return false;
    }
}
