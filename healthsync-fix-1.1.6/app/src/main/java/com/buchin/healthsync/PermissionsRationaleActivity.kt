package com.buchin.healthsync

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Privacy-policy / permission-rationale screen REQUIRED by Health Connect.
 *
 * Health Connect refuses to show its permission sheet (the request returns
 * empty, no dialog, and the app never appears in Health Connect > App
 * permissions) unless the app exposes an activity for:
 *  - androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE (Android 13 and lower)
 *  - android.intent.action.VIEW_PERMISSION_USAGE + category
 *    android.intent.category.HEALTH_PERMISSIONS (Android 14+, via the
 *    activity-alias in AndroidManifest.xml)
 * Health Connect also opens this screen from its "privacy policy" link.
 */
class PermissionsRationaleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val text = TextView(this).apply {
            setPadding(pad, pad, pad, pad)
            textSize = 16f
            text = getString(R.string.hc_privacy_policy)
        }
        setContentView(ScrollView(this).apply { addView(text) })
        title = getString(R.string.hc_privacy_title)
    }
}
