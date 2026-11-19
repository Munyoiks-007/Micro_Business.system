package com.example.microbusinesssystem.ui.sales

import android.app.DatePickerDialog
import androidx.appcompat.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import com.example.microbusinesssystem.R
import com.example.microbusinesssystem.data.local.PaymentStatus
import com.example.microbusinesssystem.data.local.SaleEntity
import com.example.microbusinesssystem.data.local.SaleType
import com.example.microbusinesssystem.data.remote.DarajaApiClient
import com.example.microbusinesssystem.data.repository.MpesaInitiationResult
import com.example.microbusinesssystem.data.repository.SalesSyncException
import com.example.microbusinesssystem.data.repository.SessionState
import com.example.microbusinesssystem.databinding.FragmentSalesBinding
import com.example.microbusinesssystem.ui.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Locale
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SalesAdapter(private var sales: List<SaleEntity>) : RecyclerView.Adapter<SalesAdapter.SaleViewHolder>() {
    class SaleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSummary: TextView = view.findViewById(R.id.tvSaleSummary)
        val tvAmount: TextView = view.findViewById(R.id.tvSaleAmount)
        val tvDate: TextView = view.findViewById(R.id.tvSaleDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SaleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sale, parent, false)
        return SaleViewHolder(view)
    }

    override fun onBindViewHolder(holder: SaleViewHolder, position: Int) {
        val sale = sales[position]
        val kind = if (sale.saleType == SaleType.MPESA.name) "M-Pesa" else "Manual"
        val showPaymentStatus = sale.saleType != SaleType.MPESA.name ||
            sale.paymentStatus !in setOf(PaymentStatus.PENDING.name, PaymentStatus.UNCERTAIN.name)
        val status = if (showPaymentStatus) " · ${statusLabel(sale.paymentStatus)}" else ""
        holder.tvSummary.text = "${sale.itemsSummary} · $kind$status"
        holder.tvAmount.text = "Ksh ${formatMinor(sale.totalAmount)}"
        holder.tvDate.text = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.getDefault())
            .withZone(SalesDateRange.BUSINESS_ZONE)
            .format(Instant.ofEpochMilli(sale.timestamp))
    }

    override fun getItemCount() = sales.size

    fun updateData(newSales: List<SaleEntity>) {
        sales = newSales
        notifyDataSetChanged()
    }

    private fun statusLabel(status: String): String = when (status) {
        PaymentStatus.INITIATED.name -> "Initiated"
        PaymentStatus.PENDING.name -> ""
        PaymentStatus.COMPLETED.name -> "Completed"
        PaymentStatus.FAILED.name -> "Failed"
        PaymentStatus.CANCELLED.name -> "Cancelled"
        PaymentStatus.UNCERTAIN.name -> ""
        else -> "Unknown"
    }
}

@AndroidEntryPoint
class SalesFragment : Fragment(R.layout.fragment_sales) {
    private var _binding: FragmentSalesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SalesViewModel by viewModels()
    private lateinit var salesAdapter: SalesAdapter
    private var submissionInProgress = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSalesBinding.bind(view)

        salesAdapter = SalesAdapter(emptyList())
        binding.rvSales.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSales.adapter = salesAdapter
        updateTotalPreview()

        binding.btnResetSandboxAttempts.visibility =
            if (DarajaApiClient.isSandbox) {
                View.VISIBLE
            } else {
                View.GONE
            }

