package com.siteblocker.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.siteblocker.app.util.Constants;

/**
 * Activity displayed when a user tries to access a blocked (non-whitelisted) site.
 * Shows the blocked domain and provides options to go back or open the app.
 */
public class BlockedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked);

        // Get blocked domain from intent
        String blockedDomain = getIntent().getStringExtra(Constants.EXTRA_BLOCKED_DOMAIN);

        // Setup UI
        TextView blockedDomainText = findViewById(R.id.blockedDomainText);
        MaterialButton goBackButton = findViewById(R.id.goBackButton);
        MaterialButton openAppButton = findViewById(R.id.openAppButton);

        if (blockedDomain != null && !blockedDomain.isEmpty()) {
            blockedDomainText.setText(blockedDomain);
        } else {
            blockedDomainText.setText("Unknown");
        }

        // Go Back button — finish this activity
        goBackButton.setOnClickListener(v -> {
            finish();
        });

        // Open App button — launch main activity
        openAppButton.setOnClickListener(v -> {
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(mainIntent);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
