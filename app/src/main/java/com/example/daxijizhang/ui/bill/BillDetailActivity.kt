package com.example.daxijizhang.ui.bill

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.daxijizhang.R
import com.example.daxijizhang.data.database.AppDatabase
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.data.model.BillItem
import com.example.daxijizhang.data.model.PaymentRecord
import com.example.daxijizhang.data.repository.BillRepository
import com.example.daxijizhang.data.repository.ProjectDictionaryRepository
import com.example.daxijizhang.databinding.ActivityBillDetailBinding
import com.example.daxijizhang.databinding.DialogAddPaymentBinding
import com.example.daxijizhang.databinding.DialogAddProjectBinding
import com.example.daxijizhang.databinding.DialogExportBillBinding
import com.example.daxijizhang.ui.base.BaseActivity
import com.example.daxijizhang.ui.view.ProjectAutoCompleteView
import com.example.daxijizhang.util.BillExportUtil
import com.example.daxijizhang.util.ThemeManager
import com.example.daxijizhang.util.ViewUtil.setOnOptimizedClickListener
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale

class BillDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityBillDetailBinding
    private lateinit var billItemAdapter: BillItemAdapter
    private lateinit var paymentRecordAdapter: PaymentRecordAdapter
    private lateinit var projectDictionaryRepository: ProjectDictionaryRepository

    private val items = mutableListOf<BillItem>()
    private val paymentRecords = mutableListOf<PaymentRecord>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var billStartDate: Date? = null
    private var billEndDate: Date? = null
    private var waivedAmount: Double = 0.0

    private var billId: Long = 0
    private var originalBill: Bill? = null
    private var hasUnsavedChanges = false

    // 编辑状态标记
    private var isEditingBasicInfo = false

    // 折叠状态标记
    private var isBasicInfoExpanded = true
    private var isProjectsExpanded = true
    private var isPaymentExpanded = true

    // 对话框标记，防止重复弹出
    private var isAddProjectDialogShowing = false
    private var isAddPaymentDialogShowing = false
    private var isExportDialogShowing = false

    // 原始值记录（用于变化检测）
    private var originalValues = mutableMapOf<String, String>()
    private var originalStartDate: Date? = null
    private var originalEndDate: Date? = null
    private var originalItems = mutableListOf<BillItem>()
    private var originalPaymentRecords = mutableListOf<PaymentRecord>()
    private var originalWaivedAmount: Double = 0.0

    private lateinit var viewModel: BillViewModel

    // 智能提示相关
    private var projectAutoCompleteView: ProjectAutoCompleteView? = null

    companion object {
        const val EXTRA_BILL_ID = "extra_bill_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBillDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        billId = intent.getLongExtra(EXTRA_BILL_ID, 0)
        if (billId == 0L) {
            Toast.makeText(this, "账单ID无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val database = AppDatabase.getDatabase(this)
        val repository = BillRepository(database.billDao(), database.billItemDao(), database.paymentRecordDao())
        viewModel = BillViewModel(repository)
        projectDictionaryRepository = ProjectDictionaryRepository(database.projectDictionaryDao())

        // 初始化默认项目词典数据
        lifecycleScope.launch {
            projectDictionaryRepository.initializeDefaultProjects()
        }

        setupToolbar()
        setupRecyclerViews()
        setupListeners()
        setupExpandCollapse()
        loadBillData()
        applyThemeColor()
    }

    /**
     * 应用主题颜色到视图
     */
    private fun applyThemeColor() {
        val themeColor = ThemeManager.getThemeColor()

        // 设置"添加项目"按钮颜色和边框
        binding.btnAddProject.setTextColor(themeColor)
        binding.btnAddProject.strokeColor = android.content.res.ColorStateList.valueOf(themeColor)
        binding.btnAddProject.compoundDrawables.forEach { drawable ->
            drawable?.setTint(themeColor)
        }

        // 设置"添加记录"按钮颜色和边框
        binding.btnAddPayment.setTextColor(themeColor)
        binding.btnAddPayment.strokeColor = android.content.res.ColorStateList.valueOf(themeColor)
        binding.btnAddPayment.compoundDrawables.forEach { drawable ->
            drawable?.setTint(themeColor)
        }

        // 设置"账单结清"按钮颜色和边框
        binding.btnSettleBill.setTextColor(themeColor)
        binding.btnSettleBill.strokeColor = android.content.res.ColorStateList.valueOf(themeColor)
        binding.btnSettleBill.compoundDrawables.forEach { drawable ->
            drawable?.setTint(themeColor)
        }

        // 设置底部按钮颜色
        binding.btnExportBill.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        binding.btnSaveBill.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)

        // 设置输入框边框和标题颜色（编辑状态下）
        applyInputLayoutThemeColor(binding.tilStartDate, themeColor)
        applyInputLayoutThemeColor(binding.tilEndDate, themeColor)
        applyInputLayoutThemeColor(binding.tilCommunity, themeColor)
        applyInputLayoutThemeColor(binding.tilPhase, themeColor)
        applyInputLayoutThemeColor(binding.tilBuilding, themeColor)
        applyInputLayoutThemeColor(binding.tilRoom, themeColor)
        applyInputLayoutThemeColor(binding.tilRemark, themeColor)
    }

    /**
     * 应用主题颜色到TextInputLayout
     */
    private fun applyInputLayoutThemeColor(textInputLayout: com.google.android.material.textfield.TextInputLayout, color: Int) {
        // 设置边框颜色
        textInputLayout.boxStrokeColor = color
        // 设置标题颜色
        textInputLayout.hintTextColor = android.content.res.ColorStateList.valueOf(color)
        // 设置光标颜色
        textInputLayout.editText?.textCursorDrawable = null
        textInputLayout.editText?.highlightColor = color
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            handleBackPressed()
        }
    }

    private fun setupRecyclerViews() {
        // 装修项目列表
        billItemAdapter = BillItemAdapter(
            onItemClick = { /* 点击项目 */ },
            onDeleteClick = { item ->
                showDeleteConfirmDialog(
                    onConfirm = {
                        handleDeleteBillItem(item)
                    }
                )
            }
        )
        binding.recyclerItems.apply {
            layoutManager = LinearLayoutManager(this@BillDetailActivity)
            adapter = billItemAdapter
        }
        billItemAdapter.submitList(items.toList())

        // 设置拖动排序
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                return makeMovementFlags(dragFlags, 0)
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                if (fromPosition < 0 || toPosition < 0 || fromPosition >= items.size || toPosition >= items.size) {
                    return false
                }

                Collections.swap(items, fromPosition, toPosition)
                billItemAdapter.notifyItemMoved(fromPosition, toPosition)
                checkForChanges()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean {
                return true
            }

            override fun isItemViewSwipeEnabled(): Boolean {
                return false
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.8f
                    viewHolder?.itemView?.scaleX = 1.05f
                    viewHolder?.itemView?.scaleY = 1.05f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
                viewHolder.itemView.scaleX = 1.0f
                viewHolder.itemView.scaleY = 1.0f
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerItems)

        // 结付记录列表
        paymentRecordAdapter = PaymentRecordAdapter(
            onItemClick = { /* 点击结付记录 */ },
            onDeleteClick = { record ->
                showDeleteConfirmDialog(
                    onConfirm = {
                        handleDeletePaymentRecord(record)
                    }
                )
            }
        )
        binding.recyclerPaymentRecords.apply {
            layoutManager = LinearLayoutManager(this@BillDetailActivity)
            adapter = paymentRecordAdapter
        }
        sortPaymentRecords()
        paymentRecordAdapter.submitList(paymentRecords.toList())
    }

    private fun setupListeners() {
        // 日期选择 - 使用优化点击监听器
        binding.etStartDate.setOnOptimizedClickListener(debounceTime = 200) {
            if (isFieldEditable(binding.etStartDate)) {
                showDatePicker { date ->
                    billStartDate = date
                    binding.etStartDate.setText(dateFormat.format(date))
                    checkForChanges()
                }
            }
        }

        binding.etEndDate.setOnOptimizedClickListener(debounceTime = 200) {
            if (isFieldEditable(binding.etEndDate)) {
                showDatePicker { date ->
                    billEndDate = date
                    binding.etEndDate.setText(dateFormat.format(date))
                    checkForChanges()
                }
            }
        }

        // 添加项目 - 使用优化点击监听器
        binding.btnAddProject.setOnOptimizedClickListener(debounceTime = 300) {
            showAddProjectDialog()
        }

        // 添加结付记录 - 使用优化点击监听器
        binding.btnAddPayment.setOnOptimizedClickListener(debounceTime = 300) {
            showAddPaymentDialog()
        }

        // 账单结清按钮 - 使用优化点击监听器
        binding.btnSettleBill.setOnOptimizedClickListener(debounceTime = 200) {
            handleSettleBill()
        }

        // 保存账单 - 使用优化点击监听器
        binding.btnSaveBill.setOnOptimizedClickListener(debounceTime = 500) {
            saveBill()
        }

        // 删除账单图标 - 使用优化点击监听器
        binding.ivDeleteBill.setOnOptimizedClickListener(debounceTime = 300) {
            showDeleteBillConfirmDialog()
        }

        // 导出账单按钮 - 使用优化点击监听器
        binding.btnExportBill.setOnOptimizedClickListener(debounceTime = 300) {
            checkUnsavedChangesBeforeExport()
        }

        // 编辑按钮 - 使用优化点击监听器
        binding.ivEditBasicInfo.setOnOptimizedClickListener(debounceTime = 200) {
            toggleBasicInfoEditMode()
        }
    }

    private fun setupExpandCollapse() {
        // 基本信息折叠 - 使用优化点击监听器
        binding.ivExpandBasicInfo.setOnOptimizedClickListener(debounceTime = 200) {
            isBasicInfoExpanded = !isBasicInfoExpanded
            updateBasicInfoExpandState()
        }

        // 装修项目折叠 - 使用优化点击监听器
        binding.ivExpandProjects.setOnOptimizedClickListener(debounceTime = 200) {
            isProjectsExpanded = !isProjectsExpanded
            updateProjectsExpandState()
        }

        // 结付记录折叠 - 使用优化点击监听器
        binding.ivExpandPayment.setOnOptimizedClickListener(debounceTime = 200) {
            isPaymentExpanded = !isPaymentExpanded
            updatePaymentExpandState()
        }
    }

    private fun updateBasicInfoExpandState() {
        if (isBasicInfoExpanded) {
            binding.ivExpandBasicInfo.setImageResource(R.drawable.ic_expand_more)
            binding.layoutBasicInfoContent.visibility = View.VISIBLE
            binding.ivEditBasicInfo.visibility = View.VISIBLE
        } else {
            binding.ivExpandBasicInfo.setImageResource(R.drawable.ic_expand_less)
            binding.layoutBasicInfoContent.visibility = View.GONE
            binding.ivEditBasicInfo.visibility = View.GONE
        }
    }

    private fun updateProjectsExpandState() {
        if (isProjectsExpanded) {
            binding.ivExpandProjects.setImageResource(R.drawable.ic_expand_more)
            binding.layoutProjectsContent.visibility = View.VISIBLE
        } else {
            binding.ivExpandProjects.setImageResource(R.drawable.ic_expand_less)
            binding.layoutProjectsContent.visibility = View.GONE
        }
    }

    private fun updatePaymentExpandState() {
        if (isPaymentExpanded) {
            binding.ivExpandPayment.setImageResource(R.drawable.ic_expand_more)
            binding.layoutPaymentContent.visibility = View.VISIBLE
        } else {
            binding.ivExpandPayment.setImageResource(R.drawable.ic_expand_less)
            binding.layoutPaymentContent.visibility = View.GONE
        }
    }

    private fun toggleBasicInfoEditMode() {
        isEditingBasicInfo = !isEditingBasicInfo

        if (isEditingBasicInfo) {
            // 进入编辑模式
            binding.ivEditBasicInfo.setImageResource(R.drawable.ic_save)
            enableAllFieldsEdit()
        } else {
            // 保存并退出编辑模式
            binding.ivEditBasicInfo.setImageResource(R.drawable.ic_edit)
            disableAllFieldsEdit()
            hideKeyboard()
            // 退出编辑模式时检查是否有实际变化
            checkForChanges()
        }
    }

    private fun enableAllFieldsEdit() {
        // 显示编辑模式布局，隐藏查看模式布局
        binding.layoutEditMode.visibility = View.VISIBLE
        binding.layoutViewMode.visibility = View.GONE
        
        enableFieldEdit(binding.etCommunity)
        enableFieldEdit(binding.etPhase)
        enableFieldEdit(binding.etBuilding)
        enableFieldEdit(binding.etRoom)
        enableFieldEdit(binding.etRemark)
        // 日期字段启用编辑 - 设置为可点击并启用焦点
        enableDateFieldEdit(binding.etStartDate)
        enableDateFieldEdit(binding.etEndDate)
        
        // 从查看模式同步数据到编辑模式
        syncViewToEdit()
    }

    private fun enableDateFieldEdit(field: android.widget.EditText) {
        field.isFocusable = false
        field.isFocusableInTouchMode = false
        field.isClickable = true
        field.isEnabled = true
    }

    private fun disableAllFieldsEdit() {
        // 隐藏编辑模式布局，显示查看模式布局
        binding.layoutEditMode.visibility = View.GONE
        binding.layoutViewMode.visibility = View.VISIBLE
        
        disableFieldEdit(binding.etCommunity)
        disableFieldEdit(binding.etPhase)
        disableFieldEdit(binding.etBuilding)
        disableFieldEdit(binding.etRoom)
        disableFieldEdit(binding.etRemark)
        // 禁用日期字段
        disableDateFieldEdit(binding.etStartDate)
        disableDateFieldEdit(binding.etEndDate)
        
        // 从编辑模式同步数据到查看模式
        syncEditToView()
    }

    private fun disableDateFieldEdit(field: android.widget.EditText) {
        field.isFocusable = false
        field.isFocusableInTouchMode = false
        field.isClickable = false
        field.isEnabled = false
    }

    private fun enableFieldEdit(field: android.widget.EditText) {
        field.isFocusableInTouchMode = true
        field.isFocusable = true
        field.isClickable = true

        // 添加文本变化监听
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                checkForChanges()
            }
        })
    }

    private fun disableFieldEdit(field: android.widget.EditText) {
        field.isFocusableInTouchMode = false
        field.isFocusable = false
        field.isClickable = false
    }
    
    /**
     * 从编辑模式同步数据到查看模式
     */
    private fun syncEditToView() {
        binding.tvStartDate.text = binding.etStartDate.text.toString()
        binding.tvEndDate.text = binding.etEndDate.text.toString()
        
        // 构建小区信息字符串
        val communityInfo = buildCommunityInfo(
            binding.etCommunity.text.toString(),
            binding.etPhase.text.toString(),
            binding.etBuilding.text.toString(),
            binding.etRoom.text.toString()
        )
        binding.tvCommunityInfo.text = communityInfo
        
        val remark = binding.etRemark.text.toString()
        if (remark.isNotEmpty()) {
            binding.layoutRemarkView.visibility = View.VISIBLE
            binding.tvRemark.text = remark
        } else {
            binding.layoutRemarkView.visibility = View.GONE
        }
    }
    
    /**
     * 从查看模式同步数据到编辑模式
     */
    private fun syncViewToEdit() {
        // 编辑模式的数据已经保存在EditText中，不需要额外同步
        // 这个方法用于确保数据一致性
    }
    
    /**
     * 构建小区信息字符串
     * 格式：{小区名} {期数}期 {楼栋号}栋{门牌号}
     * 空字段自动跳过
     */
    private fun buildCommunityInfo(
        communityName: String,
        phase: String?,
        buildingNumber: String?,
        roomNumber: String?
    ): String {
        val sb = StringBuilder()
        
        // 小区名
        sb.append(communityName)
        
        // 期数
        if (!phase.isNullOrBlank()) {
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append(phase).append("期")
        }
        
        // 楼栋号
        if (!buildingNumber.isNullOrBlank()) {
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append(buildingNumber).append("栋")
        }
        
        // 门牌号
        if (!roomNumber.isNullOrBlank()) {
            sb.append(roomNumber)
        }
        
        return sb.toString()
    }

    private fun isFieldEditable(field: android.widget.EditText): Boolean {
        // 日期字段通过isEnabled判断是否可编辑，其他字段通过isFocusableInTouchMode判断
        return field.isEnabled || field.isFocusableInTouchMode
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let {
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    private fun loadBillData() {
        // 显示加载进度条
        binding.progressBar.visibility = View.VISIBLE
        binding.contentContainer.alpha = 0f
        binding.bottomButtons.alpha = 0f

        lifecycleScope.launch {
            try {
                val bill = viewModel.repository.getBillById(billId)
                if (bill == null) {
                    Toast.makeText(this@BillDetailActivity, "账单不存在", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                originalBill = bill

                // 加载基本信息
                billStartDate = bill.startDate
                billEndDate = bill.endDate
                binding.etStartDate.setText(dateFormat.format(bill.startDate))
                binding.etEndDate.setText(dateFormat.format(bill.endDate))
                // 设置日期字段文本颜色为主题颜色，确保禁用状态下也显示正确颜色
                binding.etStartDate.setTextColor(ContextCompat.getColor(this@BillDetailActivity, R.color.text_primary))
                binding.etEndDate.setTextColor(ContextCompat.getColor(this@BillDetailActivity, R.color.text_primary))
                binding.etCommunity.setText(bill.communityName)
                binding.etPhase.setText(bill.phase ?: "")
                binding.etBuilding.setText(bill.buildingNumber ?: "")
                binding.etRoom.setText(bill.roomNumber ?: "")
                binding.etRemark.setText(bill.remark ?: "")
                
                // 初始化查看模式的文本
                binding.tvStartDate.text = dateFormat.format(bill.startDate)
                binding.tvEndDate.text = dateFormat.format(bill.endDate)
                
                // 构建小区信息字符串：{小区名} {期数}期 {楼栋号}栋{门牌号}
                val communityInfo = buildCommunityInfo(
                    bill.communityName,
                    bill.phase,
                    bill.buildingNumber,
                    bill.roomNumber
                )
                binding.tvCommunityInfo.text = communityInfo
                
                val remark = bill.remark ?: ""
                if (remark.isNotEmpty()) {
                    binding.layoutRemarkView.visibility = View.VISIBLE
                    binding.tvRemark.text = remark
                } else {
                    binding.layoutRemarkView.visibility = View.GONE
                }

                // 加载装修项目
                val billItems = viewModel.repository.getBillWithItems(billId)?.items ?: emptyList()
                items.clear()
                items.addAll(billItems)
                billItemAdapter.submitList(items.toList())

                // 加载结付记录
                val records = viewModel.repository.getPaymentRecordsByBillIdList(billId)
                paymentRecords.clear()
                paymentRecords.addAll(records)
                sortPaymentRecords()
                paymentRecordAdapter.submitList(paymentRecords.toList())

                // 从数据库加载抹零金额
                waivedAmount = bill.waivedAmount

                updateTotalAmount()
                updatePaymentStatus()

                // 保存原始值用于变化检测
                saveOriginalValues()
                hasUnsavedChanges = false

                // 初始化折叠状态
                updateBasicInfoExpandState()
                updateProjectsExpandState()
                updatePaymentExpandState()

                // 初始化编辑状态 - 确保所有字段在非编辑模式下锁定
                disableAllFieldsEdit()

                // 隐藏进度条并显示内容（带淡入动画）
                binding.progressBar.visibility = View.GONE
                animateContentShow()

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@BillDetailActivity, "加载账单失败：${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    /**
     * 保存原始值用于变化检测
     */
    private fun saveOriginalValues() {
        originalValues.clear()
        originalValues["community"] = binding.etCommunity.text.toString()
        originalValues["phase"] = binding.etPhase.text.toString()
        originalValues["building"] = binding.etBuilding.text.toString()
        originalValues["room"] = binding.etRoom.text.toString()
        originalValues["remark"] = binding.etRemark.text.toString()
        
        originalStartDate = billStartDate
        originalEndDate = billEndDate
        
        originalItems.clear()
        originalItems.addAll(items.map { it.copy() })
        
        originalPaymentRecords.clear()
        originalPaymentRecords.addAll(paymentRecords.map { it.copy() })
        
        originalWaivedAmount = waivedAmount
    }

    /**
     * 检查是否有实际变化
     */
    private fun checkForChanges() {
        // 检查基本字段
        if (binding.etCommunity.text.toString() != originalValues["community"]) {
            hasUnsavedChanges = true
            return
        }
        if (binding.etPhase.text.toString() != originalValues["phase"]) {
            hasUnsavedChanges = true
            return
        }
        if (binding.etBuilding.text.toString() != originalValues["building"]) {
            hasUnsavedChanges = true
            return
        }
        if (binding.etRoom.text.toString() != originalValues["room"]) {
            hasUnsavedChanges = true
            return
        }
        if (binding.etRemark.text.toString() != originalValues["remark"]) {
            hasUnsavedChanges = true
            return
        }
        
        // 检查日期
        if (billStartDate != originalStartDate) {
            hasUnsavedChanges = true
            return
        }
        if (billEndDate != originalEndDate) {
            hasUnsavedChanges = true
            return
        }
        
        // 检查装修项目
        if (items.size != originalItems.size) {
            hasUnsavedChanges = true
            return
        }
        for (i in items.indices) {
            if (items[i].projectName != originalItems[i].projectName ||
                items[i].unitPrice != originalItems[i].unitPrice ||
                items[i].quantity != originalItems[i].quantity) {
                hasUnsavedChanges = true
                return
            }
        }
        
        // 检查结付记录
        if (paymentRecords.size != originalPaymentRecords.size) {
            hasUnsavedChanges = true
            return
        }
        for (i in paymentRecords.indices) {
            if (paymentRecords[i].amount != originalPaymentRecords[i].amount ||
                paymentRecords[i].paymentDate != originalPaymentRecords[i].paymentDate) {
                hasUnsavedChanges = true
                return
            }
        }
        
        // 检查抹零金额
        if (waivedAmount != originalWaivedAmount) {
            hasUnsavedChanges = true
            return
        }
        
        hasUnsavedChanges = false
    }

    private fun animateContentShow() {
        // 内容区域淡入动画
        binding.contentContainer.animate()
            .alpha(1f)
            .setDuration(300)
            .setStartDelay(50)
            .start()

        // 底部按钮淡入动画
        binding.bottomButtons.animate()
            .alpha(1f)
            .setDuration(300)
            .setStartDelay(150)
            .start()
    }

    private fun showDatePicker(onDateSelected: (Date) -> Unit) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val date = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }.time
                onDateSelected(date)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showAddProjectDialog() {
        // 防止重复弹出
        if (isAddProjectDialogShowing) return
        isAddProjectDialogShowing = true

        val dialogBinding = DialogAddProjectBinding.inflate(LayoutInflater.from(this))
        val themeColor = ThemeManager.getThemeColor()

        // 设置项目名称输入框的智能提示
        setupProjectAutoComplete(dialogBinding.etProjectName)

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                calculateTotal(dialogBinding)
            }
        }

        dialogBinding.etUnitPrice.addTextChangedListener(textWatcher)
        dialogBinding.etQuantity.addTextChangedListener(textWatcher)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // 设置圆角背景
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)

        // 对话框关闭时重置标记
        dialog.setOnDismissListener {
            isAddProjectDialogShowing = false
            // 隐藏智能提示
            projectAutoCompleteView?.hideSuggestions()
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSave.setOnClickListener {
            if (validateProjectInput(dialogBinding)) {
                val projectName = dialogBinding.etProjectName.text.toString().trim()
                val item = BillItem(
                    billId = billId,
                    projectName = projectName,
                    unitPrice = dialogBinding.etUnitPrice.text.toString().toDouble(),
                    quantity = dialogBinding.etQuantity.text.toString().toDouble()
                )
                handleAddBillItem(item)

                // 更新项目词典使用频率
                lifecycleScope.launch {
                    projectDictionaryRepository.incrementUsageCountByName(projectName)
                }

                dialog.dismiss()
            }
        }

        dialog.show()

        // 应用主题色到弹窗元素
        applyThemeColorToProjectDialog(dialogBinding, themeColor)
    }

    /**
     * 应用主题色到添加项目弹窗
     */
    private fun applyThemeColorToProjectDialog(dialogBinding: DialogAddProjectBinding, themeColor: Int) {
        // 取消按钮文字颜色
        dialogBinding.btnCancel.setTextColor(themeColor)
        // 保存按钮背景颜色
        dialogBinding.btnSave.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        // 输入框边框颜色
        applyInputLayoutThemeColor(dialogBinding.tilProjectName, themeColor)
        applyInputLayoutThemeColor(dialogBinding.tilUnitPrice, themeColor)
        applyInputLayoutThemeColor(dialogBinding.tilQuantity, themeColor)
        applyInputLayoutThemeColor(dialogBinding.tilTotalPrice, themeColor)
    }

    /**
     * 设置项目名称输入框的智能提示
     */
    private fun setupProjectAutoComplete(editText: EditText) {
        projectAutoCompleteView = ProjectAutoCompleteView(this)
        projectAutoCompleteView?.attachToEditText(editText, projectDictionaryRepository)
        projectAutoCompleteView?.setOnSuggestionSelectedListener { projectName ->
            // 候选词被选中时的回调
        }
    }

    private fun showAddPaymentDialog() {
        // 防止重复弹出
        if (isAddPaymentDialogShowing) return
        isAddPaymentDialogShowing = true

        val dialogBinding = DialogAddPaymentBinding.inflate(LayoutInflater.from(this))
        val themeColor = ThemeManager.getThemeColor()

        var paymentDate: Date? = null

        dialogBinding.etPaymentDate.setOnClickListener {
            showDatePicker { date ->
                paymentDate = date
                dialogBinding.etPaymentDate.setText(dateFormat.format(date))
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // 设置圆角背景
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)

        // 对话框关闭时重置标记
        dialog.setOnDismissListener {
            isAddPaymentDialogShowing = false
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSave.setOnClickListener {
            if (validatePaymentInput(dialogBinding, paymentDate)) {
                val amount = dialogBinding.etPaymentAmount.text.toString().toDouble()
                val record = PaymentRecord(
                    billId = billId,
                    paymentDate = paymentDate!!,
                    amount = amount
                )
                handleAddPaymentRecord(record)
                dialog.dismiss()
            }
        }

        dialog.show()

        // 应用主题色到弹窗元素
        applyThemeColorToPaymentDialog(dialogBinding, themeColor)
    }

    /**
     * 应用主题色到添加记录弹窗
     */
    private fun applyThemeColorToPaymentDialog(dialogBinding: DialogAddPaymentBinding, themeColor: Int) {
        // 取消按钮文字颜色
        dialogBinding.btnCancel.setTextColor(themeColor)
        // 保存按钮背景颜色
        dialogBinding.btnSave.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        // 输入框边框颜色
        applyInputLayoutThemeColor(dialogBinding.tilPaymentDate, themeColor)
        applyInputLayoutThemeColor(dialogBinding.tilPaymentAmount, themeColor)
    }

    private fun calculateTotal(dialogBinding: DialogAddProjectBinding) {
        val unitPrice = dialogBinding.etUnitPrice.text.toString().toDoubleOrNull() ?: 0.0
        val quantity = dialogBinding.etQuantity.text.toString().toDoubleOrNull() ?: 0.0
        val total = unitPrice * quantity
        dialogBinding.etTotalPrice.setText(String.format("¥%.2f", total))
    }

    private fun validateProjectInput(binding: DialogAddProjectBinding): Boolean {
        var isValid = true

        if (binding.etProjectName.text.isNullOrBlank()) {
            binding.tilProjectName.error = getString(R.string.error_project_name_required)
            isValid = false
        } else {
            binding.tilProjectName.error = null
        }

        if (binding.etUnitPrice.text.isNullOrBlank()) {
            binding.tilUnitPrice.error = getString(R.string.error_unit_price_required)
            isValid = false
        } else {
            binding.tilUnitPrice.error = null
        }

        if (binding.etQuantity.text.isNullOrBlank()) {
            binding.tilQuantity.error = getString(R.string.error_quantity_required)
            isValid = false
        } else {
            binding.tilQuantity.error = null
        }

        return isValid
    }

    private fun validatePaymentInput(binding: DialogAddPaymentBinding, paymentDate: Date?): Boolean {
        var isValid = true

        if (paymentDate == null) {
            binding.tilPaymentDate.error = getString(R.string.error_start_date_required)
            isValid = false
        } else {
            binding.tilPaymentDate.error = null
        }

        if (binding.etPaymentAmount.text.isNullOrBlank()) {
            binding.tilPaymentAmount.error = getString(R.string.error_unit_price_required)
            isValid = false
        } else {
            binding.tilPaymentAmount.error = null
        }

        return isValid
    }

    private fun sortPaymentRecords() {
        paymentRecords.sortByDescending { it.paymentDate }
    }

    // ==================== 状态计算核心方法 ====================

    private fun calculatePaymentStatus(): Triple<String, Int, Boolean> {
        val totalProjectAmount = items.sumOf { it.totalPrice }
        val totalPaid = paymentRecords.sumOf { it.amount }
        val actualPaid = totalPaid + waivedAmount
        val remaining = totalProjectAmount - actualPaid

        return when {
            waivedAmount > 0.01 -> {
                Triple(
                    getString(R.string.payment_status_waived, waivedAmount),
                    ContextCompat.getColor(this, android.R.color.holo_green_dark),
                    true
                )
            }
            remaining > 0.01 -> {
                Triple(
                    getString(R.string.payment_status_pending, remaining),
                    ContextCompat.getColor(this, android.R.color.holo_red_dark),
                    false
                )
            }
            remaining in -0.01..0.01 -> {
                Triple(
                    getString(R.string.payment_status_paid),
                    ContextCompat.getColor(this, android.R.color.holo_green_dark),
                    true
                )
            }
            else -> {
                Triple(
                    getString(R.string.payment_status_overpaid, kotlin.math.abs(remaining)),
                    ContextCompat.getColor(this, android.R.color.holo_green_dark),
                    true
                )
            }
        }
    }

    private fun updatePaymentStatus() {
        val (statusText, color, _) = calculatePaymentStatus()
        binding.tvPaymentStatus.text = statusText
        binding.tvPaymentStatus.setTextColor(color)
        binding.btnSettleBill.isEnabled = true
    }

    private fun isPaidStatus(): Boolean {
        val (_, _, isPaid) = calculatePaymentStatus()
        return isPaid
    }

    private fun getRemainingAmount(): Double {
        val totalProjectAmount = items.sumOf { it.totalPrice }
        val totalPaid = paymentRecords.sumOf { it.amount }
        val actualPaid = totalPaid + waivedAmount
        return totalProjectAmount - actualPaid
    }

    // ==================== 添加/删除操作处理 ====================

    /**
     * 处理添加装修项目
     * 核心逻辑：状态通过 calculatePaymentStatus() 实时计算
     *
     * 3.1.1 待结清+Y：remaining自动增加Y，状态保持待结清，剩余金额=X+Y
     * 3.1.2 抹零+Y：waivedAmount增加Y，保持抹零状态，抹零金额=X+Y
     * 3.1.3 已结清+Y：remaining=Y，转为待结清，剩余金额=Y
     * 3.1.4 多收+Y：remaining=X-Y（原remaining为负），多收减少或转为待结清
     */
    private fun handleAddBillItem(item: BillItem) {
        val itemAmount = item.totalPrice

        when {
            waivedAmount > 0.01 -> {
                // 3.1.2 抹零状态：增加抹零金额以保持结清
                waivedAmount += itemAmount
            }
            // 其他状态不需要修改waivedAmount，remaining自动重新计算
        }

        items.add(item)
        billItemAdapter.submitList(items.toList())
        updateTotalAmount()
        updatePaymentStatus()
        checkForChanges()
    }

    /**
     * 处理删除装修项目
     * 核心逻辑：状态通过 calculatePaymentStatus() 实时计算
     *
     * 3.1.5 多收-删Y：remaining=X+Y（更负），多收金额增加
     * 3.1.6 抹零-删Y：waivedAmount减少Y，若Y>=X则转多收
     * 3.1.7 已结清-删Y：remaining=-Y，转为多收Y
     * 3.1.8 待结清-删Y：remaining=X-Y，若Y>=X则转已结清或多收
     */
    private fun handleDeleteBillItem(item: BillItem) {
        val itemAmount = item.totalPrice

        when {
            waivedAmount > 0.01 -> {
                // 3.1.6 抹零状态：减少抹零金额
                if (waivedAmount > itemAmount + 0.01) {
                    waivedAmount -= itemAmount
                } else {
                    // 抹零金额不足，清除抹零，转为多收状态
                    waivedAmount = 0.0
                }
            }
            // 其他状态不需要修改waivedAmount，remaining自动重新计算
        }

        items.remove(item)
        billItemAdapter.submitList(items.toList())
        updateTotalAmount()
        updatePaymentStatus()
        checkForChanges()
    }

    /**
     * 处理添加结付记录
     * 核心逻辑：状态通过 calculatePaymentStatus() 实时计算
     *
     * 3.2.1 多收+记录Y：remaining=X+Y（更负），多收金额增加
     * 3.2.2 抹零+记录Y：waivedAmount减少Y，若Y>=X则清除抹零
     * 3.2.3 已结清+记录Y：remaining=-Y，转为多收Y
     * 3.2.4 待结清+记录Y：remaining=X-Y，若Y>=X则转已结清或多收
     */
    private fun handleAddPaymentRecord(record: PaymentRecord) {
        val amount = record.amount

        when {
            waivedAmount > 0.01 -> {
                // 3.2.2 抹零状态：减少抹零金额
                if (amount < waivedAmount - 0.01) {
                    waivedAmount -= amount
                } else {
                    waivedAmount = 0.0
                }
            }
            // 其他状态不需要修改waivedAmount，remaining自动重新计算
        }

        paymentRecords.add(record)
        sortPaymentRecords()
        paymentRecordAdapter.submitList(paymentRecords.toList())
        updatePaymentStatus()
        checkForChanges()
    }

    /**
     * 处理删除结付记录
     * 核心逻辑：状态通过 calculatePaymentStatus() 实时计算
     *
     * 3.2.5 待结清-删Y：remaining=X+Y，剩余金额增加
     * 3.2.6 抹零-删Y：waivedAmount增加Y，抹零金额增加
     * 3.2.7 已结清-删Y：remaining=Y，转为待结清
     * 3.2.8 多收-删Y：remaining=X+Y（负值减少），若Y>X则转为待结清
     */
    private fun handleDeletePaymentRecord(record: PaymentRecord) {
        val amount = record.amount

        when {
            waivedAmount > 0.01 -> {
                // 3.2.6 抹零状态：增加抹零金额
                waivedAmount += amount
            }
            // 其他状态不需要修改waivedAmount，remaining自动重新计算
        }

        paymentRecords.remove(record)
        sortPaymentRecords()
        paymentRecordAdapter.submitList(paymentRecords.toList())
        updatePaymentStatus()
        checkForChanges()
    }

    // ==================== 账单结清按钮处理 ====================

    /**
     * 处理账单结清按钮点击
     * 3.3.1 待结清状态下点击，状态变为抹零状态
     * 3.3.2 抹零状态下点击，状态变为待结清
     * 3.3.3 已结清或多收状态下点击，提示"该账单已结清"
     */
    private fun handleSettleBill() {
        val remaining = getRemainingAmount()

        when {
            waivedAmount > 0.01 -> {
                // 3.3.2 当前是抹零状态，恢复为待结清
                val oldWaivedAmount = waivedAmount
                waivedAmount = 0.0
                binding.tvPaymentStatus.text = getString(R.string.payment_status_pending, oldWaivedAmount)
                binding.tvPaymentStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                checkForChanges()
            }
            remaining > 0.01 -> {
                // 3.3.1 待结清状态，记录抹零金额
                waivedAmount = remaining
                updatePaymentStatus()
                checkForChanges()
            }
            else -> {
                // 3.3.3 已结清或多收状态，显示提示
                val themeColor = ThemeManager.getThemeColor()
                val dialog = AlertDialog.Builder(this)
                    .setTitle(R.string.already_paid_title)
                    .setMessage(R.string.already_paid_message)
                    .setPositiveButton(R.string.confirm) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .create()
                dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
                dialog.show()
                // 设置确认按钮颜色为主题色
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(themeColor)
            }
        }
    }

    // ==================== 其他方法 ====================

    private fun updateTotalAmount() {
        val totalProjectAmount = items.sumOf { it.totalPrice }
        binding.tvTotalAmount.text = "合计：${String.format("¥%.2f", totalProjectAmount)}"
    }

    private fun showDeleteConfirmDialog(onConfirm: () -> Unit) {
        val themeColor = ThemeManager.getThemeColor()
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_message)
            .setPositiveButton(R.string.confirm) { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.back) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
        dialog.show()
        // 设置按钮颜色为主题色
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(themeColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(themeColor)
    }

    private fun showDeleteBillConfirmDialog() {
        val themeColor = ThemeManager.getThemeColor()
        // 第一次确认
        val firstDialog = AlertDialog.Builder(this)
            .setTitle(R.string.delete_bill_confirm_title)
            .setMessage(R.string.delete_bill_confirm_message)
            .setPositiveButton(R.string.confirm) { _, _ ->
                // 第二次确认
                val secondDialog = AlertDialog.Builder(this)
                    .setTitle(R.string.delete_bill_final_confirm)
                    .setMessage(R.string.delete_bill_confirm_message)
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        deleteBill()
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .create()
                secondDialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
                secondDialog.show()
                // 设置按钮颜色为主题色
                secondDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(themeColor)
                secondDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(themeColor)
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
        firstDialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
        firstDialog.show()
        // 设置按钮颜色为主题色
        firstDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(themeColor)
        firstDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(themeColor)
    }

    private fun checkUnsavedChangesBeforeExport() {
        if (hasUnsavedChanges) {
            val themeColor = ThemeManager.getThemeColor()
            val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.unsaved_changes_title)
                .setMessage(R.string.unsaved_changes_export_message)
                .setPositiveButton(R.string.save) { _, _ ->
                    // 保存后显示导出对话框
                    saveBillThenExport()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
            dialog.show()
            // 设置按钮颜色为主题色
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(themeColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(themeColor)
        } else {
            showExportBillDialog()
        }
    }

    private fun saveBillThenExport() {
        if (!validateBillInput()) {
            return
        }

        if (items.isEmpty()) {
            Toast.makeText(this, R.string.error_add_at_least_one_item, Toast.LENGTH_SHORT).show()
            return
        }

        val bill = Bill(
            id = billId,
            startDate = billStartDate!!,
            endDate = billEndDate!!,
            communityName = binding.etCommunity.text.toString().trim(),
            phase = binding.etPhase.text.toString().trim().takeIf { it.isNotBlank() },
            buildingNumber = binding.etBuilding.text.toString().trim().takeIf { it.isNotBlank() },
            roomNumber = binding.etRoom.text.toString().trim().takeIf { it.isNotBlank() },
            remark = binding.etRemark.text.toString().trim().takeIf { it.isNotBlank() },
            totalAmount = items.sumOf { it.totalPrice },
            paidAmount = paymentRecords.sumOf { it.amount },
            waivedAmount = waivedAmount
        )

        lifecycleScope.launch {
            try {
                // 更新账单
                viewModel.repository.updateBill(bill)

                // 删除旧的项目和记录
                viewModel.repository.deleteBillItemsByBillId(billId)
                viewModel.repository.deletePaymentRecordsByBillId(billId)

                // 插入新的项目
                val itemsWithBillId = items.map { it.copy(billId = billId) }
                viewModel.repository.insertBillItems(itemsWithBillId)

                // 插入新的结付记录
                if (paymentRecords.isNotEmpty()) {
                    val recordsWithBillId = paymentRecords.map { it.copy(billId = billId) }
                    recordsWithBillId.forEach { viewModel.repository.insertPaymentRecord(it) }
                }

                // 更新原始账单引用和原始值
                originalBill = bill
                saveOriginalValues()
                hasUnsavedChanges = false

                Toast.makeText(this@BillDetailActivity, "账单保存成功", Toast.LENGTH_SHORT).show()

                // 保存成功后显示导出对话框
                showExportBillDialog()
            } catch (e: Exception) {
                Toast.makeText(this@BillDetailActivity, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showExportBillDialog() {
        // 防止重复弹出
        if (isExportDialogShowing) return
        isExportDialogShowing = true

        val dialogBinding = DialogExportBillBinding.inflate(LayoutInflater.from(this))

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // 设置透明背景以显示圆角
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 对话框关闭时重置标记
        dialog.setOnDismissListener {
            isExportDialogShowing = false
        }

        var selectedFormat: BillExportUtil.ExportFormat? = null
        var selectedMethod: BillExportUtil.ExportMethod? = null

        // 格式选择监听
        dialogBinding.rgExportFormat.setOnCheckedChangeListener { _, checkedId ->
            selectedFormat = when (checkedId) {
                R.id.rb_format_image -> BillExportUtil.ExportFormat.IMAGE
                R.id.rb_format_pdf -> BillExportUtil.ExportFormat.PDF
                R.id.rb_format_csv -> BillExportUtil.ExportFormat.CSV
                else -> null
            }
            updateExportButtonState(dialogBinding, selectedFormat, selectedMethod)
        }

        // 方式选择监听
        dialogBinding.rgExportMethod.setOnCheckedChangeListener { _, checkedId ->
            selectedMethod = when (checkedId) {
                R.id.rb_method_save -> BillExportUtil.ExportMethod.SAVE_LOCAL
                R.id.rb_method_share -> BillExportUtil.ExportMethod.SHARE_APP
                else -> null
            }
            updateExportButtonState(dialogBinding, selectedFormat, selectedMethod)
        }

        // 取消按钮
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        // 导出按钮
        dialogBinding.btnExport.setOnClickListener {
            if (selectedFormat != null && selectedMethod != null) {
                dialog.dismiss()
                performExport(selectedFormat!!, selectedMethod!!)
            }
        }

        dialog.show()
    }

    private fun updateExportButtonState(
        dialogBinding: DialogExportBillBinding,
        format: BillExportUtil.ExportFormat?,
        method: BillExportUtil.ExportMethod?
    ) {
        dialogBinding.btnExport.isEnabled = format != null && method != null
    }

    private fun performExport(format: BillExportUtil.ExportFormat, method: BillExportUtil.ExportMethod) {
        val exportData = BillExportUtil.ExportData(
            bill = originalBill ?: return,
            items = items.toList()
        )

        BillExportUtil.exportBill(
            context = this,
            data = exportData,
            format = format,
            method = method,
            onSuccess = {
                Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show()
            },
            onError = { errorMessage ->
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun deleteBill() {
        lifecycleScope.launch {
            try {
                originalBill?.let { bill ->
                    viewModel.repository.deleteBill(bill)
                    Toast.makeText(this@BillDetailActivity, "账单已删除", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@BillDetailActivity, "删除失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveBill() {
        if (!validateBillInput()) {
            return
        }

        if (items.isEmpty()) {
            Toast.makeText(this, R.string.error_add_at_least_one_item, Toast.LENGTH_SHORT).show()
            return
        }

        val bill = Bill(
            id = billId,
            startDate = billStartDate!!,
            endDate = billEndDate!!,
            communityName = binding.etCommunity.text.toString().trim(),
            phase = binding.etPhase.text.toString().trim().takeIf { it.isNotBlank() },
            buildingNumber = binding.etBuilding.text.toString().trim().takeIf { it.isNotBlank() },
            roomNumber = binding.etRoom.text.toString().trim().takeIf { it.isNotBlank() },
            remark = binding.etRemark.text.toString().trim().takeIf { it.isNotBlank() },
            totalAmount = items.sumOf { it.totalPrice },
            paidAmount = paymentRecords.sumOf { it.amount },
            waivedAmount = waivedAmount
        )

        lifecycleScope.launch {
            try {
                // 更新账单
                viewModel.repository.updateBill(bill)

                // 删除旧的项目和记录
                viewModel.repository.deleteBillItemsByBillId(billId)
                viewModel.repository.deletePaymentRecordsByBillId(billId)

                // 插入新的项目
                val itemsWithBillId = items.map { it.copy(billId = billId) }
                viewModel.repository.insertBillItems(itemsWithBillId)

                // 插入新的结付记录
                if (paymentRecords.isNotEmpty()) {
                    val recordsWithBillId = paymentRecords.map { it.copy(billId = billId) }
                    recordsWithBillId.forEach { viewModel.repository.insertPaymentRecord(it) }
                }

                // 更新原始值
                saveOriginalValues()
                hasUnsavedChanges = false

                Toast.makeText(this@BillDetailActivity, "账单保存成功", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@BillDetailActivity, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateBillInput(): Boolean {
        var isValid = true

        if (billStartDate == null) {
            binding.tilStartDate.error = getString(R.string.error_start_date_required)
            isValid = false
        } else {
            binding.tilStartDate.error = null
        }

        if (billEndDate == null) {
            binding.tilEndDate.error = getString(R.string.error_end_date_required)
            isValid = false
        } else {
            binding.tilEndDate.error = null
        }

        if (billEndDate != null && billStartDate != null && billEndDate!!.before(billStartDate)) {
            binding.tilEndDate.error = getString(R.string.error_date_range)
            isValid = false
        }

        if (binding.etCommunity.text.isNullOrBlank()) {
            binding.tilCommunity.error = getString(R.string.error_community_required)
            isValid = false
        } else {
            binding.tilCommunity.error = null
        }

        return isValid
    }

    private fun handleBackPressed() {
        if (hasUnsavedChanges) {
            val themeColor = ThemeManager.getThemeColor()
            val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.unsaved_changes_title)
                .setMessage(R.string.unsaved_changes_message)
                .setPositiveButton(R.string.yes) { _, _ ->
                    saveBillAndFinish()
                }
                .setNegativeButton(R.string.no) { _, _ ->
                    finish()
                }
                .create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
            dialog.show()
            // 设置按钮颜色为主题色
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(themeColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(themeColor)
        } else {
            finish()
        }
    }

    private fun saveBillAndFinish() {
        if (!validateBillInput()) {
            return
        }

        if (items.isEmpty()) {
            Toast.makeText(this, R.string.error_add_at_least_one_item, Toast.LENGTH_SHORT).show()
            return
        }

        val bill = Bill(
            id = billId,
            startDate = billStartDate!!,
            endDate = billEndDate!!,
            communityName = binding.etCommunity.text.toString().trim(),
            phase = binding.etPhase.text.toString().trim().takeIf { it.isNotBlank() },
            buildingNumber = binding.etBuilding.text.toString().trim().takeIf { it.isNotBlank() },
            roomNumber = binding.etRoom.text.toString().trim().takeIf { it.isNotBlank() },
            remark = binding.etRemark.text.toString().trim().takeIf { it.isNotBlank() },
            totalAmount = items.sumOf { it.totalPrice },
            paidAmount = paymentRecords.sumOf { it.amount },
            waivedAmount = waivedAmount
        )

        lifecycleScope.launch {
            try {
                viewModel.repository.updateBill(bill)
                viewModel.repository.deleteBillItemsByBillId(billId)
                viewModel.repository.deletePaymentRecordsByBillId(billId)

                val itemsWithBillId = items.map { it.copy(billId = billId) }
                viewModel.repository.insertBillItems(itemsWithBillId)

                if (paymentRecords.isNotEmpty()) {
                    val recordsWithBillId = paymentRecords.map { it.copy(billId = billId) }
                    recordsWithBillId.forEach { viewModel.repository.insertPaymentRecord(it) }
                }

                saveOriginalValues()
                hasUnsavedChanges = false

                Toast.makeText(this@BillDetailActivity, "账单保存成功", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@BillDetailActivity, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBackPressed()
    }
}