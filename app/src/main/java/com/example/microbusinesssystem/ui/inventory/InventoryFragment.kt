package com.example.microbusinesssystem.ui.inventory

import android.os.Bundle
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
import com.example.microbusinesssystem.R
import com.example.microbusinesssystem.data.local.InventoryEntity
import com.example.microbusinesssystem.data.repository.InventoryLocalCleanupException
import com.example.microbusinesssystem.data.repository.InventorySyncException
import com.example.microbusinesssystem.databinding.FragmentInventoryBinding
import com.example.microbusinesssystem.ui.UiState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

class InventoryAdapter(
    private var items: List<InventoryEntity>,
    private val onEdit: (InventoryEntity) -> Unit,
    private val onDelete: (InventoryEntity) -> Unit
) : RecyclerView.Adapter<InventoryAdapter.InventoryViewHolder>() {
    class InventoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvItemName)
        val tvDetails: TextView = view.findViewById(R.id.tvItemDetails)
        val tvQty: TextView = view.findViewById(R.id.tvItemQty)
        val btnEdit: View = view.findViewById(R.id.btnEditItem)
        val btnDelete: View = view.findViewById(R.id.btnDeleteItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_inventory, parent, false)
        return InventoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: InventoryViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        holder.tvDetails.text = "${item.category} · Ksh ${formatPrice(item.price)} · Alert at ${item.lowStockThreshold}"
        holder.tvQty.text = "Qty: ${item.quantity}"
        holder.btnEdit.setOnClickListener { onEdit(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<InventoryEntity>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun formatPrice(priceMinor: Long): String =
        BigDecimal.valueOf(priceMinor, 2).stripTrailingZeros().toPlainString()
}

@AndroidEntryPoint
class InventoryFragment : Fragment(R.layout.fragment_inventory) {
    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InventoryViewModel by viewModels()
    private lateinit var adapter: InventoryAdapter
    private var editingItem: InventoryEntity? = null
    private var operationInProgress = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentInventoryBinding.bind(view)

        adapter = InventoryAdapter(
            items = emptyList(),
            onEdit = ::beginEditing,
            onDelete = ::confirmDelete
        )
        binding.rvInventory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvInventory.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.itemsState.collectLatest { state ->
                    when (state) {
                        is UiState.Success -> adapter.updateData(state.data)
                        UiState.Loading, UiState.Idle -> {
                            adapter.updateData(emptyList())
                            clearForm()
                        }
                        is UiState.Error -> {
                            adapter.updateData(emptyList())
                            clearForm()
                            showToast(state.message)
                        }
                    }
                }
            }
        }

        binding.btnAddItem.setOnClickListener { saveForm() }
        binding.btnCancelEdit.setOnClickListener { clearForm() }
    }

    private fun saveForm() {
        if (operationInProgress) return
        when (val parsed = InventoryFormParser.parse(
            name = binding.etItemName.text?.toString().orEmpty(),
            category = binding.etCategory.text?.toString().orEmpty(),
            price = binding.etPrice.text?.toString().orEmpty(),
            quantity = binding.etQty.text?.toString().orEmpty()
        )) {
            is InventoryFormResult.Invalid -> showToast(parsed.message)
            is InventoryFormResult.Valid -> {
                val fields = parsed.fields
                val selected = editingItem
                val item = selected?.copy(
                    name = fields.name,
                    category = fields.category,
                    price = fields.priceMinor,
                    quantity = fields.quantity,
                    lastUpdated = System.currentTimeMillis()
                ) ?: InventoryEntity(
                    id = UUID.randomUUID().toString(),
                    name = fields.name,
                    category = fields.category,
                    price = fields.priceMinor,
                    quantity = fields.quantity,
                    lowStockThreshold = DEFAULT_LOW_STOCK_THRESHOLD
                )
                persistItem(item, editing = selected != null)
            }
        }
    }

    private fun persistItem(item: InventoryEntity, editing: Boolean) {
        setOperationInProgress(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (editing) viewModel.updateItem(item) else viewModel.saveItem(item)
                clearForm()
                showToast(if (editing) R.string.inventory_updated else R.string.inventory_added)
            } catch (_: InventorySyncException) {
                // The Room mutation already committed; keep the UI truthful about cloud status.
                editingItem = item
                binding.btnAddItem.setText(R.string.action_save_item_changes)
                binding.btnCancelEdit.visibility = View.VISIBLE
                showToast(
                    R.string.inventory_saved_local_sync_failed
                )
            } catch (_: Exception) {
                showToast(R.string.inventory_save_failed)
            } finally {
                setOperationInProgress(false)
            }
        }
    }

    private fun beginEditing(item: InventoryEntity) {
        if (operationInProgress) return
        editingItem = item
        binding.etItemName.setText(item.name)
        binding.etCategory.setText(item.category)
        binding.etPrice.setText(BigDecimal.valueOf(item.price, 2).stripTrailingZeros().toPlainString())
        binding.etQty.setText(item.quantity.toString())
        binding.btnAddItem.setText(R.string.action_save_item_changes)
        binding.btnCancelEdit.visibility = View.VISIBLE
        binding.root.smoothScrollTo(0, 0)
    }

    private fun confirmDelete(item: InventoryEntity) {
        if (operationInProgress) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_inventory_title)
            .setMessage(getString(R.string.delete_inventory_message, item.name))
            .setNegativeButton(R.string.action_keep_item, null)
            .setPositiveButton(R.string.action_confirm_delete) { _, _ -> deleteItem(item) }
            .show()
    }

    private fun deleteItem(item: InventoryEntity) {
        setOperationInProgress(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.deleteItem(item.id)
                if (editingItem?.id == item.id) clearForm()
                showToast(R.string.inventory_deleted)
            } catch (_: InventorySyncException) {
                showToast(R.string.inventory_delete_sync_failed)
            } catch (_: InventoryLocalCleanupException) {
                showToast(R.string.inventory_local_delete_failed)
            } catch (_: Exception) {
                showToast(R.string.inventory_delete_failed)
            } finally {
                setOperationInProgress(false)
            }
        }
    }

    private fun setOperationInProgress(inProgress: Boolean) {
        operationInProgress = inProgress
        _binding?.let { b ->
            b.btnAddItem.isEnabled = !inProgress
            b.btnCancelEdit.isEnabled = !inProgress
            b.rvInventory.isEnabled = !inProgress
        }
    }

    private fun clearForm() {
        editingItem = null
        _binding?.let { b ->
            b.etItemName.text?.clear()
            b.etCategory.text?.clear()
            b.etPrice.text?.clear()
            b.etQty.text?.clear()
            b.btnAddItem.setText(R.string.action_add_stock)
            b.btnCancelEdit.visibility = View.GONE
        }
    }

    private fun showToast(message: String) {
        _binding?.let { Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show() }
    }

    private fun showToast(messageRes: Int) {
        _binding?.let { Toast.makeText(requireContext(), messageRes, Toast.LENGTH_LONG).show() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val DEFAULT_LOW_STOCK_THRESHOLD = 5
    }
}
