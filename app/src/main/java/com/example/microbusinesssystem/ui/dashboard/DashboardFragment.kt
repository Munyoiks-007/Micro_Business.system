package com.example.microbusinesssystem.ui.dashboard

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.example.microbusinesssystem.R
import com.example.microbusinesssystem.data.repository.SessionState
import com.example.microbusinesssystem.databinding.FragmentDashboardBinding
import com.example.microbusinesssystem.ui.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.math.BigDecimal

@AndroidEntryPoint
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDashboardBinding.bind(view)
        clearAccountData()

        binding.btnEditGoals.setOnClickListener { showGoalEditor() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.sessionState.collect { state ->
                        when (state) {
                            is SessionState.Authenticated -> {
                                binding.tvCompanyName.text = state.businessName.uppercase()
                                binding.tvGreeting.text = getString(R.string.welcome_business, state.ownerName)
                            }
                            SessionState.Loading, SessionState.Unauthenticated,
                            is SessionState.ProfileUnavailable -> clearAccountData()
                        }
                    }
                }
                launch {
                    viewModel.todayRevenue.collect { state ->
                        binding.tvTodayRevenue.text = when (state) {
                            is UiState.Success -> formatMoney(state.data)
                            is UiState.Error -> "Unavailable"
                            UiState.Loading, UiState.Idle -> "—"
                        }
                    }
                }
                launch {
                    viewModel.completedSalesCount.collect { count ->
                        binding.tvCompletedSales.text = count.toString()
                    }
                }
                launch {
                    viewModel.lowStockItems.collect { items ->
                        binding.tvLowStockCount.text = items.size.toString()
                        binding.tvLowStockItems.text = if (items.isEmpty()) {
                            getString(R.string.no_low_stock_items)
                        } else {
                            items.take(3).joinToString(", ") { it.name } +
                                if (items.size > 3) getString(R.string.low_stock_more_format, items.size - 3) else ""
                        }
                    }
                }
                launch {
                    viewModel.goals.collect { state ->
                        when (state) {
                            is UiState.Success -> renderGoals(state.data)
                            is UiState.Error -> showGoalsMessage(state.message)
                            UiState.Loading -> showGoalsMessage(getString(R.string.sales_goals_loading))
                            UiState.Idle -> clearGoals()
                        }
                    }
                }
            }
        }

        binding.btnQuickSale.setOnClickListener {
            navigateToSales()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPeriodBoundaries()
    }

    private fun showGoalEditor() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sales_goals, null)
        val dailyInput = dialogView.findViewById<EditText>(R.id.etDailyGoal)
        val dailyLayout = dialogView.findViewById<TextInputLayout>(R.id.dailyGoalLayout)
        (viewModel.goals.value as? UiState.Success)?.data?.let { state ->
            dailyInput.setText(SalesGoalInputParser.displayAmount(state.daily.targetMinor))
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sales_goals_title)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_save_goals, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dailyLayout.error = null
                val daily = parseGoal(dailyInput, dailyLayout)
                if (dailyLayout.error != null) {
                    return@setOnClickListener
                }
                val saveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                saveButton.isEnabled = false
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        viewModel.saveGoals(daily)
                        Toast.makeText(requireContext(), R.string.sales_goals_saved, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), R.string.sales_goals_save_failed, Toast.LENGTH_LONG).show()
                        if (dialog.isShowing) saveButton.isEnabled = true
                    }
                }
            }
        }
        dialog.show()
    }

    private fun parseGoal(input: EditText, layout: TextInputLayout): Long? = try {
        SalesGoalInputParser.parseMinorUnits(input.text?.toString().orEmpty())
    } catch (_: Exception) {
        layout.error = getString(R.string.sales_goal_invalid_amount)
        null
    }

    private fun renderGoals(state: DashboardGoalsState) {
        binding.tvGoalsEmpty.setText(R.string.sales_goals_empty)
        binding.tvGoalsEmpty.isVisible = !state.hasConfiguredTargets
        binding.layoutGoalProgress.isVisible = true
        binding.tvDailyGoal.text = goalLabel(getString(R.string.goal_daily_label), state.daily)
        binding.tvWeeklyGoal.text = goalLabel(getString(R.string.goal_weekly_label), state.weekly)
        binding.tvMonthlyGoal.text = goalLabel(getString(R.string.goal_monthly_label), state.monthly)
        binding.progressDailyGoal.progress = (state.daily.percent ?: 0).coerceIn(0, 100)
        binding.progressWeeklyGoal.progress = (state.weekly.percent ?: 0).coerceIn(0, 100)
        binding.progressMonthlyGoal.progress = (state.monthly.percent ?: 0).coerceIn(0, 100)
        val history = buildList {
            if (state.dailyHistory.isNotEmpty()) add("Days: ${historyLine(state.dailyHistory)}")
            if (state.weeklyHistory.isNotEmpty()) add("Weeks: ${historyLine(state.weeklyHistory)}")
            if (state.monthlyHistory.isNotEmpty()) add("Months: ${historyLine(state.monthlyHistory)}")
        }
        binding.tvSalesHistory.text = if (history.isEmpty()) {
            getString(R.string.sales_goal_history_empty)
        } else {
            "${getString(R.string.sales_goal_history_title)}\n${history.joinToString("\n")}"
        }
    }

    private fun showGoalsMessage(message: String) {
        binding.tvGoalsEmpty.isVisible = true
        binding.tvGoalsEmpty.text = message
        binding.layoutGoalProgress.isVisible = false
    }

    private fun historyLine(history: List<com.example.microbusinesssystem.util.HistoricalRevenue>): String =
        history.joinToString(" · ") { "${it.label}: ${formatMoney(it.amountMinor)}" }

    private fun goalLabel(label: String, progress: com.example.microbusinesssystem.util.GoalProgress): String =
        if (progress.targetMinor == null) {
            getString(R.string.goal_no_target_format, label, formatMoney(progress.actualMinor))
        } else {
            getString(
                R.string.goal_progress_format,
                label,
                formatMoney(progress.actualMinor),
                formatMoney(progress.targetMinor),
                progress.percent ?: 0,
                formatMoney(progress.remainingMinor ?: 0L)
            ) + if ((progress.remainingMinor ?: 1L) == 0L) getString(R.string.goal_achieved_suffix) else ""
        }

    private fun navigateToSales() {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.nav_sales) return
        navController.navigate(R.id.nav_sales, null, navOptions { launchSingleTop = true })
    }

    private fun clearAccountData() {
        _binding?.let { b ->
            b.tvCompanyName.text = ""
            b.tvGreeting.text = ""
            b.tvCompletedSales.text = "—"
            b.tvLowStockCount.text = "—"
            b.tvLowStockItems.text = ""
            b.tvTodayRevenue.text = "—"
            clearGoals()
        }
    }

    private fun clearGoals() {
        _binding?.let { b ->
            b.tvGoalsEmpty.isVisible = true
            b.tvGoalsEmpty.setText(R.string.sales_goals_empty)
            b.layoutGoalProgress.isVisible = false
            b.tvSalesHistory.text = ""
        }
    }

    private fun formatMoney(amountMinor: Long): String =
        "Ksh ${BigDecimal.valueOf(amountMinor, 2).setScale(2).toPlainString()}"

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
