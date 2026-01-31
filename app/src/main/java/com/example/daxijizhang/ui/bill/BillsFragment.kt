package com.example.daxijizhang.ui.bill

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.daxijizhang.R
import com.example.daxijizhang.data.database.AppDatabase
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.data.repository.BillRepository
import com.example.daxijizhang.databinding.DialogFilterBinding
import com.example.daxijizhang.databinding.FragmentBillsBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BillsFragment : Fragment() {

    private var _binding: FragmentBillsBinding? = null
    private val binding get() = _binding!!

    private lateinit var billAdapter: BillAdapter

    private val viewModel: BillViewModel by viewModels {
        BillViewModel.Factory(
            BillRepository(
                AppDatabase.getDatabase(requireActivity().applicationContext).billDao(),
                AppDatabase.getDatabase(requireActivity().applicationContext).billItemDao(),
                AppDatabase.getDatabase(requireActivity().applicationContext).paymentRecordDao()
            )
        )
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // 跟踪筛选对话框状态
    private var filterDialog: AlertDialog? = null

    // 当前筛选条件状态
    private var currentStartDateFrom: Date? = null
    private var currentStartDateTo: Date? = null
    private var currentEndDateFrom: Date? = null
    private var currentEndDateTo: Date? = null
    private var currentPaymentStatus: BillViewModel.PaymentStatusFilter = BillViewModel.PaymentStatusFilter.ALL

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBillsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupListeners()
    }

    private fun setupRecyclerView() {
        billAdapter = BillAdapter { bill ->
            // 跳转到账单详情页
            val intent = Intent(requireContext(), BillDetailActivity::class.java).apply {
                putExtra(BillDetailActivity.EXTRA_BILL_ID, bill.id)
            }
            startActivity(intent)
        }
        binding.recyclerBills.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = billAdapter
        }
    }

    private fun setupObservers() {
        viewModel.bills.observe(viewLifecycleOwner) { bills ->
            bills?.let {
                billAdapter.submitList(it)
                updateEmptyView(it.isEmpty())
            }
        }

        viewModel.sortType.observe(viewLifecycleOwner) { sortType ->
            updateSortInfo(sortType)
        }

        viewModel.isFilterActive.observe(viewLifecycleOwner) { isActive ->
            updateFilterButtonState(isActive)
        }
    }

    private fun setupListeners() {
        // 排序按钮
        binding.btnSort.setOnClickListener {
            showSortMenu()
        }

        // 筛选按钮
        binding.btnFilter.setOnClickListener {
            // 如果筛选面板已打开且有筛选条件，直接关闭
            if (filterDialog?.isShowing == true && viewModel.isFilterActive.value == true) {
                filterDialog?.dismiss()
                filterDialog = null
            } else {
                showFilterDialog()
            }
        }

        // 查找按钮
        binding.btnSearch.setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }

        binding.fabAddBill.setOnClickListener {
            startActivity(Intent(requireContext(), AddBillActivity::class.java))
        }
    }

    private fun showSortMenu() {
        val popup = PopupMenu(requireContext(), binding.btnSort)
        popup.menuInflater.inflate(R.menu.menu_sort, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.sort_start_date_asc -> viewModel.setSortType(BillViewModel.SortType.START_DATE_ASC)
                R.id.sort_start_date_desc -> viewModel.setSortType(BillViewModel.SortType.START_DATE_DESC)
                R.id.sort_end_date_asc -> viewModel.setSortType(BillViewModel.SortType.END_DATE_ASC)
                R.id.sort_end_date_desc -> viewModel.setSortType(BillViewModel.SortType.END_DATE_DESC)
                R.id.sort_community -> viewModel.setSortType(BillViewModel.SortType.COMMUNITY_ASC)
                R.id.sort_amount_asc -> viewModel.setSortType(BillViewModel.SortType.AMOUNT_ASC)
                R.id.sort_amount_desc -> viewModel.setSortType(BillViewModel.SortType.AMOUNT_DESC)
            }
            true
        }

        popup.show()
    }

    private fun showFilterDialog() {
        // 关闭已存在的对话框
        filterDialog?.dismiss()

        val dialogBinding = DialogFilterBinding.inflate(LayoutInflater.from(requireContext()))

        // 结付状态下拉选项
        val paymentStatusOptions = arrayOf("全部", "已结清", "未结清")
        val paymentStatusAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            paymentStatusOptions
        )
        dialogBinding.spinnerPaymentStatus.setAdapter(paymentStatusAdapter)

        // 恢复当前筛选条件到输入框
        currentStartDateFrom?.let { dialogBinding.etStartDateFrom.setText(dateFormat.format(it)) }
        currentStartDateTo?.let { dialogBinding.etStartDateTo.setText(dateFormat.format(it)) }
        currentEndDateFrom?.let { dialogBinding.etEndDateFrom.setText(dateFormat.format(it)) }
        currentEndDateTo?.let { dialogBinding.etEndDateTo.setText(dateFormat.format(it)) }

        // 恢复结付状态选择
        val statusText = when (currentPaymentStatus) {
            BillViewModel.PaymentStatusFilter.PAID -> "已结清"
            BillViewModel.PaymentStatusFilter.UNPAID -> "未结清"
            else -> "全部"
        }
        dialogBinding.spinnerPaymentStatus.setText(statusText, false)

        // 日期选择器
        dialogBinding.etStartDateFrom.setOnClickListener {
            showDatePicker { date ->
                currentStartDateFrom = date
                dialogBinding.etStartDateFrom.setText(dateFormat.format(date))
            }
        }

        dialogBinding.etStartDateTo.setOnClickListener {
            showDatePicker { date ->
                currentStartDateTo = date
                dialogBinding.etStartDateTo.setText(dateFormat.format(date))
            }
        }

        dialogBinding.etEndDateFrom.setOnClickListener {
            showDatePicker { date ->
                currentEndDateFrom = date
                dialogBinding.etEndDateFrom.setText(dateFormat.format(date))
            }
        }

        dialogBinding.etEndDateTo.setOnClickListener {
            showDatePicker { date ->
                currentEndDateTo = date
                dialogBinding.etEndDateTo.setText(dateFormat.format(date))
            }
        }

        filterDialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        // 应用筛选按钮
        dialogBinding.btnApplyFilter.setOnClickListener {
            // 验证并应用开始日期筛选
            if (currentStartDateFrom != null && currentStartDateTo != null) {
                if (currentStartDateFrom!!.after(currentStartDateTo)) {
                    Toast.makeText(requireContext(), "开始日期的起始时间不能晚于结束时间", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            // 验证并应用结束日期筛选
            if (currentEndDateFrom != null && currentEndDateTo != null) {
                if (currentEndDateFrom!!.after(currentEndDateTo)) {
                    Toast.makeText(requireContext(), "结束日期的起始时间不能晚于结束时间", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            // 应用筛选
            viewModel.setStartDateRangeFilter(currentStartDateFrom, currentStartDateTo)
            viewModel.setEndDateRangeFilter(currentEndDateFrom, currentEndDateTo)

            // 应用结付状态筛选
            currentPaymentStatus = when (dialogBinding.spinnerPaymentStatus.text.toString()) {
                "已结清" -> BillViewModel.PaymentStatusFilter.PAID
                "未结清" -> BillViewModel.PaymentStatusFilter.UNPAID
                else -> BillViewModel.PaymentStatusFilter.ALL
            }
            viewModel.setPaymentStatusFilter(currentPaymentStatus)

            filterDialog?.dismiss()
            filterDialog = null
        }

        // 清除筛选按钮
        dialogBinding.btnClearFilter.setOnClickListener {
            // 清除本地状态
            currentStartDateFrom = null
            currentStartDateTo = null
            currentEndDateFrom = null
            currentEndDateTo = null
            currentPaymentStatus = BillViewModel.PaymentStatusFilter.ALL

            // 清除输入框
            dialogBinding.etStartDateFrom.text?.clear()
            dialogBinding.etStartDateTo.text?.clear()
            dialogBinding.etEndDateFrom.text?.clear()
            dialogBinding.etEndDateTo.text?.clear()
            dialogBinding.spinnerPaymentStatus.setText("全部", false)

            // 应用清除
            viewModel.clearAllFilters()
        }

        filterDialog?.setOnDismissListener {
            filterDialog = null
        }

        filterDialog?.show()
    }

    private fun showDatePicker(onDateSelected: (Date) -> Unit) {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val date = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                }.time
                onDateSelected(date)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateSortInfo(sortType: BillViewModel.SortType) {
        val text = when (sortType) {
            BillViewModel.SortType.START_DATE_ASC -> "按开始日期升序"
            BillViewModel.SortType.START_DATE_DESC -> "按开始日期降序"
            BillViewModel.SortType.END_DATE_ASC -> "按结束日期升序"
            BillViewModel.SortType.END_DATE_DESC -> "按结束日期降序"
            BillViewModel.SortType.COMMUNITY_ASC -> "按小区名排序"
            BillViewModel.SortType.AMOUNT_ASC -> "按金额升序"
            BillViewModel.SortType.AMOUNT_DESC -> "按金额降序"
        }
        binding.tvSortInfo.text = text
    }

    private fun updateFilterButtonState(isActive: Boolean) {
        val color = if (isActive) {
            ContextCompat.getColor(requireContext(), R.color.accent)
        } else {
            Color.BLACK
        }
        binding.btnFilter.setColorFilter(color)
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        binding.emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerBills.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        filterDialog?.dismiss()
        filterDialog = null
        _binding = null
    }
}
