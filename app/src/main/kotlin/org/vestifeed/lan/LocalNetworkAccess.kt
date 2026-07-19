package org.vestifeed.lan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.net.InetAddress
import kotlin.coroutines.resume

object LocalNetworkAccess {
    private const val ENFORCEMENT_SDK = 37

    @RequiresApi(ENFORCEMENT_SDK)
    val permission: String = Manifest.permission.ACCESS_LOCAL_NETWORK

    fun isEnforced(): Boolean {
        return Build.VERSION.SDK_INT >= ENFORCEMENT_SDK
    }

    fun isGranted(context: Context): Boolean {
        return !isEnforced() || ContextCompat.checkSelfPermission(
            context,
            permission,
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun requiresPermission(url: HttpUrl): Boolean {
        if (!isEnforced()) {
            return false
        }

        val host = url.host.lowercase()
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) {
            return true
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                InetAddress.getAllByName(host).any { it.isLocalNetworkAddress() }
            }.getOrDefault(false)
        }
    }

    private fun InetAddress.isLocalNetworkAddress(): Boolean {
        if (isAnyLocalAddress || isLinkLocalAddress || isLoopbackAddress || isMulticastAddress || isSiteLocalAddress) {
            return true
        }

        val address = address
        if (address.size == 4) {
            val first = address[0].toInt() and 0xff
            val second = address[1].toInt() and 0xff
            return first == 100 && second in 64..127
        }

        return address.size == 16 && address[0].toInt() and 0xfe == 0xfc
    }
}

class LocalNetworkPermissionRequester {
    private var continuation: kotlinx.coroutines.CancellableContinuation<Boolean>? = null

    suspend fun requestIfNeeded(
        context: Context,
        urls: Collection<HttpUrl>,
        launcher: ActivityResultLauncher<String>,
    ): Boolean {
        if (LocalNetworkAccess.isGranted(context)) {
            return true
        }

        if (!requiresPermission(urls)) {
            return true
        }

        return suspendCancellableCoroutine { currentContinuation ->
            check(continuation == null)
            continuation = currentContinuation
            currentContinuation.invokeOnCancellation {
                if (continuation === currentContinuation) {
                    continuation = null
                }
            }
            launcher.launch(LocalNetworkAccess.permission)
        }
    }

    private suspend fun requiresPermission(urls: Collection<HttpUrl>): Boolean {
        for (url in urls) {
            if (LocalNetworkAccess.requiresPermission(url)) {
                return true
            }
        }
        return false
    }

    fun onPermissionResult(isGranted: Boolean) {
        val currentContinuation = continuation ?: return
        continuation = null
        if (currentContinuation.isActive) {
            currentContinuation.resume(isGranted)
        }
    }
}
