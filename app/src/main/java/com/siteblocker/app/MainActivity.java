package com.siteblocker.app;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.siteblocker.app.admin.SiteBlockerDeviceAdmin;
import com.siteblocker.app.util.Constants;

/**
 * Main Activity — Dashboard for Site Blocker.
 *
 * Features:
 * - Animated shield showing protection status
 * - Allowed sites count (hardcoded: 2)
 * - Accessibility Service toggle
 * - Device Admin toggle
 * 
 * Note: Site adding/removing has been disabled.
 * Only accounts.google.com and familylink.google.com are allowed.
 */
public class MainActivity extends AppCompatActivity {

    // Views
    private View shieldGlowRing;
    private ImageView shieldIcon;
    private TextView statusChip;
    private TextView statusSubtitle;
    private TextView allowedCount;
    private MaterialSwitch accessibilityToggle;
    private MaterialSwitch deviceAdminToggle;
    private MaterialSwitch multiRunToggle;
    private MaterialSwitch browserProtectionToggle;
    private View accessibilityCard;
    private View deviceAdminCard;
    private View multiRunCard;
    private View browserProtectionCard;

    // Data
    private DevicePolicyManager devicePolicyManager;
    private ComponentName deviceAdminComponent;

    // Activity result launchers
    private ActivityResultLauncher<Intent> deviceAdminLauncher;

    // Animation
    private Animation pulseAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize
        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdminComponent = new ComponentName(this, SiteBlockerDeviceAdmin.class);

