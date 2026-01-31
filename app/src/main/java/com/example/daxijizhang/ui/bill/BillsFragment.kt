package com.example.daxijizhang.ui.bill

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.daxijizhang.R
import com.example.daxijizhang.data.database.AppDatabase
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.data.repository.BillRepository
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
    }

    private fun setupListeners() {
        binding.btnSortFilter.setOnClickListener {
            showSortFilterMenu()
        }

        binding.fabAddBill.setOnClickListener {
            startActivity(Intent(requireContext(), AddBillActivity::class.java))
        }
    }

    private fun showSortFilterMenu() {
        val popup = PopupMenu(requireContext(), binding.btnSortFilter)
        popup.menuInflater.inflate(R.menu.menu_sort_filter, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.sort_start_date_asc -> viewModel.setSortType(BillViewModel.SortType.START_DATE_ASC)
                R.id.sort_start_date_desc -> viewModel.setSortType(BillViewModel.SortType.START_DATE_DESC)
                R.id.sort_end_date_asc -> viewModel.setSortType(BillViewModel.SortType.END_DATE_ASC)
                R.id.sort_end_date_desc -> viewModel.setSortType(BillViewModel.SortType.END_DATE_DESC)
                R.id.sort_community -> viewModel.setSortType(BillViewModel.SortType.COMMUNITY_ASC)
                R.id.sort_amount_asc -> viewModel.setSortType(BillViewModel.SortType.AMOUNT_ASC)
                R.id.sort_amount_desc -> viewModel.setSortType(BillViewModel.SortType.AMOUNT_DESC)
                R.id.filter_date_range -> showDateRangePicker()
                R.id.clear_filter -> viewModel.clearDateRangeFilter()
            }
            true
        }

        popup.show()
    }

    private fun showDateRangePicker() {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val startDate = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                }.time

                DatePickerDialog(
                    requireContext(),
                    { _, endYear, endMonth, endDayOfMonth ->
                        val endDate = Calendar.getInstance().apply {
                            set(endYear, endMonth, endDayOfMonth, 23, 59, 59)
                        }.time
                        viewModel.setDateRangeFilter(startDate, endDate)
                    },
                    year, month, dayOfMonth
                ).show()
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

    private fun updateEmptyView(isEmpty: Boolean) {
        binding.emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerBills.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
