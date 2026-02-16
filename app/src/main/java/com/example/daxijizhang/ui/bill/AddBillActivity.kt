package com.example.daxijizhang.ui.bill

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.daxijizhang.R
import com.example.daxijizhang.data.database.AppDatabase
import com.example.daxijizhang.data.local.BillDraft
import com.example.daxijizhang.data.local.DraftManager
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.data.model.BillItem
import com.example.daxijizhang.data.model.PaymentRecord
import com.example.daxijizhang.data.repository.BillRepository
import com.example.daxijizhang.data.repository.ProjectDictionaryRepository
import com.example.daxijizhang.databinding.ActivityAddBillBinding
import com.example.daxijizhang.databinding.DialogAddPaymentBinding
import com.example.daxijizhang.databinding.DialogAddProjectBinding
import com.example.daxijizhang.ui.base.BaseActivity
import com.example.daxijizhang.ui.view.ModernDatePickerDialog
import com.example.daxijizhang.ui.view.ProjectAutoCompleteView
import com.example.daxijizhang.util.ThemeManager
import com.example.daxijizhang.util.ViewUtil.setOnOptimizedClickListener
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale

class AddBillActivity : BaseActivity() {

    private lateinit var binding: ActivityAddBillBinding
    private lateinit var billItemAdapter: BillItemAdapter
    private lateinit var paymentRecordAdapter: PaymentRecordAdapter
    private lateinit var draftManager: DraftManager
    private lateinit var projectDictionaryRepository: ProjectDictionaryRepository

    private val items = mutableListOf<BillItem>()
    private val paymentRecords = mutableListOf<PaymentRecord>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var billStartDate: Date? = null
    private var billEndDate: Date? = null

    // 抹零金额（不创建虚拟支付记录）
    private var waivedAmount: Double = 0.0

    private lateinit var viewModel: BillViewModel

    // 对话框标记，防止重复弹出
    private var isAddProjectDialogShowing = false
    private var isAddPaymentDialogShowing = false

    // 智能提示相关
    private var projectAutoCompleteView: ProjectAutoCompleteView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddBillBinding.inflate(layoutInflater)
        setContentView(binding.root)

        draftManager = DraftManager(this)

        val database = AppDatabase.getDatabase(this)
        val repository = BillRepository(database.billDao(), database.billItemDao(), database.paymentRecordDao())
        viewModel = BillViewModel(repository)
        projectDictionaryRepository = ProjectDictionaryRepository(database.projectDictionaryDao())

        // 初始化默认项目词典数据
        lifecycleScope.launch {
            projectDictionaryRepository.initializeDefaultProjects()
        }

        setupStatusBarPadding()
        setupToolbar()
        setupRecyclerViews()
        setupListeners()
        updatePaymentStatus()
        applyThemeColor()