        // Setup activity result launchers
        deviceAdminLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> updateToggleStates()
        );

        // Bind views
        bindViews();

        // Setup UI interactions
        setupToggles();

        // Load animation
        pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse);

        // Set hardcoded allowed count
        allowedCount.setText(String.valueOf(Constants.ALLOWED_DOMAINS.size()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateToggleStates();
        updateShieldStatus();
    }

    /**
     * Bind all views from the layout.
     */
    private void bindViews() {
        shieldGlowRing = findViewById(R.id.shieldGlowRing);
        shieldIcon = findViewById(R.id.shieldIcon);
        statusChip = findViewById(R.id.statusChip);
        statusSubtitle = findViewById(R.id.statusSubtitle);
        allowedCount = findViewById(R.id.allowedCount);
        accessibilityToggle = findViewById(R.id.accessibilityToggle);
        deviceAdminToggle = findViewById(R.id.deviceAdminToggle);
        accessibilityCard = findViewById(R.id.accessibilityCard);
        deviceAdminCard = findViewById(R.id.deviceAdminCard);
        multiRunToggle = findViewById(R.id.multiRunToggle);
        multiRunCard = findViewById(R.id.multiRunCard);
        browserProtectionToggle = findViewById(R.id.browserProtectionToggle);
        browserProtectionCard = findViewById(R.id.browserProtectionCard);
    }

    /**
     * Setup toggle switches for Accessibility and Device Admin.
     */
    private void setupToggles() {
        // Accessibility Toggle
        accessibilityToggle.setOnClickListener(v -> {
            // We can't programmatically enable/disable accessibility service.
            // Open system settings for the user to toggle it.
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, R.string.enable_accessibility_prompt, Toast.LENGTH_LONG).show();

            // Reset toggle to actual state (will be updated in onResume)
            v.postDelayed(this::updateToggleStates, 500);
        });

        // Device Admin Toggle
        deviceAdminToggle.setOnClickListener(v -> {
            if (isDeviceAdminActive()) {
                // Disable Device Admin
                try {
                    devicePolicyManager.removeActiveAdmin(deviceAdminComponent);
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to disable Device Admin", Toast.LENGTH_SHORT).show();
                }
            } else {
                // Enable Device Admin
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        getString(R.string.device_admin_description));
                deviceAdminLauncher.launch(intent);
            }
            updateToggleStates();
        });

        // Card click also triggers toggle
        accessibilityCard.setOnClickListener(v -> accessibilityToggle.performClick());
        deviceAdminCard.setOnClickListener(v -> deviceAdminToggle.performClick());

        // Multi Run Protection Toggle
        multiRunToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(Constants.PREF_MULTI_RUN_BLOCK_ENABLED, isChecked).apply();
            updateMultiRunCardState(isChecked);
        });
        multiRunCard.setOnClickListener(v -> multiRunToggle.toggle());

        // Browser Protection Toggle
        browserProtectionToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(Constants.PREF_PROTECTION_ENABLED, isChecked).apply();
            updateBrowserCardState(isChecked);
            updateShieldStatus();
        });
        browserProtectionCard.setOnClickListener(v -> browserProtectionToggle.toggle());
    }

    /**
     * Update toggle states based on actual system state.
     */
    private void updateToggleStates() {
        boolean accessibilityEnabled = isAccessibilityServiceEnabled();
        boolean adminEnabled = isDeviceAdminActive();

        // Update accessibility toggle
        accessibilityToggle.setChecked(accessibilityEnabled);
        accessibilityCard.setBackground(ContextCompat.getDrawable(this,
                accessibilityEnabled ? R.drawable.bg_glass_card_active : R.drawable.bg_glass_card));

        // Update device admin toggle
        deviceAdminToggle.setChecked(adminEnabled);
        deviceAdminCard.setBackground(ContextCompat.getDrawable(this,
                adminEnabled ? R.drawable.bg_glass_card_active : R.drawable.bg_glass_card));

        // Update Multi Run toggle
        boolean multiRunEnabled = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(Constants.PREF_MULTI_RUN_BLOCK_ENABLED, false);
        multiRunToggle.setChecked(multiRunEnabled);
        updateMultiRunCardState(multiRunEnabled);

        // Update Browser Protection toggle
        boolean browserEnabled = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(Constants.PREF_PROTECTION_ENABLED, false);
        browserProtectionToggle.setChecked(browserEnabled);
        updateBrowserCardState(browserEnabled);

        // Update shield
        updateShieldStatus();
    }

    /**
     * Update the shield icon and status based on protection state.
     */
    private void updateShieldStatus() {
        boolean accessibilityEnabled = isAccessibilityServiceEnabled();
        boolean browserEnabled = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(Constants.PREF_PROTECTION_ENABLED, false);
        boolean isProtecting = accessibilityEnabled && browserEnabled;

        if (isProtecting) {
            // Active state
            statusChip.setText(R.string.protection_active);
            statusChip.setTextColor(ContextCompat.getColor(this, R.color.success));
            statusChip.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_status_chip_active));
            statusSubtitle.setText(R.string.subtitle_active);
            shieldIcon.setColorFilter(ContextCompat.getColor(this, R.color.success));
            shieldIcon.setImageResource(R.drawable.ic_shield); // Reset just in case

            // Start glow animation
            shieldGlowRing.clearAnimation();
            shieldGlowRing.startAnimation(pulseAnimation);
            shieldGlowRing.setAlpha(0.5f);
        } else {
            // Inactive state
            statusChip.setText(R.string.protection_inactive);
            statusChip.setTextColor(ContextCompat.getColor(this, R.color.error));
            statusChip.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_status_chip));
            if (!accessibilityEnabled) {
                statusSubtitle.setText(R.string.subtitle_inactive);
            } else {
                statusSubtitle.setText(R.string.subtitle_browser_disabled);
            }
            shieldIcon.setColorFilter(ContextCompat.getColor(this, R.color.shield_inactive));

            // Stop glow animation
            shieldGlowRing.clearAnimation();
            shieldGlowRing.setAlpha(0.15f);
        }
    }

    // === Utility Methods ===

    /**
     * Check if our Accessibility Service is currently enabled.
     */
    private boolean isAccessibilityServiceEnabled() {
        String serviceName = getPackageName() + "/"
                + "com.siteblocker.app.accessibility.BrowserMonitorService";

        try {
            int enabled = Settings.Secure.getInt(
                    getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 0);

            if (enabled == 0) return false;

            String settingValue = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

            if (settingValue != null) {
                TextUtils.SimpleStringSplitter splitter =
                        new TextUtils.SimpleStringSplitter(':');
                splitter.setString(settingValue);

                while (splitter.hasNext()) {
                    String accessibilityService = splitter.next();
                    if (accessibilityService.equalsIgnoreCase(serviceName)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Settings not found
        }
        return false;
    }

    /**
     * Check if Device Admin is currently active.
     */
    private boolean isDeviceAdminActive() {
        return devicePolicyManager != null
                && devicePolicyManager.isAdminActive(deviceAdminComponent);
    }

    /**
     * Update Multi Run card visual state based on toggle.
     */
    private void updateMultiRunCardState(boolean enabled) {
        multiRunCard.setBackground(ContextCompat.getDrawable(this,
                enabled ? R.drawable.bg_glass_card_active : R.drawable.bg_glass_card));
    }

    /**
     * Update Browser Protection card visual state based on toggle.
     */
    private void updateBrowserCardState(boolean enabled) {
        browserProtectionCard.setBackground(ContextCompat.getDrawable(this,
                enabled ? R.drawable.bg_glass_card_active : R.drawable.bg_glass_card));
    }
}
