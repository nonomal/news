package org.vestifeed.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vestifeed.R
import org.vestifeed.app.App
import org.vestifeed.databinding.FragmentLogBinding
import org.vestifeed.navigation.AppFragment

class LogFragment : AppFragment() {

    private var _binding: FragmentLogBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { (requireContext().applicationContext as App).db }

    private val _state = MutableStateFlow(State())

    private val adapter = LogAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        binding.list.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@LogFragment.adapter
        }

        binding.chipTrace.text = LogLevel.TRACE.value
        binding.chipDebug.text = LogLevel.DEBUG.value
        binding.chipInfo.text = LogLevel.INFO.value
        binding.chipWarn.text = LogLevel.WARN.value
        binding.chipError.text = LogLevel.ERROR.value

        binding.chipDebug.isChecked = true
        binding.filterChips.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) {
                binding.chipDebug.isChecked = true
                return@setOnCheckedStateChangeListener
            }
            val minLevel = when (checkedIds.first()) {
                R.id.chipTrace -> LogLevel.TRACE
                R.id.chipDebug -> LogLevel.DEBUG
                R.id.chipInfo -> LogLevel.INFO
                R.id.chipWarn -> LogLevel.WARN
                R.id.chipError -> LogLevel.ERROR
                else -> LogLevel.DEBUG
            }
            loadLogs(minLevel)
        }

        binding.clearLogs.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.clear_logs_warning)
                .setPositiveButton(R.string.delete) { _, _ -> clearLogs() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        loadLogs(LogLevel.DEBUG)
    }

    private fun loadLogs(minLevel: LogLevel) {
        _state.update { it.copy(isLoading = true) }

        viewLifecycleOwner.lifecycleScope.launch {
            val logs = withContext(Dispatchers.IO) {
                db.log.selectByMinLevel(minLevel).map { LogAdapter.Item(it) }
            }

            _state.update {
                it.copy(
                    isLoading = false,
                    logs = logs,
                    minLevel = minLevel,
                )
            }

            updateUi()
        }
    }

    private fun clearLogs() {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.log.deleteAll()
            }
            loadLogs(_state.value.minLevel)
        }
    }

    private fun updateUi() {
        val currentState = _state.value
        binding.progress.isVisible = currentState.isLoading
        binding.list.isVisible = !currentState.isLoading && currentState.logs.isNotEmpty()
        adapter.submitList(currentState.logs)

        if (!currentState.isLoading && currentState.logs.isEmpty()) {
            binding.list.isVisible = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class State(
        val isLoading: Boolean = true,
        val logs: List<LogAdapter.Item> = emptyList(),
        val minLevel: LogLevel = LogLevel.DEBUG,
    )
}