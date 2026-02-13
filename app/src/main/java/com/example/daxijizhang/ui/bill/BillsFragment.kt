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
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.daxijizhang.R
import com.example.daxijizhang.DaxiApplication
import com.example.daxijizhang.data.database.AppDatabase
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.data.repository.BillRepository
import com.example.daxijizhang.databinding.DialogFilterBinding
import com.example.daxijizhang.databinding.DialogSortBinding
import com.example.daxijizhang.databinding.FragmentBillsBinding
import com.example.daxijizhang.util.ThemeManager
import com.example.daxijizhang.util.ViewUtil.fadeIn
import com.example.daxijizhang.util.ViewUtil.setOnOptimizedClickListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BillsFragment : Fragment() {

    private var _binding: FragmentBillsBinding? = null
    private val binding get() = _binding!!

    private lateinit var billAdapter: BillAdapter

    private val viewModel: BillViewModel by activityViewModels {
        val app = requireActivity().application as DaxiApplication
        val database = AppDatabase.getDatabase(app)
        BillViewModel.Factory(
            BillRepository(
                database.billDao(),
                database.billItemDao(),
                database.paymentRecordDao()
            )
        )
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var filterDialog: AlertDialog? = null

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

        setupStatusBarPadding()
        setupRecyclerView()
        setupObservers()
        setupListeners()
        applyThemeColor()
    }

    private fun applyThemeColor() {
        val themeColor = ThemeManager.getThemeColor()
        binding.fabAddBill.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
    }

    private fun setupStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            binding.statusBarPlaceholder.updateLayoutParams {
                height = insets.top
            }
            windowInsets
        }
    }

    private fun setupRecyclerView() {
        billAdapter = BillAdapter { bill ->
            val intent = Intent(requireContext(), BillDetailActivity::class.java).apply {
                putExtra(BillDetailActivity.EXTRA_BILL_ID, bill.id)
            }
            val options = ActivityOptionsCompat.makeCustomAnimation(
                requireContext(),
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }
        binding.recyclerBills.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = billAdapter
            setHasFixedSize(true)
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                addDuration = 200
                removeDuration = 200
                moveDuration = 200
                changeDuration = 200
            }
        }
    }

    private fun setupObservers() {
        viewModel.bills.observe(viewLifecycleOwner) { bills ->
            bills?.let {
                billAdapter.submitList(it)
                updateEmptyView(it.isEmpty())
                updateBillCount(it.size)
            }
        }

        viewModel.sortType.observe(viewLifecycleOwner) { sortType ->
            updateSortInfo(sortType)
        }

        viewModel.isFilterActive.observe(viewLifecycleOwner) { isActive ->
            updateFilterButtonState(isActive)
        }
        
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    private fun setupListeners() {
        binding.btnSort.setOnOptimizedClickListener(debounceTime = 200) {
            showSortMenu()
        }

        binding.btnFilter.setOnOptimizedClickListener(debounceTime = 200) {
            if (filterDialog?.isShowing == true && viewModel.isFilterActive.value == true) {
                filterDialog?.dismiss()
                filterDialog = null
            } else {
                showFilterDialog()
            }
        }

        binding.btnSearch.setOnOptimizedClickListener(debounceTime = 200) {
            val intent = Intent(requireContext(), SearchActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                requireContext(),
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }

        binding.fabAddBill.setOnOptimizedClickListener(debounceTime = 300) {
            val intent = Intent(requireContext(), AddBillActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                requireContext(),
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }
    }

    private fun showSortMenu() {
        val dialogBinding = DialogSortBinding.inflate(LayoutInflater.from(requireContext()))

        val sortDialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnSortStartDateDesc.setOnClickListener {
            viewModel.setSortType(BillViewModel.SortType.START_DATE_DESC)
            sortDialog.dismiss()
        }
        dialogBinding.btnSortStartDateAsc.setOnClickListener {
            viewModel.setSortType(BillViewModel.SortType.START_DATE_ASC)
            sortDialog.dismiss()
        }
        dialogBinding.btnSortEndDateDesc.setOnClickListener {
            viewModel.setSortType(BillViewModel.SortType.END_DATE_DESC)
            sortDialog.dismiss()
        }
        dialogBinding.btnSortEndDateAsc.setOnClickListener {
            viewModel.setSortType(BillViewModel.SortType.END_DATE_ASC)
            sortDialog.dismiss()
        }
        dialogBinding.btnSortCommunity.setOnClickListener {
            viewModel.setSortType(BillViewModel.SortType.COMMUNITY_ASC)
            sortDialog.dismiss()
        }
        dialogBinding.btnSortAmountDesc.setOnClickListener {
            viewModel.setSortType(BillViewModel.SortType.AMOUNT_DESC)
            sortDialog.dismiss()
        }
        dialogBinding.btnSortAmountAsc.setOnClickListener {
            viewModel.setSortType(BillViewModel.SortType.AMOUNT_ASC)
            sortDialog.dismiss()
        }

        sortDialog.show()

        sortDialog.window?.apply {
            setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.bg_dialog_rounded))
            val displayMetrics = resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.7).toInt()
            setLayout(width, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun showFilterDialog() {
        filterDialog?.dismiss()

        val dialogBinding = DialogFilterBinding.inflate(LayoutInflater.from(requireContext()))

        val paymentStatusOptions = arrayOf("全部", "已结清", "未结清")
        val paymentStatusAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            paymentStatusOptions
        )
        dialogBinding.spinnerPaymentStatus.setAdapter(paymentStatusAdapter)

        currentStartDateFrom?.let { dialogBinding.etStartDateFrom.setText(dateFormat.format(it)) }
        currentStartDateTo?.let { dialogBinding.etStartDateTo.setText(dateFormat.format(it)) }
        currentEndDateFrom?.let { dialogBinding.etEndDateFrom.setText(dateFormat.format(it)) }
        currentEndDateTo?.let { dialogBinding.etEndDateTo.setText(dateFormat.format(it)) }

        val statusText = when (currentPaymentStatus) {
            BillViewModel.PaymentStatusFilter.PAID -> "已结清"
            BillViewModel.PaymentStatusFilter.UNPAID -> "未结清"
            else -> "全部"
        }
        dialogBinding.spinnerPaymentStatus.setText(statusText, false)

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

        filterDialog?.window?.apply {
            setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.bg_dialog_rounded))
        }

        val themeColor = ThemeManager.getThemeColor()
        dialogBinding.btnApplyFilter.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        dialogBinding.btnClearFilter.setTextColor(themeColor)

        dialogBinding.btnApplyFilter.setOnClickListener {
            if (currentStartDateFrom != null && currentStartDateTo != null) {
                if (currentStartDateFrom!!.after(currentStartDateTo)) {
                    Toast.makeText(requireContext(), "开始日期的起始时间不能晚于结束时间", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            if (currentEndDateFrom != null && currentEndDateTo != null) {
                if (currentEndDateFrom!!.after(currentEndDateTo)) {
                    Toast.makeText(requireContext(), "结束日期的起始时间不能晚于结束时间", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            viewModel.setStartDateRangeFilter(currentStartDateFrom, currentStartDateTo)
            viewModel.setEndDateRangeFilter(currentEndDateFrom, currentEndDateTo)

            currentPaymentStatus = when (dialogBinding.spinnerPaymentStatus.text.toString()) {
                "已结清" -> BillViewModel.PaymentStatusFilter.PAID
                "未结清" -> BillViewModel.PaymentStatusFilter.UNPAID
                else -> BillViewModel.PaymentStatusFilter.ALL
            }
            viewModel.setPaymentStatusFilter(currentPaymentStatus)

            filterDialog?.dismiss()
            filterDialog = null
        }

        dialogBinding.btnClearFilter.setOnClickListener {
            currentStartDateFrom = null
            currentStartDateTo = null
            currentEndDateFrom = null
            currentEndDateTo = null
            currentPaymentStatus = BillViewModel.PaymentStatusFilter.ALL

            dialogBinding.etStartDateFrom.text?.clear()
            dialogBinding.etStartDateTo.text?.clear()
            dialogBinding.etEndDateFrom.text?.clear()
            dialogBinding.etEndDateTo.text?.clear()
            dialogBinding.spinnerPaymentStatus.setText("全部", false)

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
            ThemeManager.getThemeColor()
        } else {
            ContextCompat.getColor(requireContext(), R.color.text_primary)
        }
        binding.btnFilter.setColorFilter(color)
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        binding.emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerBills.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.tvBillCount.visibility = View.VISIBLE
    }

    private fun updateBillCount(count: Int) {
        binding.tvBillCount.text = getString(R.string.bill_count_format, count)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        filterDialog?.dismiss()
        filterDialog = null
        _binding = null
    }
}
