package org.vestifeed.navigation

import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import okhttp3.HttpUrl
import org.vestifeed.lan.LocalNetworkPermissionRequester

abstract class AppFragment : Fragment() {
    private val localNetworkPermissionRequester = LocalNetworkPermissionRequester()

    private val localNetworkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            localNetworkPermissionRequester.onPermissionResult(it)
        }

    protected suspend fun requestLocalNetworkAccess(urls: Collection<HttpUrl>): Boolean {
        return localNetworkPermissionRequester.requestIfNeeded(
            context = requireContext(),
            urls = urls,
            launcher = localNetworkPermissionLauncher,
        )
    }
}