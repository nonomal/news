package org.vestifeed.auth

import android.graphics.drawable.Animatable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import org.vestifeed.navigation.AppFragment
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import org.vestifeed.R
import org.vestifeed.app.App
import org.vestifeed.app.db
import org.vestifeed.databinding.FragmentAuthBinding
import org.vestifeed.db.table.ConfTable
import org.vestifeed.db.table.LogTable
import org.vestifeed.entries.EntriesFilter
import org.vestifeed.entries.EntriesFragment
import org.vestifeed.sync.BackgroundSyncScheduler
import java.util.concurrent.TimeUnit

class AuthFragment : AppFragment() {

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.initButtons()
        db().log.insert(
            LogTable.InsertArgs(
                level = "debug",
                tag = "AuthFragment",
                message = "onViewCreated",
            )
        )
    }

    override fun onResume() {
        super.onResume()
        (binding.icon.drawable as? Animatable)?.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun FragmentAuthBinding.initButtons() {
        useMinifluxBackend.setOnClickListener {
            parentFragmentManager.commit {
                replace<MinifluxAuthFragment>(R.id.fragmentContainerView)
                addToBackStack(null)
            }
        }

        useEmbeddedBackend.setOnClickListener {
            val db = (requireContext().applicationContext as App).db
            val syncScheduler = BackgroundSyncScheduler(requireContext())

            db().log.insert(
                LogTable.InsertArgs(
                    level = "debug",
                    tag = "AuthFragment",
                    message = "Switching to embedded backend",
                )
            )

            db.conf.update {
                it.copy(
                    backend = ConfTable.BACKEND_STANDALONE,
                    syncOnStartup = false,
                    backgroundSyncIntervalMillis = TimeUnit.HOURS.toMillis(12),
                )
            }

            syncScheduler.schedule()

            parentFragmentManager.commit {
                replace(
                    R.id.fragmentContainerView,
                    EntriesFragment::class.java,
                    bundleOf("filter" to EntriesFilter.Unread),
                )
            }
        }
    }
}