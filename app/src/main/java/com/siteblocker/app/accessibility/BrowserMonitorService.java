package com.siteblocker.app.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.siteblocker.app.BlockedActivity;
import com.siteblocker.app.util.Constants;
import com.siteblocker.app.util.DomainMatcher;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Collections;

/**
 * Accessibility Service that monitors browser URL bars.
 *
 * This is the CORE blocking engine of the app. It works by:
 * 1. Detecting when a monitored browser is in the foreground
 * 2. Reading the URL from the browser's address bar
 * 3. Checking if the URL's domain is in the allowed list
 * 4. If NOT allowed → redirecting the user to the blocked screen
 *
 * Only Firefox is monitored. All other browsers are ignored.
 *
 * Security considerations:
 * - Uses hardcoded whitelist (accounts.google.com, familylink.google.com)
 * - A cooldown mechanism prevents rapid-fire blocking events
 */
public class BrowserMonitorService extends AccessibilityService {

    private static final String TAG = "BrowserMonitor";

    // Hardcoded whitelist from Constants
    private final List<String> allowedDomains = Constants.ALLOWED_DOMAINS;

    // Prevent rapid-fire blocking
    private long lastBlockTime = 0;
    private String lastBlockedDomain = "";
    private static final long BLOCK_COOLDOWN_MS = 50; // 50ms cooldown for instant block action

    // Flag to prevent re-entrant blocking
    private final AtomicBoolean isBlocking = new AtomicBoolean(false);

    // Handler for main thread operations
    private Handler mainHandler;

    // Track current foreground browser
    private String currentBrowserPackage = null;
    private String lastActivePackage = null;

