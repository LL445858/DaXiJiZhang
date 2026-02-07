package com.example.daxijizhang.ui.settings

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.window.OnBackInvokedDispatcher
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.daxijizhang.R
import com.example.daxijizhang.databinding.ActivityMoreSettingsBinding
import com.example.daxijizhang.ui.base.BaseActivity
import com.example.daxijizhang.util.ViewUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MoreSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityMoreSettingsBinding
    
    // 项目词典数据
    private val projectList = mutableListOf<String>()
    private lateinit var projectAdapter: ProjectDictionaryAdapter
    private var projectDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMoreSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        setupClickListeners()
        setupBackPressHandler()
        setupForwardTransition()
    }

    private fun setupBackPressHandler() {
        // Android 13+ 使用新的预测性返回手势API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                finishWithAnimation()
            }
        } else {
            // 低版本使用OnBackPressedDispatcher
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithAnimation()
                }
            })
        }
    }

    /**
     * 设置进入动画（前进动画）
     */
    private fun setupForwardTransition() {
        // Android 13+ 使用新的过渡API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
        }
    }

    /**
     * 设置返回动画（后退动画）并结束Activity
     */
    private fun finishWithAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用新的API
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        } else {
            // 低版本仍然需要使用旧API
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        finish()
    }

    private fun initViews() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        // 项目填写 - 显示编辑弹窗
        binding.itemProjectDictionary.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                showProjectDictionaryDialog()
            }
        }
    }
    
    /**
     * 显示项目填写弹窗
     */
    private fun showProjectDictionaryDialog() {
        // 加载示例数据（实际应从数据库或SharedPreferences加载）
        loadProjectData()
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_project_dictionary, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recycler_projects)
        val btnAdd = dialogView.findViewById<ImageButton>(R.id.btn_add_project)
        val tvEmpty = dialogView.findViewById<TextView>(R.id.tv_empty_hint)
        
        // 设置RecyclerView
        projectAdapter = ProjectDictionaryAdapter(
            projectList,
            onDeleteClick = { position ->
                showDeleteConfirmDialog(position)
            },
            onSaveClick = { position, newName ->
                // TODO: 保存功能待实现
                Toast.makeText(this, "保存: $newName", Toast.LENGTH_SHORT).show()
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = projectAdapter
        
        // 更新空状态显示
        updateEmptyState(tvEmpty)
        
        // 添加按钮点击事件
        btnAdd.setOnClickListener {
            addNewProjectItem(recyclerView, tvEmpty)
        }
        
        projectDialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()
            .apply {
                window?.setBackgroundDrawableResource(android.R.color.transparent)
                show()
            }
    }
    
    /**
     * 加载项目数据
     */
    private fun loadProjectData() {
        projectList.clear()
        // 示例数据，实际应从数据库加载
        projectList.addAll(listOf(
            "地砖",
            "墙面漆",
            "地板",
            "吊顶",
            "橱柜",
            "卫浴"
        ))
    }
    
    /**
     * 更新空状态显示
     */
    private fun updateEmptyState(tvEmpty: TextView) {
        tvEmpty.visibility = if (projectList.isEmpty()) View.VISIBLE else View.GONE
    }
    
    /**
     * 添加新项目条目
     */
    private fun addNewProjectItem(recyclerView: RecyclerView, tvEmpty: TextView) {
        // 在列表顶部插入新条目
        projectList.add(0, "")
        projectAdapter.notifyItemInserted(0)
        recyclerView.scrollToPosition(0)
        updateEmptyState(tvEmpty)
        
        // 自动进入编辑模式
        projectAdapter.setEditingPosition(0)
    }
    
    /**
     * 显示删除确认对话框
     */
    private fun showDeleteConfirmDialog(position: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除确认")
            .setMessage("确定要删除该项目吗？")
            .setPositiveButton("删除") { _, _ ->
                projectList.removeAt(position)
                projectAdapter.notifyItemRemoved(position)
                projectAdapter.notifyItemRangeChanged(position, projectList.size - position)
                // 更新空状态
                projectDialog?.findViewById<TextView>(R.id.tv_empty_hint)?.let {
                    updateEmptyState(it)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 项目词典适配器
     */
    inner class ProjectDictionaryAdapter(
        private val projects: MutableList<String>,
        private val onDeleteClick: (Int) -> Unit,
        private val onSaveClick: (Int, String) -> Unit
    ) : RecyclerView.Adapter<ProjectDictionaryAdapter.ViewHolder>() {
        
        private var editingPosition = -1
        
        fun setEditingPosition(position: Int) {
            val previousEditing = editingPosition
            editingPosition = position
            if (previousEditing >= 0 && previousEditing < projects.size) {
                notifyItemChanged(previousEditing)
            }
            if (position >= 0 && position < projects.size) {
                notifyItemChanged(position)
            }
        }
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tv_project_name)
            val etName: EditText = itemView.findViewById(R.id.et_project_name)
            val btnSave: ImageButton = itemView.findViewById(R.id.btn_save)
            val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_project_dictionary, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val projectName = projects[position]
            val isEditing = position == editingPosition
            
            if (isEditing) {
                // 编辑模式
                holder.tvName.visibility = View.GONE
                holder.etName.visibility = View.VISIBLE
                holder.btnSave.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.GONE
                
                holder.etName.setText(projectName)
                holder.etName.requestFocus()
                
                // 显示软键盘
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(holder.etName, InputMethodManager.SHOW_IMPLICIT)
                
                // 保存按钮点击事件
                holder.btnSave.setOnClickListener {
                    val newName = holder.etName.text.toString().trim()
                    if (newName.isNotEmpty()) {
                        projects[position] = newName
                        onSaveClick(position, newName)
                        editingPosition = -1
                        notifyItemChanged(position)
                        // 隐藏软键盘
                        imm.hideSoftInputFromWindow(holder.etName.windowToken, 0)
                    } else {
                        Toast.makeText(this@MoreSettingsActivity, "项目名称不能为空", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // 显示模式
                holder.tvName.visibility = View.VISIBLE
                holder.etName.visibility = View.GONE
                holder.btnSave.visibility = View.GONE
                holder.btnDelete.visibility = View.VISIBLE
                
                holder.tvName.text = projectName
                
                // 点击条目进入编辑模式
                holder.itemView.setOnClickListener {
                    setEditingPosition(position)
                }
                
                // 删除按钮点击事件
                holder.btnDelete.setOnClickListener {
                    onDeleteClick(position)
                }
            }
        }
        
        override fun getItemCount(): Int = projects.size
    }
}
