package org.vestifeed.navigation

import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.navigation.NavigationBarView.OnItemReselectedListener
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.vestifeed.R
import org.vestifeed.app.App
import org.vestifeed.app.db
import org.vestifeed.app.sync
import org.vestifeed.auth.AuthEvents
import org.vestifeed.auth.AuthFragment
import org.vestifeed.db.table.ConfTable
import org.vestifeed.databinding.ActivityBinding
import org.vestifeed.entries.EntriesFilter
import org.vestifeed.entries.EntriesFragment
import org.vestifeed.entries.toBundle
import org.vestifeed.feeds.FeedsFragment
import org.vestifeed.lan.LocalNetworkPermissionRequester

class Activity : AppCompatActivity() {

    lateinit var binding: ActivityBinding

    private val localNetworkPermissionRequester = LocalNetworkPermissionRequester()

    private val localNetworkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            localNetworkPermissionRequester.onPermissionResult(it)
        }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                finish()
            }
        }
    }

    private val fragmentLifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            updateBottomNavVisibility()
        }
    }

    private fun updateBottomNavVisibility() {
        val top = supportFragmentManager.fragments.lastOrNull { it.isVisible }
        binding.bottomNav.isVisible = supportFragmentManager.backStackEntryCount == 0 &&
            (top is EntriesFragment || top is FeedsFragment)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            fragmentLifecycleCallbacks,
            false,
        )

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { v, insets ->
            insets.getInsets(WindowInsetsCompat.Type.navigationBars()).let {
                v.updatePadding(bottom = it.bottom)
            }
            insets
        }

        lifecycleScope.launch {
            val conf = db().conf.select()

            if (conf.backend != null && conf.syncOnStartup) {
                val urls = when (conf.backend) {
                    ConfTable.Backend.Miniflux -> {
                        listOfNotNull(conf.minifluxUrl?.toHttpUrlOrNull())
                    }

                    ConfTable.Backend.Embedded -> {
                        db().feed.selectAll()
                            .flatMap { db().link.selectByFeedId(it.id) }
                            .mapNotNull { it.href.toHttpUrlOrNull() }
                    }
                }

                if (localNetworkPermissionRequester.requestIfNeeded(
                        context = this@Activity,
                        urls = urls,
                        launcher = localNetworkPermissionLauncher,
                    )
                ) {
                    Log.d("activity", "sync on startup start")
                    sync().runInBackground()
                    Log.d("activity", "sync on startup end")
                }
            }
        }

        lifecycleScope.launch {
            val conf = db().conf.select()

            if (conf.backend != null) {
                supportFragmentManager.commit {
                    replace(
                        R.id.fragmentContainerView,
                        EntriesFragment::class.java,
                        EntriesFilter.Unread.toBundle(),
                    )
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AuthEvents.invalidationCount
                    .filter { it > 0 }
                    .collect { logOut() }
            }
        }
    }

    private fun logOut() {
        val db = (applicationContext as App).db

        if (db.conf.select().backend == null) {
            return
        }

        db.conf.delete()
        db.transaction {
            db.feed.deleteAll()
            db.entry.deleteAll()
        }

        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        supportFragmentManager.commit {
            replace(
                R.id.fragmentContainerView, AuthFragment::class.java, null
            )
        }

        binding.bottomNav.isVisible = false
    }

    override fun onStart() {
        super.onStart()

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        binding.bottomNav.apply {
            setOnItemSelectedListener { item ->
                when (item.itemId) {
R.id.newsFragment -> {
                        supportFragmentManager.commit {
                            replace(
                                R.id.fragmentContainerView,
                                EntriesFragment::class.java,
                                EntriesFilter.Unread.toBundle(),
                            )
                        }
                        true
                    }

                    R.id.bookmarksFragment -> {
                        supportFragmentManager.commit {
                            replace(
                                R.id.fragmentContainerView,
                                EntriesFragment::class.java,
                                EntriesFilter.Bookmarked.toBundle(),
                            )
                        }
                        true
                    }

                    R.id.feedsFragment -> {
                        supportFragmentManager.commit {
                            replace(
                                R.id.fragmentContainerView,
                                FeedsFragment::class.java,
                                bundleOf("url" to ""),
                            )
                        }
                        true
                    }

                    else -> false
                }
            }

            setOnItemReselectedListener(createOnItemReselectedListener())
        }
    }

    private fun createOnItemReselectedListener(): OnItemReselectedListener {
        return OnItemReselectedListener { item ->
            supportFragmentManager.fragments.forEach { fragment ->
                fragment.childFragmentManager.fragments.forEach {
                    (it as? OnItemReselectedListener)?.onNavigationItemReselected(item)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragmentLifecycleCallbacks)
    }
}
