package auth

import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentAuthBinding
import common.AppFragment
import common.ConfRepository
import common.app
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit

class AuthFragment : AppFragment(
    showToolbar = false,
) {

    private val model: AuthViewModel by viewModel()

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val conf = runBlocking { model.selectConf().first() }

        return if (conf.backend.isBlank()) {
            _binding = FragmentAuthBinding.inflate(inflater, container, false)
            binding.root
        } else {
            val intent = requireActivity().intent
            val sharedFeedUrl = (intent?.dataString ?: intent?.getStringExtra(Intent.EXTRA_TEXT))?.trim()

            if (sharedFeedUrl.isNullOrBlank()) {
                findNavController().navigate(R.id.action_authFragment_to_entriesFragment)
            } else {
                val directions = AuthFragmentDirections.actionAuthFragmentToFeedsFragment(sharedFeedUrl)
                findNavController().navigate(directions)
            }

            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.initButtons()
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
        useStandaloneBackend.setOnClickListener {
            lifecycleScope.launchWhenResumed {
                model.upsertConf(
                    model.selectConf().first().copy(
                        backend = ConfRepository.BACKEND_STANDALONE,
                        syncOnStartup = false,
                        backgroundSyncIntervalMillis = TimeUnit.HOURS.toMillis(12),
                        initialSyncCompleted = true,
                    )
                )

                app().setupBackgroundSync(override = true)

                binding.root.animate().alpha(0f).setDuration(150).withEndAction {
                    findNavController().navigate(R.id.action_authFragment_to_entriesFragment)
                }
            }
        }

        useMinifluxBackend.setOnClickListener {
            binding.root.animate().alpha(0f).setDuration(150).withEndAction {
                findNavController().navigate(R.id.action_authFragment_to_minifluxAuthFragment)
            }
        }

        useNextcloudBackend.setOnClickListener {
            binding.root.animate().alpha(0f).setDuration(150).withEndAction {
                findNavController().navigate(R.id.action_authFragment_to_nextcloudAuthFragment)
            }
        }
    }
}