    // Search box blocking cooldown
    private long lastSearchBlockTime = 0;
    private static final long SEARCH_BLOCK_COOLDOWN_MS = 100; // 100ms cooldown — fast response

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "BrowserMonitorService connected");

        mainHandler = new Handler(Looper.getMainLooper());

        // Configure service
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    | AccessibilityEvent.TYPE_VIEW_FOCUSED
                    | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.notificationTimeout = 0; // Instant dispatch
            info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            setServiceInfo(info);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String packageName = event.getPackageName() != null
                ? event.getPackageName().toString() : null;

        if (packageName == null) return;

        int eventType = event.getEventType();
        Log.d(TAG, "Event: pkg=" + packageName + " type=" + eventType);

        // === TRANSITION BACK FROM BLOCK SCREEN CHECK ===
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (Constants.MONITORED_BROWSERS.containsKey(packageName)
                    && "com.siteblocker.app".equals(lastActivePackage)) {
                Log.d(TAG, "Transition back from block screen detected, going back");
                performGlobalAction(GLOBAL_ACTION_BACK);
            }
            lastActivePackage = packageName;
        }

        // === MULTI RUN APP BLOCKING ===
        // Must be checked BEFORE the Firefox-only filter below.
        // This blocks "Device Apps" screen and search in Multi Run.
        if (Constants.MULTI_RUN_PACKAGE.equals(packageName)) {
            if (isMultiRunBlockEnabled()) {
                handleMultiRunBlocking(event, eventType);
            }
            return; // Multi Run is not a browser, don't process further
        }

        // === FIREFOX ONLY ===
        if (!"org.mozilla.firefox".equals(packageName)) {
            return;
        }

        if (!isBrowserBlockingEnabled()) {
            return;
        }

        // === ONLY MONITOR KNOWN BROWSERS ===
        if (!Constants.MONITORED_BROWSERS.containsKey(packageName)) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                currentBrowserPackage = null;
            }
            return;
        }

        // === SEARCH BOX BLOCKING ===
        // Any text change in Firefox URL bar = user is typing in the search/URL bar.
        // Block it IMMEDIATELY: clear text and close URL bar.
        if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            if (isUrlBarEvent(event, packageName)) {
                Log.i(TAG, "TEXT CHANGE detected in Firefox URL bar — blocking immediately!");
                blockSearchBox(packageName);
                return;
            }
        }
        // Focus on any EditText in Firefox URL bar = user tapped the URL bar
        if (eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            if (isUrlBarEvent(event, packageName)) {
                blockSearchBox(packageName);
                return;
            }
        }
        // Also check during content/state changes if the edit URL view appeared
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (isUrlBarEditOverlayOpen(packageName)) {
                blockSearchBox(packageName);
                return;
            }
        }

        // === FOREGROUND CHECK ===
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            CharSequence fgPackage = rootNode.getPackageName();
            rootNode.recycle();
            Log.d(TAG, "Foreground package check: " + fgPackage);
            if (fgPackage != null && "com.siteblocker.app".equals(fgPackage.toString())
                    && !fgPackage.toString().equals(packageName)) {
                Log.d(TAG, "Our app is in foreground, ignoring background event");
                return;
            }
        }

        // Update current browser tracker
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentBrowserPackage = packageName;
        }

        // Only process if we have a current browser
        if (currentBrowserPackage == null) {
            Log.d(TAG, "No current browser package tracked");
            return;
        }

        // Try to extract URL from the browser
        String url = extractUrlFromBrowser(packageName);
        Log.d(TAG, "Extracted URL: " + url);
        if (url != null && !url.isEmpty()) {
            checkAndBlockUrl(url, packageName);
        }
    }

    /**
     * Check if an accessibility event is coming from the URL/search bar.
     * Detects EditText focus or text change in Firefox's address bar.
     */
    private boolean isUrlBarEvent(AccessibilityEvent event, String packageName) {
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return false;

        try {
            String className = source.getClassName() != null
                    ? source.getClassName().toString() : "";
            String viewId = source.getViewIdResourceName() != null
                    ? source.getViewIdResourceName() : "";

            Log.d(TAG, "Source check — class: " + className + ", viewId: " + viewId);

            boolean isEditText = className.contains("EditText")
                    || className.contains("AutoCompleteTextView");

            if (!isEditText) {
                return false;
            }

            if (viewId == null || viewId.isEmpty()) {
                return false;
            }

            // Check if the view ID matches any browser's URL bar IDs
            boolean isUrlBarId = false;

            if (packageName.equals("org.mozilla.firefox")) {
                isUrlBarId = viewId.contains("mozac_browser_toolbar")
                        || viewId.contains("url_view")
                        || viewId.contains("search")
                        || viewId.contains("edit_url")
                        || viewId.contains("url_bar")
                        || viewId.contains("mozac_browser_toolbar_edit_url_view")
                        || viewId.contains("ADDRESSBAR");
            } else if (packageName.equals("com.android.chrome")
                    || packageName.equals("com.brave.browser")
                    || packageName.equals("com.microsoft.emmx")) {
                isUrlBarId = viewId.contains("url_bar")
                        || viewId.contains("ADDRESSBAR")
                        || viewId.contains("com.android.chrome:id/url_bar")
                        || viewId.contains("com.microsoft.emmx:id/url_bar");
            }

            return isUrlBarId;
        } finally {
            if (source != null) source.recycle();
        }
    }

    /**
     * Check if Firefox's URL bar edit overlay is currently open by looking
     * for the edit URL view in the accessibility node tree.
     * This works even when Firefox doesn't emit standard focus events.
     */
    private boolean isUrlBarEditOverlayOpen(String packageName) {
        // If Firefox is monitored, scan all active windows to locate the Compose/Jetpack search overlay
        if (packageName.equals("org.mozilla.firefox")) {
            List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                    AccessibilityNodeInfo rootNode = window.getRoot();
                    if (rootNode == null) continue;

                    try {
                        if (scanNodeForFirefoxSearchOverlay(rootNode, 0)) {
                            return true;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error checking window in Firefox edit overlay", e);
                    } finally {
                        rootNode.recycle();
                    }
                }
            }
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return false;

        try {
            if (packageName.equals("com.android.chrome")
                    || packageName.equals("com.brave.browser")
                    || packageName.equals("com.microsoft.emmx")) {
                // Chrome/Brave/Edge-specific check
                // These browsers don't have a dedicated "edit overlay" ID, so we check for focused EditText
                return hasFocusedEditText(rootNode, 0);
            }

            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking URL bar edit overlay", e);
            return false;
        } finally {
            rootNode.recycle();
        }
    }

    private boolean scanNodeForFirefoxSearchOverlay(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 40) return false;

        try {
            String viewId = node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "";
            CharSequence textSeq = node.getText();
            String text = textSeq != null ? textSeq.toString() : "";
            CharSequence descSeq = node.getContentDescription();
            String desc = descSeq != null ? descSeq.toString() : "";

            if (viewId.contains("ADDRESSBAR_EDIT_MODE")
                    || viewId.contains("ADDRESSBAR_SEARCH_BOX")
                    || desc.contains("QR scanner")
                    || text.contains("Search or type web address")) {
                Log.d(TAG, "Search overlay node found! viewId: " + viewId + ", desc: " + desc + ", text: " + text);
                return true;
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    boolean found = scanNodeForFirefoxSearchOverlay(child, depth + 1);
                    child.recycle();
                    if (found) return true;
                }
            }
        } catch (Exception e) {
            // stable check
        }
        return false;
    }

    /**
     * Recursively check if there's a focused EditText in the node tree.
     */
    private boolean hasFocusedEditText(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 15) return false;

        String className = node.getClassName() != null ? node.getClassName().toString() : "";
        if ((className.contains("EditText") || className.contains("AutoCompleteTextView"))
                && node.isFocused()) {
            return true;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean found = hasFocusedEditText(child, depth + 1);
                child.recycle();
                if (found) return true;
            }
        }
        return false;
    }

    /**
     * Block the search box: clear any text and go to HOME screen.
     * This prevents the user from typing anything in the search/URL bar.
     */
    private void blockSearchBox(String packageName) {
        long now = System.currentTimeMillis();
        if ((now - lastSearchBlockTime) < SEARCH_BLOCK_COOLDOWN_MS) {
            return; // Cooldown to avoid rapid-fire actions
        }
        lastSearchBlockTime = now;

        Log.i(TAG, "BLOCKING SEARCH BOX in " + packageName);

        // Step 1: Clear any text that was typed
        clearUrlBarText(packageName);

        // Step 2: Press BACK to close the URL bar and stay in the browser
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    /**
     * Try to find the URL bar EditText and clear its text content.
     */
    private void clearUrlBarText(String packageName) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        try {
            if (packageName.equals("org.mozilla.firefox")) {
                // Firefox-specific URL bar IDs
                String[] editIds = {
                        packageName + ":id/mozac_browser_toolbar_edit_url_view",
                        packageName + ":id/mozac_browser_toolbar_url_view",
                        packageName + ":id/url_bar_title",
                };

                for (String editId : editIds) {
                    List<AccessibilityNodeInfo> nodes =
                            rootNode.findAccessibilityNodeInfosByViewId(editId);
                    if (nodes != null && !nodes.isEmpty()) {
                        for (AccessibilityNodeInfo node : nodes) {
                            if (node != null) {
                                // Clear the text
                                Bundle args = new Bundle();
                                args.putCharSequence(
                                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
                                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                                Log.d(TAG, "Cleared URL bar text via " + editId);
                                node.recycle();
                                return; // Done
                            }
                        }
                    }
                }
            } else if (packageName.equals("com.android.chrome")
                    || packageName.equals("com.brave.browser")
                    || packageName.equals("com.microsoft.emmx")) {
                // Chrome/Brave/Edge-specific URL bar IDs
                String[] editIds = {
                        packageName + ":id/url_bar",
                        packageName + ":id/ADDRESSBAR_URL_BOX",
                };

                for (String editId : editIds) {
                    List<AccessibilityNodeInfo> nodes =
                            rootNode.findAccessibilityNodeInfosByViewId(editId);
                    if (nodes != null && !nodes.isEmpty()) {
                        for (AccessibilityNodeInfo node : nodes) {
                            if (node != null) {
                                // Clear the text
                                Bundle args = new Bundle();
                                args.putCharSequence(
                                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
                                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                                Log.d(TAG, "Cleared URL bar text via " + editId);
                                node.recycle();
                                return; // Done
                            }
                        }
                    }
                }
            }


        } catch (Exception e) {
            Log.e(TAG, "Error clearing URL bar text", e);
        } finally {
            rootNode.recycle();
        }
    }

    /**
     * Recursively find a focused EditText and clear its text.
     */
    private void clearFocusedEditText(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 15) return;

        String className = node.getClassName() != null ? node.getClassName().toString() : "";
        if ((className.contains("EditText") || className.contains("AutoCompleteTextView"))
                && node.isFocused()) {
            Bundle args = new Bundle();
            args.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            Log.d(TAG, "Cleared focused EditText text");
            return;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                clearFocusedEditText(child, depth + 1);
                child.recycle();
            }
        }
    }

    /**
     * Extract the URL from the browser's address bar.
     */
    private String extractUrlFromBrowser(String packageName) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return null;

        try {
            // Strategy 1: Try known URL bar view ID (including fallbacks for newer Firefox versions)
            String[] possibleIds = {
                Constants.MONITORED_BROWSERS.get(packageName),
                packageName + ":id/ADDRESSBAR_URL_BOX",
                "ADDRESSBAR_URL_BOX"
            };

            for (String viewId : possibleIds) {
                if (viewId == null) continue;
                List<AccessibilityNodeInfo> urlNodes =
                        rootNode.findAccessibilityNodeInfosByViewId(viewId);
                if (urlNodes != null && !urlNodes.isEmpty()) {
                    Log.d(TAG, "Found URL nodes using viewId: " + viewId + " count: " + urlNodes.size());
                    String extractedText = null;
                    AccessibilityNodeInfo targetNode = urlNodes.get(0);
                    if (targetNode != null) {
                        extractedText = getTextFromNode(targetNode);
                    }
                    // Recycle all nodes in the list
                    for (AccessibilityNodeInfo node : urlNodes) {
                        if (node != null) {
                            node.recycle();
                        }
                    }
                    if (extractedText != null && !extractedText.trim().isEmpty()) {
                        Log.d(TAG, "Extracted text from viewId: " + viewId + " -> " + extractedText);
                        return extractedText.trim();
                    }
                }
            }

            // Strategy 2: Traverse node tree looking for URL-like content (max depth 30)
            return findUrlInNodeTree(rootNode, 0);

        } catch (Exception e) {
            Log.e(TAG, "Error extracting URL from " + packageName, e);
            return null;
        } finally {
            rootNode.recycle();
        }
    }

    /**
     * Recursively extract text or content description from a node or its children.
     */
    private String getTextFromNode(AccessibilityNodeInfo node) {
        if (node == null) return null;

        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            return text.toString();
        }

        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null && contentDesc.length() > 0) {
            String cleaned = cleanUrlFromContentDesc(contentDesc.toString());
            if (cleaned != null && !cleaned.isEmpty()) {
                return cleaned;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                String childText = getTextFromNode(child);
                child.recycle();
                if (childText != null && !childText.trim().isEmpty()) {
                    return childText;
                }
            }
        }
        return null;
    }

    /**
     * Helper to clean Firefox url/domain from content description.
     * e.g., " m.youtube.com. Search or enter address" -> "m.youtube.com"
     */
    private String cleanUrlFromContentDesc(String desc) {
        if (desc == null) return null;
        String cleaned = desc.trim();
        String[] suffixes = {
            ". search or enter address",
            ". search or type web address",
            " search or enter address",
            " search or type web address",
            ". search or type url",
            ". search"
        };
        for (String suffix : suffixes) {
            int idx = cleaned.toLowerCase().indexOf(suffix);
            if (idx > 0) {
                cleaned = cleaned.substring(0, idx).trim();
            }
        }
        return cleaned;
    }

    /**
     * Recursively search the accessibility node tree for URL-like text.
     * Looks for EditText nodes containing domain-like content.
     */
    private String findUrlInNodeTree(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 30) return null; // Max depth 30 to support deep Compose layouts

        try {
            // Check if this node has URL-like text
            CharSequence text = node.getText();
            String textStr = null;
            if (text != null && text.length() > 0) {
                textStr = text.toString().trim();
            } else {
                CharSequence contentDesc = node.getContentDescription();
                if (contentDesc != null && contentDesc.length() > 0) {
                    textStr = cleanUrlFromContentDesc(contentDesc.toString());
                }
            }

            if (textStr != null && !textStr.isEmpty()) {
                // Check if the node is an EditText (typical URL bar)
                String className = node.getClassName() != null
                        ? node.getClassName().toString() : "";

                if (className.contains("EditText") || className.contains("UrlBar")
                        || className.contains("AutoCompleteTextView")
                        || className.contains("View")) { // View in Compose

                    // Check if the text looks like a URL
                    if (DomainMatcher.looksLikeUrl(textStr) && !DomainMatcher.isSearchQuery(textStr)) {
                        return textStr;
                    }
                }
            }

            // Recurse into children
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    String result = findUrlInNodeTree(child, depth + 1);
                    child.recycle();
                    if (result != null) {
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            // Silently handle - node tree can be unstable
        }

        return null;
    }

    /**
     * Check if the URL is allowed and block if necessary.
     */
    private void checkAndBlockUrl(String url, String browserPackage) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        String cleanedUrl = url.trim().toLowerCase();

        // 1. Allow browser UI/settings/prompts
        if (isBrowserPromptOrSettings(cleanedUrl)) {
            return;
        }

        // 2. Extract domain if it looks like a URL
        String domain = DomainMatcher.extractDomain(url);
        if (domain != null && !domain.isEmpty()) {
            // Check against whitelist
            if (DomainMatcher.isAllowed(domain, allowedDomains)) {
                return; // Whitelisted domain is allowed
            }
            // Not whitelisted -> Block it!
            triggerBlock(domain, url, browserPackage);
            return;
        }

        // 3. Fallback for page titles (when domain extraction returns null, e.g. "Google" or "Facebook")
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        boolean hasWeb = false;
        if (rootNode != null) {
            hasWeb = hasWebView(rootNode, 0);
            rootNode.recycle();
        }

        if (hasWeb) {
            // Webpage is loaded. Check if the page title matches any allowed domain.
            boolean isAllowedTitle = false;
            for (String allowed : allowedDomains) {
                String mainPart = getMainDomainPart(allowed);
                if (!mainPart.isEmpty() && cleanedUrl.contains(mainPart)) {
                    isAllowedTitle = true;
                    break;
                }
            }

            if (!isAllowedTitle) {
                // Not whitelisted title -> Block it!
                triggerBlock(cleanedUrl, url, browserPackage);
            }
        }
    }

    private boolean isBrowserPromptOrSettings(String text) {
        String t = text.toLowerCase().trim();
        return t.equals("via") ||
               t.equals("home") ||
               t.equals("new tab") ||
               t.equals("search") ||
               t.equals("search or enter url") ||
               t.equals("search or enter address") ||
               t.equals("search or type web address") ||
               t.equals("homepage") ||
               t.contains("chrome://") ||
               t.contains("about:blank") ||
               t.contains("file://");
    }

    private String getMainDomainPart(String domain) {
        String d = domain.toLowerCase().trim();
        int firstDot = d.indexOf('.');
        if (firstDot > 0) {
            return d.substring(0, firstDot);
        }
        return d;
    }

    private boolean hasWebView(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 30) return false;

        CharSequence className = node.getClassName();
        if (className != null) {
            String classStr = className.toString();
            if (classStr.contains("WebView") || classStr.contains("browser.engine")) {
                return true;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean hasWeb = hasWebView(child, depth + 1);
                child.recycle();
                if (hasWeb) return true;
            }
        }
        return false;
    }

    private void triggerBlock(String domain, String url, String browserPackage) {
        long now = System.currentTimeMillis();
        // Global cooldown to prevent rapid-fire duplicate blocking during page loading transitions
        if ((now - lastBlockTime) < BLOCK_COOLDOWN_MS) {
            return;
        }

        // Prevent re-entrant blocking
        if (!isBlocking.compareAndSet(false, true)) {
            return;
        }

        Log.i(TAG, "BLOCKING: " + domain + " (from " + browserPackage + ")");

        lastBlockTime = now;
        lastBlockedDomain = domain;

        // Increment blocked count
        incrementBlockedCount();

        // Navigate back in the same tab instead of opening a new activity.
        // This takes the user to the previous page (or Firefox home if no history).
        try {
            performGlobalAction(GLOBAL_ACTION_BACK);
            Log.i(TAG, "Pressed Back to navigate away from blocked site: " + domain);
        } catch (Exception e) {
            Log.e(TAG, "Error performing back action", e);
            performGlobalAction(GLOBAL_ACTION_HOME);
        } finally {
            // Reset blocking flag
            isBlocking.set(false);
        }
    }

    /**
     * Increment the blocked attempts counter in SharedPreferences.
     */
    private void incrementBlockedCount() {
        try {
            SharedPreferences prefs = getSharedPreferences(
                    Constants.PREFS_NAME, Context.MODE_PRIVATE);
            int count = prefs.getInt(Constants.PREF_BLOCKED_COUNT, 0);
            prefs.edit().putInt(Constants.PREF_BLOCKED_COUNT, count + 1).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error incrementing blocked count", e);
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "BrowserMonitorService interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "BrowserMonitorService destroyed");
    }

    // ============================================================
    // MULTI RUN APP BLOCKING
    // ============================================================

    /** Cooldown for Multi Run blocking to prevent rapid-fire back actions */
    private long lastMultiRunBlockTime = 0;
    private static final long MULTI_RUN_BLOCK_COOLDOWN_MS = 300;

    /**
     * Check if Browser app blocking is enabled via SharedPreferences.
     * Defaults to FALSE as requested.
     */
    private boolean isBrowserBlockingEnabled() {
        try {
            SharedPreferences prefs = getSharedPreferences(
                    Constants.PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getBoolean(Constants.PREF_PROTECTION_ENABLED, false);
        } catch (Exception e) {
            Log.e(TAG, "Error reading Browser block pref", e);
            return false;
        }
    }

    /**
     * Check if Multi Run app blocking is enabled via SharedPreferences.
     * Defaults to FALSE as requested.
     */
    private boolean isMultiRunBlockEnabled() {
        try {
            SharedPreferences prefs = getSharedPreferences(
                    Constants.PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getBoolean(Constants.PREF_MULTI_RUN_BLOCK_ENABLED, false);
        } catch (Exception e) {
            Log.e(TAG, "Error reading Multi Run block pref", e);
            return false;
        }
    }

    /**
     * Handle blocking for Multi Run app.
     * Blocks:
     * 1. "Device Apps" screen (opened by the "+" Add App button)
     * 2. Search functionality (search icon and search EditText)
     */
    private void handleMultiRunBlocking(AccessibilityEvent event, int eventType) {
        long now = System.currentTimeMillis();
        if ((now - lastMultiRunBlockTime) < MULTI_RUN_BLOCK_COOLDOWN_MS) {
            return; // Cooldown to avoid rapid-fire actions
        }

        // Block "Device Apps" screen on window state/content change
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (isMultiRunBlockedScreen()) {
                Log.i(TAG, "BLOCKING Multi Run: Device Apps screen detected");
                lastMultiRunBlockTime = now;
                performGlobalAction(GLOBAL_ACTION_BACK);
                return;
            }
        }

        // Block search EditText focus in Multi Run
        if (eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                try {
                    String className = source.getClassName() != null
                            ? source.getClassName().toString() : "";
                    if (className.contains("EditText")
                            || className.contains("SearchView")
                            || className.contains("AutoCompleteTextView")) {
                        Log.i(TAG, "BLOCKING Multi Run: Search focus detected");
                        lastMultiRunBlockTime = now;
                        performGlobalAction(GLOBAL_ACTION_BACK);
                    }
                } finally {
                    source.recycle();
                }
            }
        }

        // Block text change in Multi Run search
        if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            Log.i(TAG, "BLOCKING Multi Run: Text input in search detected");
            lastMultiRunBlockTime = now;
            performGlobalAction(GLOBAL_ACTION_BACK);
        }
    }

    /**
     * Check if the current Multi Run screen is one that should be blocked.
     * Specifically looks for the "Device Apps" title text.
     */
    private boolean isMultiRunBlockedScreen() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return false;

        try {
            // 1. Check for "Device Apps"
            List<AccessibilityNodeInfo> deviceAppsNodes =
                    rootNode.findAccessibilityNodeInfosByText("Device Apps");
            if (deviceAppsNodes != null && !deviceAppsNodes.isEmpty()) {
                for (AccessibilityNodeInfo node : deviceAppsNodes) {
                    if (node != null) {
                        node.recycle();
                    }
                }
                Log.d(TAG, "Found Device Apps screen");
                return true;
            }

            // 2. Check for "Search apps"
            List<AccessibilityNodeInfo> searchAppsNodes =
                    rootNode.findAccessibilityNodeInfosByText("Search apps");
            if (searchAppsNodes != null && !searchAppsNodes.isEmpty()) {
                for (AccessibilityNodeInfo node : searchAppsNodes) {
                    if (node != null) {
                        node.recycle();
                    }
                }
                Log.d(TAG, "Found Search apps screen");
                return true;
            }

            // 3. Scan recursively for "Install (" button
            if (scanNodeForInstallButton(rootNode, 0)) {
                return true;
            }

            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking Multi Run blocked screen", e);
            return false;
        } finally {
            rootNode.recycle();
        }
    }

    /**
     * Scan recursively for Install button text like "Install (0)"
     */
    private boolean scanNodeForInstallButton(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 40) return false;
        try {
            CharSequence textSeq = node.getText();
            if (textSeq != null) {
                String text = textSeq.toString().trim();
                if (text.startsWith("Install (") && text.endsWith(")")) {
                    Log.d(TAG, "Found Install button node: " + text);
                    return true;
                }
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    boolean found = scanNodeForInstallButton(child, depth + 1);
                    child.recycle();
                    if (found) return true;
                }
            }
        } catch (Exception e) {
            // ignore unstable hierarchy checks
        }
        return false;
    }
}
