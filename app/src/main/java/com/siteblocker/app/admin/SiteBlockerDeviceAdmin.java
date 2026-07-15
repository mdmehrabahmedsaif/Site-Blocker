package com.siteblocker.app.admin;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * Device Admin Receiver for Site Blocker.
 * 
 * When enabled as Device Admin, this prevents the app from being uninstalled
 * without first disabling Device Admin (which requires going through app settings).
 * This provides tamper-resistance for the blocking functionality.
 */
public class SiteBlockerDeviceAdmin extends DeviceAdminReceiver {

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Toast.makeText(context, "Device Admin enabled — app uninstall protection active",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Toast.makeText(context, "Device Admin disabled — app can now be uninstalled",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        // Show a warning when the user tries to disable Device Admin
        return "Disabling Device Admin will remove uninstall protection for Site Blocker. Are you sure?";
    }
}
