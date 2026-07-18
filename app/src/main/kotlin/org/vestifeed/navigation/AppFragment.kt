package org.vestifeed.navigation

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import org.vestifeed.app.ogFetcher
import org.vestifeed.entries.EntriesFragment
import org.vestifeed.feeds.FeedsFragment
import org.vestifeed.lan.LocalNetworkPermissionRequester

abstract class AppFragment : Fragment() {
    private val localNetworkPermissionRequester = LocalNetworkPermissionRequester()

    private val localNetworkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            localNetworkPermissionRequester.onPermissionResult(it)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as Activity).binding.bottomNav.isVisible =
            parentFragmentManager.backStackEntryCount == 0 &&
                    (this is EntriesFragment || this is FeedsFragment)

        viewLifecycleOwner.lifecycleScope.launch {
            ogFetcher().lastDownload.collect {
                onOpenGraphImageDownloaded()
            }
        }

    }

    protected suspend fun requestLocalNetworkAccess(urls: Collection<HttpUrl>): Boolean {
        return localNetworkPermissionRequester.requestIfNeeded(
            context = requireContext(),
            urls = urls,
            launcher = localNetworkPermissionLauncher,
        )
    }

    open fun onOpenGraphImageDownloaded() {}
}