package com.buchin.healthsync

import android.content.Intent
import android.content.pm.PackageManager
import android.health.connect.HealthConnectManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single status screen. No food logging here — this app is plumbing only:
 * it moves meals between Health Connect (Samsung Health) and Google Drive,
 * where the Calorie Tracker picks them up, and writes Workout-space
 * sessions into Health Connect (one-way, v1.1.0).
 *
 * Buttons: Sign in with Google (Drive) / Grant Health Connect permissions /
 * Sync now. Status text shows Health Connect state, Drive state, and the
 * last sync result.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var googleSignInClient: GoogleSignInClient

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        val missing = HealthSync.ALL_PERMISSIONS - granted
        if (missing.isEmpty()) {
            toast("Health Connect permissions granted")
        } else {
            // On some devices the permission screen never appears and the
            // request comes back empty. Fall back to the Health Connect
            // settings screen, where the permissions can be toggled by hand.
            toast("Permission screen did not open — opening Health Connect settings; turn on Nutrition and Exercise for Health Sync there")
            try {
                openHealthConnectSettings()
            } catch (e: Exception) {
                toast("Could not open Health Connect settings: ${e.message}")
            }
        }
        refreshStatus()
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account: GoogleSignInAccount = task.result
            toast("Signed in as ${account.email}")
        } catch (e: Exception) {
            toast("Google sign-in failed: ${e.message}")
        }
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.status_text)

        googleSignInClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                // Drive scope: the app only ever reads/writes the two sync
                // files by name, but the Drive API needs the full scope to
                // reach a file the phone app did not create itself.
                .requestScopes(Scope("https://www.googleapis.com/auth/drive"))
                // Web-application OAuth client ID (BuildConfig). The Android
                // client (package + signing SHA-1) must exist in the same
                // Google Cloud project; see the setup guide.
                .requestIdToken(BuildConfig.GOOGLE_OAUTH_CLIENT_ID)
                .build()
        )

        findViewById<Button>(R.id.btn_sign_in).setOnClickListener {
            if (!DriveSync.isConfigured()) {
                toast("Drive is not configured yet — see the setup guide (OAuth client step)")
                return@setOnClickListener
            }
            signInLauncher.launch(googleSignInClient.signInIntent)
        }

        findViewById<Button>(R.id.btn_permissions).setOnClickListener {
            lifecycleScope.launch {
                val client = HealthConnectClient.getOrCreate(this@MainActivity)
                val granted = client.permissionController.getGrantedPermissions()
                val missing = HealthSync.ALL_PERMISSIONS - granted
                if (missing.isEmpty()) {
                    toast("All Health Connect permissions already granted")
                } else {
                    permissionLauncher.launch(missing)
                }
            }
        }

        findViewById<Button>(R.id.btn_sync_now).setOnClickListener {
            lifecycleScope.launch {
                setStatus("Syncing…")
                val outcome = try {
                    withContext(Dispatchers.IO) { HealthSync.syncNow(this@MainActivity) }
                } catch (e: Exception) {
                    setStatus("Sync failed: ${e.message}")
                    return@launch
                }
                val notes = if (outcome.notes.isEmpty()) "" else "\n" + outcome.notes.joinToString("\n")
                setStatus(
                    "Last sync: ${HealthSync.lastSyncText(this@MainActivity)}\n" +
                        "Exported: ${outcome.exported} meals, removed: ${outcome.deletedFromHealth}\n" +
                        "Workouts: ${outcome.exportedWorkouts} exported, ${outcome.deletedWorkouts} removed\n" +
                        "Imported: ${outcome.imported} meals, skipped: ${outcome.skipped}$notes"
                )
                refreshStatus()
            }
        }

        findViewById<Button>(R.id.btn_sign_out).setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                toast("Signed out of Google Drive")
                refreshStatus()
            }
        }

        refreshStatus()
    }

    private fun refreshStatus() {
        lifecycleScope.launch {
            val lines = mutableListOf<String>()
            lines += "Health Sync v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})"
            // Health Connect availability
            val sdkStatus = try {
                HealthConnectClient.getSdkStatus(this@MainActivity)
            } catch (e: Exception) {
                -1
            }
            lines += when (sdkStatus) {
                HealthConnectClient.SDK_AVAILABLE -> "Health Connect: available"
                HealthConnectClient.SDK_UNAVAILABLE -> "Health Connect: not available on this device"
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    "Health Connect: update required (Play Store)"
                else -> "Health Connect: status unknown ($sdkStatus)"
            }
            // Diagnostics: why the permission screen may never appear.
            // On Android 14+ the Jetpack request is a plain runtime
            // permission request; if the system auto-denies it (e.g. the
            // permission was denied twice, "don't ask again"), the
            // request comes back empty with no dialog, and the app never
            // appears in Health Connect's App permissions list.
            try {
                val pm = packageManager
                val info = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(
                            "com.google.android.apps.healthdata",
                            PackageManager.PackageInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo("com.google.android.apps.healthdata", 0)
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
                lines += if (info == null) {
                    "Diag: Health Connect app (healthdata): NOT INSTALLED"
                } else {
                    "Diag: Health Connect app (healthdata): installed v${info.versionName}, enabled=${info.applicationInfo?.enabled}"
                }
                fun resolves(intent: Intent): Boolean =
                    pm.queryIntentActivities(
                        intent,
                        PackageManager.MATCH_DEFAULT_ONLY
                    ).isNotEmpty()
                val settingsIntent =
                    Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                lines += "Diag: settings intent resolves: ${resolves(settingsIntent)}"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val manageIntent = Intent(
                        HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS
                    ).addCategory(HealthConnectManager.CATEGORY_HEALTH_PERMISSIONS)
                    lines += "Diag: manage-permissions intent resolves: ${resolves(manageIntent)}"
                }
                for (perm in HealthSync.ALL_PERMISSIONS) {
                    val granted = checkSelfPermission(perm) ==
                        PackageManager.PERMISSION_GRANTED
                    val rationale =
                        shouldShowRequestPermissionRationale(perm)
                    lines += "Diag: $perm granted=$granted rationale=$rationale"
                }
            } catch (e: Exception) {
                lines += "Diag: check failed (${e.message})"
            }
            // Permissions
            try {
                val client = HealthConnectClient.getOrCreate(this@MainActivity)
                val granted = client.permissionController.getGrantedPermissions()
                val missing = HealthSync.ALL_PERMISSIONS - granted
                lines += if (missing.isEmpty()) {
                    "Health Connect permissions: Read + Write nutrition, Write exercise granted"
                } else {
                    "Health Connect permissions: MISSING ${missing.size} (tap 'Grant Health Connect permissions')"
                }
            } catch (e: Exception) {
                lines += "Health Connect permissions: could not check (${e.message})"
            }
            // Drive
            lines += when {
                !DriveSync.isConfigured() ->
                    "Google Drive: NOT CONFIGURED — OAuth client step in the setup guide is still needed"
                DriveSync.isSignedIn(this@MainActivity) ->
                    "Google Drive: signed in (${GoogleSignIn.getLastSignedInAccount(this@MainActivity)?.email})"
                else -> "Google Drive: not signed in (tap 'Sign in with Google')"
            }
            lines += "Background sync: every 15 min when online"
            lines += "Last sync: ${HealthSync.lastSyncText(this@MainActivity)}"
            setStatus(lines.joinToString("\n"))
        }
    }

    private fun setStatus(text: String) {
        statusView.text = text
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun openHealthConnectSettings() {
        // Try the Jetpack settings action first, then the framework
        // manage-permissions action (Android 14+). Either opens the
        // Health Connect screen where the permissions can be toggled.
        val manageIntent = Intent(
            HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS
        ).addCategory(HealthConnectManager.CATEGORY_HEALTH_PERMISSIONS)
        val settingsIntent =
            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        val tried = listOf(settingsIntent, manageIntent)
        var lastError: Exception? = null
        for (intent in tried) {
            try {
                startActivity(intent)
                return
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("no Health Connect settings activity")
    }
}
