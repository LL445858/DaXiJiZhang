package com.example.daxijizhang.ui.bill

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import com.example.daxijizhang.databinding.ActivityAddBillBinding
import com.example.daxijizhang.databinding.DialogAddPaymentBinding
import com.example.daxijizhang.databinding.DialogAddProjectBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale

class AddBillActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddBillBinding
    private lateinit var billItemAdapter: BillItemAdapter
    private lateinit var paymentRecordAdapter: PaymentRecordAdapter
    private lateinit var draftManager: DraftManager

    private val items = mutableListOf<BillItem>()
    private val paymentRecords = mutableListOf<PaymentRecord>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var billStartDate: Date? = null
    private var billEndDate: Date? = null

    // 抹零金额（不创建虚拟支付记录）
    private var waivedAmount: Double = 0.0

    private lateinit var viewModel: BillViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddBillBinding.inflate(layoutInflater)
        setContentView(binding.root)

        draftManager = DraftManager(this)

        val database = AppDatabase.getDatabase(this)
        val repository = BillRepository(database.billDao(), database.billItemDao(), database.paymentRecordDao())
        viewModel = BillViewModel(repository)

        setupToolbar()
        setupRecyclerViews()
        setupListeners()
        updatePaymentStatus()

        // 检查是否有草稿
        checkAndLoadDraft()
    }

    private fun checkAndLoadDraft() {
        if (draftManager.hasDraft()) {
            AlertDialog.Builder(this)
                .setTitle("加载草稿")
                .setMessage("检测到未保存的草稿，是否加载？")
                .setPositiveButton("加载") { _, _ ->
                    loadDraft()
                }
                .setNegativeButton("新建") { _, _ ->
                    draftManager.clearDraft()
                }
                .setCancelable(false)
                .show()
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
        }
        sortPaymentRecords()
        paymentRecordAdapter.submitList(paymentRecords.toList())
    }

    private fun setupListeners() {
        // 日期选择
        binding.etStartDate.setOnClickListener {
            showDatePicker { date ->
                billStartDate = date
                binding.etStartDate.setText(dateFormat.format(date))
            }
        }

        binding.etEndDate.setOnClickListener {
            showDatePicker { date ->
                billEndDate = date
                binding.etEndDate.setText(dateFormat.format(date))
            }
        }

        // 添加项目
        binding.btnAddProject.setOnClickListener {
            showAddProjectDialog()
        }

        // 添加结付记录
        binding.btnAddPayment.setOnClickListener {
            showAddPaymentDialog()
        }

        // 账单结清按钮
        binding.btnSettleBill.setOnClickListener {
            handleSettleBill()
        }

        // 保存账单
        binding.btnSaveBill.setOnClickListener {
            saveBill()
        }
    }

    private fun handleBackPressed() {
        if (hasContent()) {
            AlertDialog.Builder(this)
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
                .show()
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
        val dialogBinding = DialogAddProjectBinding.inflate(LayoutInflater.from(this))

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

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSave.setOnClickListener {
            if (validateProjectInput(dialogBinding)) {
                val item = BillItem(
                    billId = 0,
                    projectName = dialogBinding.etProjectName.text.toString().trim(),
                    unitPrice = dialogBinding.etUnitPrice.text.toString().toDouble(),
                    quantity = dialogBinding.etQuantity.text.toString().toDouble()
                )
                handleAddBillItem(item)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showAddPaymentDialog() {
        val dialogBinding = DialogAddPaymentBinding.inflate(LayoutInflater.from(this))

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

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSave.setOnClickListener {
            if (validatePaymentInput(dialogBinding, paymentDate)) {
                val amount = dialogBinding.etPaymentAmount.text.toString().toDouble()
                val record = PaymentRecord(
                    billId = 0,
                    paymentDate = paymentDate!!,
                    amount = amount
                )
                handleAddPaymentRecord(record)
                dialog.dismiss()
            }
        }

        dialog.show()
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
     */
    private fun handleAddBillItem(item: BillItem) {
        val itemAmount = item.totalPrice

        // 根据当前状态处理
        when {
            waivedAmount > 0.01 -> {
                // 当前是抹零状态
                if (itemAmount < waivedAmount - 0.01) {
                    // 增加的金额小于抹零金额，减少抹零金额
                    waivedAmount -= itemAmount
                } else {
                    // 增加的金额大于等于抹零金额，转为待结清
                    waivedAmount = 0.0
                }
            }
            isPaidStatus() -> {
                // 当前是已结清或多收状态
                val remaining = getRemainingAmount()
                if (itemAmount <= kotlin.math.abs(remaining) + 0.01) {
                    // 增加的金额小于等于多收金额，保持已结清状态（多收金额减少）
                    // 不需要修改任何值，calculatePaymentStatus会自动计算
                } else {
                    // 增加的金额大于多收金额，转为待结清
                    // 不需要修改任何值，calculatePaymentStatus会自动计算
                }
            }
            else -> {
                // 当前是待结清状态，只需更新显示
                // 不需要修改任何值，calculatePaymentStatus会自动计算
            }
        }

        // 添加项目并更新UI
        items.add(item)
        billItemAdapter.submitList(items.toList())
        updateTotalAmount()
        updatePaymentStatus()
    }

    /**
     * 处理删除装修项目
     */
    private fun handleDeleteBillItem(item: BillItem) {
        val itemAmount = item.totalPrice

        // 根据当前状态处理
        when {
            waivedAmount > 0.01 -> {
                // 当前是抹零状态，增加抹零金额
                waivedAmount += itemAmount
            }
            isPaidStatus() -> {
                // 当前是已结清或多收状态
                val remaining = getRemainingAmount()
                if (remaining >= -0.01) {
                    // 刚好结清或多收不多，删除后转为待结清
                    // 不需要修改任何值，calculatePaymentStatus会自动计算
                } else {
                    // 多收较多，删除后可能还是多收或转为待结清
                    // 不需要修改任何值，calculatePaymentStatus会自动计算
                }
            }
            else -> {
                // 当前是待结清状态，剩余金额增加
                // 不需要修改任何值，calculatePaymentStatus会自动计算
            }
        }

        // 删除项目并更新UI
        items.remove(item)
        billItemAdapter.submitList(items.toList())
        updateTotalAmount()
        updatePaymentStatus()
    }

    /**
     * 处理添加结付记录
     */
    private fun handleAddPaymentRecord(record: PaymentRecord) {
        val amount = record.amount

        // 根据当前状态处理
        when {
            waivedAmount > 0.01 -> {
                // 当前是抹零状态
                if (amount < waivedAmount - 0.01) {
                    // 支付金额小于抹零金额，减少抹零金额
                    waivedAmount -= amount
                } else {
                    // 支付金额大于等于抹零金额，清除抹零
                    waivedAmount = 0.0
                }
            }
            isPaidStatus() -> {
                // 当前是已结清或多收状态，保持多收状态
                // 不需要修改任何值，calculatePaymentStatus会自动计算
            }
            else -> {
                // 当前是待结清状态
                val remaining = getRemainingAmount()
                if (amount >= remaining - 0.01) {
                    // 支付完成，转为已结清或多收
                    // 不需要修改任何值，calculatePaymentStatus会自动计算
                }
            }
        }

        // 添加记录并更新UI
        paymentRecords.add(record)
        sortPaymentRecords()
        paymentRecordAdapter.submitList(paymentRecords.toList())
        updatePaymentStatus()
    }

    /**
     * 处理删除结付记录
     */
    private fun handleDeletePaymentRecord(record: PaymentRecord) {
        val amount = record.amount

        // 根据当前状态处理
        when {
            waivedAmount > 0.01 -> {
                // 当前是抹零状态，增加抹零金额
                waivedAmount += amount
            }
            isPaidStatus() -> {
                // 当前是已结清或多收状态
                val remaining = getRemainingAmount()
                if (remaining >= -0.01) {
                    // 刚好结清或多收不多，删除后转为待结清
                    // 不需要修改任何值，calculatePaymentStatus会自动计算
                } else {
                    // 多收较多，删除后可能还是多收或转为待结清
                    val overpaidAmount = kotlin.math.abs(remaining)
                    if (amount <= overpaidAmount + 0.01) {
                        // 删除金额小于等于多收金额，保持已结清状态
                        // 不需要修改任何值，calculatePaymentStatus会自动计算
                    } else {
                        // 删除金额大于多收金额，转为待结清
                        // 不需要修改任何值，calculatePaymentStatus会自动计算
                    }
                }
            }
            else -> {
                // 当前是待结清状态，剩余金额增加
                // 不需要修改任何值，calculatePaymentStatus会自动计算
            }
        }

        // 删除记录并更新UI
        paymentRecords.remove(record)
        sortPaymentRecords()
        paymentRecordAdapter.submitList(paymentRecords.toList())
        updatePaymentStatus()
    }

    // ==================== 账单结清按钮处理 ====================

    private fun handleSettleBill() {
        val remaining = getRemainingAmount()

        when {
            waivedAmount > 0.01 -> {
                // 当前是抹零状态，恢复为待结清
                val oldWaivedAmount = waivedAmount
                waivedAmount = 0.0
                binding.tvPaymentStatus.text = getString(R.string.payment_status_pending, oldWaivedAmount)
                binding.tvPaymentStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
            remaining > 0.01 -> {
                // 待结清状态，记录抹零金额
                waivedAmount = remaining
                updatePaymentStatus()
            }
            else -> {
                // 已结清或多收状态，显示提示
                AlertDialog.Builder(this)
                    .setTitle(R.string.already_paid_title)
                    .setMessage(R.string.already_paid_message)
                    .setPositiveButton(R.string.confirm) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    // ==================== 其他方法 ====================

    private fun updateTotalAmount() {
        val totalProjectAmount = items.sumOf { it.totalPrice }
        binding.tvTotalAmount.text = "合计：${String.format("¥%.2f", totalProjectAmount)}"
    }

    private fun showDeleteConfirmDialog(onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_message)
            .setPositiveButton(R.string.confirm) { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.back) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun saveBill() {
        // 验证基本信息
        if (!validateBillInput()) {
            return
        }

        // 验证是否有项目
        if (items.isEmpty()) {
            Toast.makeText(this, R.string.error_add_at_least_one_item, Toast.LENGTH_SHORT).show()
            return
        }

        val bill = Bill(
            startDate = billStartDate!!,
            endDate = billEndDate!!,
            communityName = binding.etCommunity.text.toString().trim(),
            phase = binding.etPhase.text.toString().trim().takeIf { it.isNotBlank() },
            buildingNumber = binding.etBuilding.text.toString().trim().takeIf { it.isNotBlank() },
            roomNumber = binding.etRoom.text.toString().trim().takeIf { it.isNotBlank() },
            remark = binding.etRemark.text.toString().trim().takeIf { it.isNotBlank() }
        )

        lifecycleScope.launch {
            try {
                // 使用新的保存方法，同时保存账单、项目、结付记录和抹零金额
                val billId = viewModel.repository.saveBillWithItems(
                    bill = bill,
                    items = items,
                    paymentRecords = paymentRecords,
                    waivedAmount = waivedAmount
                )

                // 保存成功后清除草稿
                draftManager.clearDraft()

                Toast.makeText(this@AddBillActivity, "账单保存成功", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@AddBillActivity, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
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
}