        val totalWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateTotalPreview()
            override fun afterTextChanged(s: Editable?) = Unit
        }
        binding.etQuantity.addTextChangedListener(totalWatcher)
        binding.etUnitPrice.addTextChangedListener(totalWatcher)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.sessionState.collectLatest { state ->
                        if (state !is SessionState.Authenticated) clearAccountState()
                    }
                }
                launch {
                    viewModel.salesState.collectLatest { state ->
                        when (state) {
                            is UiState.Success -> {
                                salesAdapter.updateData(state.data)
                                binding.tvSalesEmpty.visibility = if (state.data.isEmpty()) View.VISIBLE else View.GONE
                                binding.tvSalesEmpty.setText(
                                    if (viewModel.dateRange.value != null) R.string.no_sales_for_selected_period
                                    else R.string.no_sales_recorded
                                )
                            }
                            UiState.Loading -> clearSalesList()
                            UiState.Idle -> clearAccountState()
                            is UiState.Error -> {
                                clearAccountState()
                                showToast(state.message)
                            }
                        }
                    }
                }
                launch {
                    viewModel.dateRange.collectLatest { range ->
                        if (range == null) {
                            binding.btnFilterDate.setText(R.string.filter_date)
                            binding.btnClearDateFilter.visibility = View.GONE
                        } else {
                            val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
                            binding.btnFilterDate.text = "${range.startDate.format(formatter)} – ${range.endDate.format(formatter)}"
                            binding.btnClearDateFilter.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        binding.btnRecordManualSale.setOnClickListener { submitManualSale() }
        binding.btnStkPush.setOnClickListener { submitMpesaRequest() }
        binding.btnResetSandboxAttempts.setOnClickListener { confirmSandboxReset() }
        binding.btnFilterDate.setOnClickListener {
            selectDateRange()
        }
        binding.btnClearDateFilter.setOnClickListener { viewModel.clearDateRange() }
    }

    private fun confirmSandboxReset() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset Sandbox Attempts?")
            .setMessage(
                "This will mark unresolved M-Pesa sandbox attempts as CANCELLED " +
                "in your app so you can test another payment.\n\n" +
                "It does not cancel a real payment with Safaricom. " +
                "Completed transactions will not be changed."
            )
            .setNegativeButton("Keep Attempts", null)
            .setPositiveButton("Reset Attempts") { _, _ ->
                performSandboxReset()
            }
            .show()
    }

    private fun performSandboxReset() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.btnResetSandboxAttempts.isEnabled = false

            try {
                val result = viewModel.resetUnresolvedSandboxMpesaAttempts()

                when {
                    result.resetCount > 0 && result.failedCount == 0 -> {
                        showToast("${result.resetCount} sandbox attempt(s) reset successfully.")
                        binding.tvPaymentStatus.text =
                            "Sandbox attempts reset. Ready for another test."
                        binding.tvPaymentStatus.visibility = View.VISIBLE
                    }

                    result.resetCount > 0 -> {
                        showToast(
                            "${result.resetCount} reset successfully. " +
                            "${result.failedCount} could not be synchronized. Retry the reset."
                        )
                    }

                    result.failedCount > 0 -> {
                        showToast(
                            "Could not synchronize the reset. No local attempts were cleared."
                        )
                    }

                    else -> {
                        showToast("No unresolved sandbox attempts were found.")
                    }
                }

            } catch (cancelled: CancellationException) {
                throw cancelled

            } catch (error: Exception) {
                showToast(
                    error.message ?: "Could not reset sandbox attempts."
                )

            } finally {
                _binding?.btnResetSandboxAttempts?.isEnabled = true
            }
        }
    }

    private fun selectDateRange() {
        val current = viewModel.dateRange.value
        val today = LocalDate.now(SalesDateRange.BUSINESS_ZONE)
        showDatePicker(current?.startDate ?: today) { start ->
            val endInitial = current?.endDate?.takeUnless { it.isBefore(start) } ?: start
            showDatePicker(endInitial) { end ->
                if (end.isBefore(start)) {
                    showToast(R.string.sales_date_range_invalid)
                } else {
                    viewModel.setDateRange(SalesDateRange(start, end))
                }
            }
        }
    }

    private fun showDatePicker(initial: LocalDate, onSelected: (LocalDate) -> Unit) {
        DatePickerDialog(
            requireContext(),
            { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).show()
    }

    private fun submitManualSale() {
        if (submissionInProgress) return
        val fields = parseFields() ?: return
        setSubmissionInProgress(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.recordManualSale(fields.itemName, fields.quantity, fields.unitPriceMinor)
                clearSaleForm()
                showToast(R.string.manual_sale_saved)
            } catch (error: SalesSyncException) {
                clearSaleForm()
                showToast(R.string.manual_sale_local_only)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showToast("The manual sale could not be recorded. Please try again.")
            } finally {
                setSubmissionInProgress(false)
            }
        }
    }

    private fun submitMpesaRequest() {
        if (submissionInProgress) return
        val fields = parseFields() ?: return
        if (fields.totalMinor % 100L != 0L) {
            showToast("M-Pesa amounts must be a whole number of shillings.")
            return
        }
        val phone = com.example.microbusinesssystem.data.remote.KenyanPhoneNumber.normalize(
            binding.etPhone.text?.toString().orEmpty()
        )
        if (phone == null) {
            showToast("Enter a valid Kenyan mobile number, such as 0712345678 or +254712345678.")
            return
        }

        setSubmissionInProgress(true)
        binding.tvPaymentStatus.setText(R.string.mpesa_initiating)
        binding.tvPaymentStatus.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (val result = viewModel.initiateMpesaPayment(
                    fields.itemName,
                    fields.quantity,
                    fields.unitPriceMinor,
                    phone
                )) {
                    MpesaInitiationResult.NotConfigured -> {
                        binding.tvPaymentStatus.setText(R.string.mpesa_config_missing)
                        showToast(R.string.mpesa_config_missing)
                    }
                    MpesaInitiationResult.AnotherAttemptUnresolved -> {
                        binding.tvPaymentStatus.setText(R.string.mpesa_attempt_already_open)
                        showToast(R.string.mpesa_attempt_already_open)
                    }
                    is MpesaInitiationResult.Pending -> {
                        clearSaleForm()
                        binding.tvPaymentStatus.visibility = View.GONE
                    }
                    is MpesaInitiationResult.Uncertain -> {
                        clearSaleForm()
                        binding.tvPaymentStatus.visibility = View.GONE
                    }
                    is MpesaInitiationResult.Failed -> {
                        clearSaleForm()
                        val message = result.failureMessage ?: getString(
                            if (result.requestWasSent) R.string.mpesa_attempt_failed else R.string.mpesa_attempt_not_sent
                        )
                        binding.tvPaymentStatus.text = message
                        showToast(withSyncWarning(message, result.cloudSynced))
                    }
                    is MpesaInitiationResult.Error -> {
                        binding.tvPaymentStatus.text = result.message
                        showToast(result.message)
                    }
                }
            } catch (cancelled: CancellationException) {
                _binding?.tvPaymentStatus?.visibility = View.GONE
                throw cancelled
            } catch (_: Exception) {
                _binding?.tvPaymentStatus?.visibility = View.GONE
                showToast("The M-Pesa request could not be completed.")
            } finally {
                setSubmissionInProgress(false)
            }
        }
    }

    private fun parseFields(): SaleFormFields? = when (val parsed = SaleFormParser.parse(
        itemName = binding.etItemDesc.text?.toString().orEmpty(),
        quantity = binding.etQuantity.text?.toString().orEmpty(),
        unitPrice = binding.etUnitPrice.text?.toString().orEmpty()
    )) {
        is SaleFormResult.Valid -> parsed.fields
        is SaleFormResult.Invalid -> {
            showToast(parsed.message)
            null
        }
    }

    private fun updateTotalPreview() {
        val total = SaleFormParser.previewTotalMinor(
            binding.etQuantity.text?.toString().orEmpty(),
            binding.etUnitPrice.text?.toString().orEmpty()
        )
        binding.tvSaleTotal.text = if (total == null) {
            getString(R.string.sale_total_label) + ": —"
        } else {
            getString(R.string.sale_total_label) + ": Ksh ${formatMinor(total)}"
        }
    }

    private fun setSubmissionInProgress(inProgress: Boolean) {
        submissionInProgress = inProgress
        _binding?.let { b ->
            b.btnRecordManualSale.isEnabled = !inProgress
            b.btnStkPush.isEnabled = !inProgress
        }
    }

    private fun withSyncWarning(message: String, synced: Boolean): String =
        if (synced) message else "$message ${getString(R.string.mpesa_status_sync_warning)}"

    private fun clearAccountState() {
        clearSalesList()
        clearSaleForm()
        _binding?.tvPaymentStatus?.visibility = View.GONE
    }

    private fun clearSalesList() {
        salesAdapter.updateData(emptyList())
        _binding?.tvSalesEmpty?.visibility = View.GONE
    }

    private fun clearSaleForm() {
        _binding?.let { b ->
            b.etItemDesc.text?.clear()
            b.etQuantity.text?.clear()
            b.etUnitPrice.text?.clear()
            b.etPhone.text?.clear()
            updateTotalPreview()
        }
    }

    private fun showToast(message: String) {
        _binding?.let { Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show() }
    }

    private fun showToast(messageRes: Int) = showToast(getString(messageRes))

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private fun formatMinor(amountMinor: Long): String =
    BigDecimal.valueOf(amountMinor, 2).setScale(2).toPlainString()