        // 检查是否有草稿
        checkAndLoadDraft()
    }

    private fun setupStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            
            binding.statusBarPlaceholder.updateLayoutParams {
                height = statusBarInsets.top
            }
            
            binding.bottomButtons.setPadding(
                binding.bottomButtons.paddingLeft,
                binding.bottomButtons.paddingTop,
                binding.bottomButtons.paddingRight,
                navigationBarInsets.bottom + binding.bottomButtons.paddingTop
            )
            
            windowInsets
        }
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

        // 设置底部"保存账单"按钮颜色
        binding.btnSaveBill.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)

        // 设置输入框边框和标题颜色
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

    private fun checkAndLoadDraft() {
        if (draftManager.hasDraft()) {
            val themeColor = ThemeManager.getThemeColor()
            val dialog = AlertDialog.Builder(this)
                .setTitle("加载草稿")
                .setMessage("检测到未保存的草稿，是否加载？")
                .setPositiveButton("加载") { _, _ ->
                    loadDraft()
                }
                .setNegativeButton("新建") { _, _ ->
                    draftManager.clearDraft()
                }
                .setCancelable(false)
                .create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
            dialog.show()
            // 设置按钮颜色为主题色
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(themeColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(themeColor)
        }
    }

    private fun loadDraft() {
        val draft = draftManager.loadDraft()

        billStartDate = draft.startDate?.let { Date(it) }
        billEndDate = draft.endDate?.let { Date(it) }

        binding.etStartDate.setText(billStartDate?.let { dateFormat.format(it) } ?: "")
        binding.etEndDate.setText(billEndDate?.let { dateFormat.format(it) } ?: "")
        binding.etCommunity.setText(draft.communityName)
        binding.etPhase.setText(draft.phase)
        binding.etBuilding.setText(draft.buildingNumber)
        binding.etRoom.setText(draft.roomNumber)
        binding.etRemark.setText(draft.remark)

        items.clear()
        items.addAll(draft.items)
        billItemAdapter.submitList(items.toList())

        paymentRecords.clear()
        paymentRecords.addAll(draft.paymentRecords)
        sortPaymentRecords()
        paymentRecordAdapter.submitList(paymentRecords.toList())

        waivedAmount = draft.waivedAmount

        updateTotalAmount()
        updatePaymentStatus()
    }

    private fun saveDraft() {
        val draft = BillDraft(
            startDate = billStartDate?.time,
            endDate = billEndDate?.time,
            communityName = binding.etCommunity.text.toString(),
            phase = binding.etPhase.text.toString(),
            buildingNumber = binding.etBuilding.text.toString(),
            roomNumber = binding.etRoom.text.toString(),
            remark = binding.etRemark.text.toString(),
            items = items.toList(),
            paymentRecords = paymentRecords.toList(),
            waivedAmount = waivedAmount
        )
        draftManager.saveDraft(draft)
    }

    private fun hasContent(): Boolean {
        return !binding.etCommunity.text.isNullOrBlank() ||
               !binding.etPhase.text.isNullOrBlank() ||
               !binding.etBuilding.text.isNullOrBlank() ||
               !binding.etRoom.text.isNullOrBlank() ||
               !binding.etRemark.text.isNullOrBlank() ||
               items.isNotEmpty() ||
               paymentRecords.isNotEmpty() ||
               billStartDate != null ||
               billEndDate != null
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
            layoutManager = LinearLayoutManager(this@AddBillActivity)
            adapter = billItemAdapter
            itemAnimator = null
        }
        billItemAdapter.submitList(items.toList())

        // 设置拖动排序 - 使用长按触发拖动
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

                // 交换数据
                Collections.swap(items, fromPosition, toPosition)
                // 通知适配器移动
                billItemAdapter.notifyItemMoved(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不支持滑动删除
            }

            override fun isLongPressDragEnabled(): Boolean {
                return true // 启用长按拖动
            }

            override fun isItemViewSwipeEnabled(): Boolean {
                return false // 禁用滑动
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    // 拖动开始时，改变视觉效果
                    viewHolder?.itemView?.alpha = 0.8f
                    viewHolder?.itemView?.scaleX = 1.05f
                    viewHolder?.itemView?.scaleY = 1.05f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                // 拖动结束时，恢复视觉效果
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
            layoutManager = LinearLayoutManager(this@AddBillActivity)
            adapter = paymentRecordAdapter
            itemAnimator = null
        }
        sortPaymentRecords()
        paymentRecordAdapter.submitList(paymentRecords.toList())
    }

    private fun setupListeners() {
        // 日期选择 - 使用优化点击监听器
        binding.etStartDate.setOnOptimizedClickListener(debounceTime = 200) {
            showDatePicker { date ->
                billStartDate = date
                binding.etStartDate.setText(dateFormat.format(date))
            }
        }

        binding.etEndDate.setOnOptimizedClickListener(debounceTime = 200) {
            showDatePicker { date ->
                billEndDate = date
                binding.etEndDate.setText(dateFormat.format(date))
            }
        }

        // 添加项目 - 使用优化点击监听器
        binding.btnAddProject.setOnOptimizedClickListener(debounceTime = 300) {
            hideKeyboardAndClearFocus()
            showAddProjectDialog()
        }

        // 添加结付记录 - 使用优化点击监听器
        binding.btnAddPayment.setOnOptimizedClickListener(debounceTime = 300) {
            hideKeyboardAndClearFocus()
            showAddPaymentDialog()
        }

        // 账单结清按钮 - 使用优化点击监听器
        binding.btnSettleBill.setOnOptimizedClickListener(debounceTime = 200) {
            handleSettleBill()
        }

        // 清空内容按钮 - 使用优化点击监听器
        binding.btnClearContent.setOnOptimizedClickListener(debounceTime = 300) {
            showClearContentConfirmDialog()
        }

        // 保存账单 - 使用优化点击监听器
        binding.btnSaveBill.setOnOptimizedClickListener(debounceTime = 500) {
            saveBill()
        }
    }

    private fun handleBackPressed() {
        if (hasContent()) {
            val themeColor = ThemeManager.getThemeColor()
            val dialog = AlertDialog.Builder(this)
                .setTitle("保存草稿")
                .setMessage("是否需要将已填写内容保存为草稿？")
                .setPositiveButton("是") { _, _ ->
                    saveDraft()
                    finish()
                }
                .setNegativeButton("否") { _, _ ->
                    draftManager.clearDraft()
                    finish()
                }
                .setCancelable(false)
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBackPressed()
    }

    private fun showDatePicker(onDateSelected: (Date) -> Unit) {
        val calendar = Calendar.getInstance()
        ModernDatePickerDialog.show(
            context = this,
            initialDate = calendar,
            onDateSelected = { selectedCalendar ->
                onDateSelected(selectedCalendar.time)
            }
        )
    }

    private fun showAddProjectDialog() {
        // 防止重复弹出
        if (isAddProjectDialogShowing) return
        isAddProjectDialogShowing = true

        val dialogBinding = DialogAddProjectBinding.inflate(LayoutInflater.from(this))
        val themeColor = ThemeManager.getThemeColor()

        // 设置项目名称输入框的智能提示
        setupProjectAutoComplete(dialogBinding.etProjectName)

        // 实时计算总价
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

        // 设置透明背景以显示圆角
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 设置软输入模式，确保输入法能正常弹出
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

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
                try {
                    val projectName = dialogBinding.etProjectName.text.toString().trim()
                    val unitPrice = dialogBinding.etUnitPrice.text.toString().toDoubleOrNull() ?: 0.0
                    val quantity = if (dialogBinding.etQuantity.text.isNullOrBlank()) {
                        1.0
                    } else {
                        dialogBinding.etQuantity.text.toString().toDoubleOrNull() ?: 1.0
                    }
                    val item = BillItem(
                        billId = 0,
                        projectName = projectName,
                        unitPrice = unitPrice,
                        quantity = quantity
                    )
                    handleAddBillItem(item)

                    dialog.dismiss()
                } catch (e: NumberFormatException) {
                    dialogBinding.tilUnitPrice.error = "请输入有效的数字"
                }
            }
        }

        // 设置对话框显示监听器，在对话框完全显示后聚焦并弹出输入法
        dialog.setOnShowListener {
            dialogBinding.etProjectName.post {
                dialogBinding.etProjectName.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(dialogBinding.etProjectName, InputMethodManager.SHOW_IMPLICIT)
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

        // 日期选择
        dialogBinding.etPaymentDate.setOnClickListener {
            showDatePicker { date ->
                paymentDate = date
                dialogBinding.etPaymentDate.setText(dateFormat.format(date))
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // 设置透明背景以显示圆角
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 对话框关闭时重置标记
        dialog.setOnDismissListener {
            isAddPaymentDialogShowing = false
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSave.setOnClickListener {
            if (validatePaymentInput(dialogBinding, paymentDate)) {
                try {
                    val amount = dialogBinding.etPaymentAmount.text.toString().toDoubleOrNull() ?: 0.0
                    val record = PaymentRecord(
                        billId = 0,
                        paymentDate = paymentDate!!,
                        amount = amount
                    )
                    handleAddPaymentRecord(record)
                    dialog.dismiss()
                } catch (e: NumberFormatException) {
                    dialogBinding.tilPaymentAmount.error = "请输入有效的数字"
                }
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
        val quantity = if (dialogBinding.etQuantity.text.isNullOrBlank()) {
            1.0
        } else {
            dialogBinding.etQuantity.text.toString().toDoubleOrNull() ?: 1.0
        }
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

        // 数量字段可选，为空时默认为1
        binding.tilQuantity.error = null

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

    /**
     * 计算当前支付状态
     * @return Triple(状态字符串, 状态颜色, 是否是已结清状态)
     */
    private fun calculatePaymentStatus(): Triple<String, Int, Boolean> {
        val totalProjectAmount = items.sumOf { it.totalPrice }
        val totalPaid = paymentRecords.sumOf { it.amount }
        val actualPaid = totalPaid + waivedAmount
        val remaining = totalProjectAmount - actualPaid

        return when {
            waivedAmount > 0.01 -> {
                // 抹零状态
                Triple(
                    getString(R.string.payment_status_waived, waivedAmount),
                    ContextCompat.getColor(this, android.R.color.holo_green_dark),
                    true
                )
            }
            remaining > 0.01 -> {
                // 待结清状态
                Triple(
                    getString(R.string.payment_status_pending, remaining),
                    ContextCompat.getColor(this, android.R.color.holo_red_dark),
                    false
                )
            }
            remaining in -0.01..0.01 -> {
                // 刚好结清
                Triple(
                    getString(R.string.payment_status_paid),
                    ContextCompat.getColor(this, android.R.color.holo_green_dark),
                    true
                )
            }
            else -> {
                // 多收状态
                Triple(
                    getString(R.string.payment_status_overpaid, kotlin.math.abs(remaining)),
                    ContextCompat.getColor(this, android.R.color.holo_green_dark),
                    true
                )
            }
        }
    }

    /**
     * 更新支付状态显示
     */
    private fun updatePaymentStatus() {
        val (statusText, color, _) = calculatePaymentStatus()
        binding.tvPaymentStatus.text = statusText
        binding.tvPaymentStatus.setTextColor(color)
        binding.btnSettleBill.isEnabled = true
    }

    /**
     * 获取当前是否是已结清状态（包括抹零和多收）
     */
    private fun isPaidStatus(): Boolean {
        val (_, _, isPaid) = calculatePaymentStatus()
        return isPaid
    }

    /**
     * 获取当前剩余金额（待结清时为正，多收时为负，结清时为0）
     */
    private fun getRemainingAmount(): Double {
        val totalProjectAmount = items.sumOf { it.totalPrice }
        val totalPaid = paymentRecords.sumOf { it.amount }
        val actualPaid = totalPaid + waivedAmount
        return totalProjectAmount - actualPaid
    }

    // ==================== 添加/删除操作处理 ====================

    /**
     * 处理添加装修项目
     * 核心逻辑：状态通过 calculatePaymentStatus() 实时计算，基于以下公式：
     * remaining = totalProjectAmount - totalPaid - waivedAmount
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
                // 例：项目¥1000，已付¥950，抹零¥50。添加¥100项目后，
                // 项目¥1100，已付¥950，抹零需增加到¥150才能保持结清
                waivedAmount += itemAmount
            }
            // 其他状态（已结清、多收、待结清）不需要修改waivedAmount
            // 因为remaining会根据新的totalProjectAmount自动重新计算
        }

        items.add(item)
        billItemAdapter.submitList(items.toList())
        updateTotalAmount()
        updatePaymentStatus()
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
                // 例：项目¥1100，已付¥950，抹零¥150。删除¥100项目后，
                // 若抹零足够：抹零变为¥50；若抹零不足：转为多收
                if (waivedAmount > itemAmount + 0.01) {
                    waivedAmount -= itemAmount
                } else {
                    // 抹零金额不足，清除抹零，转为多收状态
                    // 多收金额 = 项目金额 - 原抹零金额，由calculatePaymentStatus自动计算
                    waivedAmount = 0.0
                }
            }
            // 其他状态（已结清、多收、待结清）不需要修改waivedAmount
            // remaining会根据新的totalProjectAmount自动重新计算
        }

        items.remove(item)
        billItemAdapter.submitList(items.toList())
        updateTotalAmount()
        updatePaymentStatus()
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
                // 例：项目¥1000，已付¥950，抹零¥50。添加¥30记录后，
                // 抹零变为¥20；若添加¥60记录，清除抹零，转为多收
                if (amount < waivedAmount - 0.01) {
                    waivedAmount -= amount
                } else {
                    waivedAmount = 0.0
                }
            }
            // 其他状态（已结清、多收、待结清）不需要修改waivedAmount
            // remaining会根据新的totalPaid自动重新计算
        }

        paymentRecords.add(record)
        sortPaymentRecords()
        paymentRecordAdapter.submitList(paymentRecords.toList())
        updatePaymentStatus()
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
                // 例：项目¥1000，已付¥950，抹零¥50。删除¥30记录后，
                // 已付变为¥920，抹零需增加到¥80才能保持结清
                waivedAmount += amount
            }
            // 其他状态（已结清、多收、待结清）不需要修改waivedAmount
            // remaining会根据新的totalPaid自动重新计算
        }

        paymentRecords.remove(record)
        sortPaymentRecords()
        paymentRecordAdapter.submitList(paymentRecords.toList())
        updatePaymentStatus()
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
            }
            remaining > 0.01 -> {
                // 3.3.1 待结清状态，记录抹零金额
                waivedAmount = remaining
                updatePaymentStatus()
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

    private fun showClearContentConfirmDialog() {
        val themeColor = ThemeManager.getThemeColor()
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.clear_content_confirm_title)
            .setMessage(R.string.clear_content_confirm_message)
            .setPositiveButton(R.string.confirm) { dialog, _ ->
                clearAllContent()
                dialog.dismiss()
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
    }

    private fun clearAllContent() {
        // 清空所有输入字段
        binding.etCommunity.text?.clear()
        binding.etPhase.text?.clear()
        binding.etBuilding.text?.clear()
        binding.etRoom.text?.clear()
        binding.etRemark.text?.clear()
        binding.etStartDate.text?.clear()
        binding.etEndDate.text?.clear()

        // 清空日期
        billStartDate = null
        billEndDate = null

        // 清空装修项目列表
        items.clear()
        billItemAdapter.submitList(items.toList())

        // 清空结付记录列表
        paymentRecords.clear()
        sortPaymentRecords()
        paymentRecordAdapter.submitList(paymentRecords.toList())

        // 重置抹零金额
        waivedAmount = 0.0

        // 更新总金额和支付状态
        updateTotalAmount()
        updatePaymentStatus()

        // 清除草稿
        draftManager.clearDraft()

        Toast.makeText(this, R.string.clear_content_success, Toast.LENGTH_SHORT).show()
    }

    private fun saveBill() {
        hideKeyboard()

        if (!validateBillInput()) {
            return
        }

        if (items.isEmpty()) {
            Toast.makeText(this, R.string.error_add_at_least_one_item, Toast.LENGTH_SHORT).show()
            return
        }

        val startDate = billStartDate
        val endDate = billEndDate
        if (startDate == null || endDate == null) {
            Toast.makeText(this, "日期无效", Toast.LENGTH_SHORT).show()
            return
        }

        val bill = Bill(
            startDate = startDate,
            endDate = endDate,
            communityName = binding.etCommunity.text.toString().trim(),
            phase = binding.etPhase.text.toString().trim().takeIf { it.isNotBlank() },
            buildingNumber = binding.etBuilding.text.toString().trim().takeIf { it.isNotBlank() },
            roomNumber = binding.etRoom.text.toString().trim().takeIf { it.isNotBlank() },
            remark = binding.etRemark.text.toString().trim().takeIf { it.isNotBlank() }
        )

        lifecycleScope.launch {
            try {
                val billId = viewModel.repository.saveBillWithItems(
                    bill = bill,
                    items = items,
                    paymentRecords = paymentRecords,
                    waivedAmount = waivedAmount
                )

                draftManager.clearDraft()

                Toast.makeText(this@AddBillActivity, "账单保存成功", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@AddBillActivity, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hideKeyboardAndClearFocus() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        
        currentFocus?.let { view ->
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
        
        window?.decorView?.rootView?.let { rootView ->
            imm.hideSoftInputFromWindow(rootView.windowToken, 0)
        }
        
        binding.root.requestFocus()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { view ->
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
        window?.decorView?.rootView?.let { rootView ->
            imm.hideSoftInputFromWindow(rootView.windowToken, 0)
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
}
