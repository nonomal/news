package org.vestifeed.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment

/**
 * Helpers for the `POST_NOTIFICATIONS` runtime permission. The runtime
 * permission exists from Android 13 (API 33) onward; the app's `minSdk` is
 * 34, so this helper treats the permission as always requiring a runtime
 * grant.
 *
 * We must distinguish three "denied" states because they warrant different
 * UI:
 *
 * - **Never asked**: permission denied, system prompt has not been shown yet.
 *   `shouldShowRequestPermissionRationale()` returns `false` because the user
 *   hasn't interacted with the prompt. We should still show the warning icon
 *   so the user can trigger the first prompt.
 * - **Previously denied**: the user said "Don't allow" at least once. The
 *   system can still show the prompt again, and `shouldShowRequestPermissionRationale()`
 *   returns `true`. We show the warning icon so the user can retry.
 * - **Permanently denied** ("Don't ask again"): the system will never show
 *   the prompt again. `shouldShowRequestPermissionRationale()` returns
 *   `false` even though we have already asked once. We hide the warning icon
 *   because retrying from inside the app is no longer possible.
 *
 * Because `shouldShowRequestPermissionRationale()` returns `false` in both
 * the "never asked" and "permanently denied" cases, we keep a persisted flag
 * (`hasRequestedOnce`) that tells us whether the user has ever been shown the
 * system prompt. The flag is written the first time the launcher fires and
 * cleared again whenever the user grants the permission, so that a later
 * revocation through system settings once again allows the app to show the
 * system prompt.
 */
object NotificationPermissionAccess {
    val permission: String
        get() = Manifest.permission.POST_NOTIFICATIONS

    fun isGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Whether the warning icon should be visible. Returns `false` when the
     * permission is granted or when the user has reached the
     * permanently-denied state.
     */
    fun shouldShowWarning(
        context: Context,
        fragment: Fragment,
        prefs: NotificationPermissionPrefs,
    ): Boolean {
        if (isGranted(context)) return false
        if (!prefs.hasRequestedOnce()) return true
        return fragment.shouldShowRequestPermissionRationale(permission)
    }
}

/**
 * Persists whether the notification permission prompt has been shown at
 * least once. Required to distinguish the "never asked" state from the
 * "permanently denied" state, which `shouldShowRequestPermissionRationale`
 * collapses into a single `false` return value.
 */
class NotificationPermissionPrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasRequestedOnce(): Boolean = prefs.getBoolean(KEY_REQUESTED_ONCE, false)

    fun markRequestedOnce() {
        prefs.edit { putBoolean(KEY_REQUESTED_ONCE, true) }
    }

    fun reset() {
        prefs.edit { remove(KEY_REQUESTED_ONCE) }
    }

    companion object {
        private const val PREFS_NAME = "notification_permission"
        private const val KEY_REQUESTED_ONCE = "requested_once"
    }
}