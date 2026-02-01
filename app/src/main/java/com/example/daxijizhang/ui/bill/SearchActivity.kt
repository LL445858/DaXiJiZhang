package com.example.daxijizhang.ui.bill

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.daxijizhang.R
import com.example.daxijizhang.data.database.AppDatabase
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.databinding.ActivitySearchBinding
import com.example.daxijizhang.ui.base.BaseActivity
import com.example.daxijizhang.util.ViewUtil.setOnOptimizedClickListener
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SearchActivity : BaseActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var billAdapter: BillAdapter
    private lateinit var billDao: com.example.daxijizhang.data.dao.BillDao

    private val searchQuery = MutableStateFlow("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        billDao = AppDatabase.getDatabase(applicationContext).billDao()

        setupRecyclerView()
        setupListeners()
        setupSearch()
        
        // 页面加载完成后自动聚焦搜索框并打开输入法
        binding.etSearch.post {
            binding.etSearch.requestFocus()
            showKeyboard(binding.etSearch)
        }
    }

    private fun setupRecyclerView() {
        billAdapter = BillAdapter { bill ->
            // 跳转到账单详情页
            val intent = Intent(this, BillDetailActivity::class.java).apply {
                putExtra(BillDetailActivity.EXTRA_BILL_ID, bill.id)
            }
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }
        binding.recyclerSearchResults.apply {
            layoutManager = LinearLayoutManager(this@SearchActivity)
            adapter = billAdapter
            // 设置固定高度优化性能
            setHasFixedSize(true)
            // 设置默认动画
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                addDuration = 200
                removeDuration = 200
                moveDuration = 200
                changeDuration = 200
            }
        }
    }

    private fun setupListeners() {
        // 返回按钮 - 使用优化点击监听器
        binding.btnBack.setOnOptimizedClickListener(debounceTime = 200) {
            onBackPressedDispatcher.onBackPressed()
        }

        // 清除按钮点击事件 - 使用优化点击监听器
        binding.btnClear.setOnOptimizedClickListener(debounceTime = 200) {
            binding.etSearch.text?.clear()
            searchQuery.value = ""
            // 清除后重新聚焦并打开输入法
            binding.etSearch.requestFocus()
            showKeyboard(binding.etSearch)
        }
    }

    private fun setupSearch() {
        // 监听搜索输入
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                searchQuery.value = query
                
                // 显示/隐藏清除按钮
                binding.btnClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            }
        })

        // 使用debounce防抖，300ms后执行搜索
        searchQuery
            .debounce(300)
            .onEach { query ->
                performSearch(query)
            }
            .launchIn(lifecycleScope)
    }

    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            runOnUiThread {
                showEmptyState()
            }
            return
        }

        // 获取所有账单并进行模糊匹配
        val allBills = billDao.getAllList()
        val filteredBills = allBills.filter { bill ->
            bill.communityName.contains(query, ignoreCase = true)
        }

        runOnUiThread {
            updateSearchResults(filteredBills)
        }
    }

    private fun updateSearchResults(bills: List<Bill>) {
        when {
            bills.isEmpty() -> {
                showNoResultState()
            }
            else -> {
                showResultsState()
                billAdapter.submitList(bills)
            }
        }
    }

    private fun showEmptyState() {
        binding.emptyView.visibility = View.VISIBLE
        binding.noResultView.visibility = View.GONE
        binding.recyclerSearchResults.visibility = View.GONE
    }

    private fun showNoResultState() {
        binding.emptyView.visibility = View.GONE
        binding.noResultView.visibility = View.VISIBLE
        binding.recyclerSearchResults.visibility = View.GONE
    }

    private fun showResultsState() {
        binding.emptyView.visibility = View.GONE
        binding.noResultView.visibility = View.GONE
        binding.recyclerSearchResults.visibility = View.VISIBLE
    }
    
    private fun showKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }
}
