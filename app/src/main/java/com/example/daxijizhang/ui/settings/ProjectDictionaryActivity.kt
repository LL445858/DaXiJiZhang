package com.example.daxijizhang.ui.settings

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.daxijizhang.R
import com.example.daxijizhang.data.database.AppDatabase
import com.example.daxijizhang.data.model.ProjectDictionary
import com.example.daxijizhang.data.repository.ProjectDictionaryRepository
import com.example.daxijizhang.util.PinyinUtil
import com.example.daxijizhang.util.ThemeManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * 项目词典管理页面
 */
class ProjectDictionaryActivity : AppCompatActivity() {

    private lateinit var repository: ProjectDictionaryRepository
    private lateinit var adapter: ProjectDictionaryAdapter
    private val projectList = mutableListOf<ProjectDictionary>()

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnAdd: ImageButton
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var statusBarPlaceholder: View
    private lateinit var navigationBarPlaceholder: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project_dictionary)

        // 初始化仓库
        val database = AppDatabase.getDatabase(this)
        repository = ProjectDictionaryRepository(database.projectDictionaryDao())

        initViews()
        setupStatusBarPadding()
        setupRecyclerView()
        setupClickListeners()
        setupBackPressHandler()
        setupForwardTransition()

        // 加载数据
        loadProjects()

        // 初始化默认数据（如果是首次使用）
        initializeDefaultData()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recycler_projects)
        tvEmpty = findViewById(R.id.tv_empty_hint)
        btnAdd = findViewById(R.id.btn_add_project)
        statusBarPlaceholder = findViewById(R.id.status_bar_placeholder)
        navigationBarPlaceholder = findViewById(R.id.navigation_bar_placeholder)

        toolbar.setNavigationOnClickListener {
            finishWithAnimation()
        }

        // 应用主题色到加号按钮
        applyThemeColorToViews()
    }

    private fun setupStatusBarPadding() {
        val rootView = findViewById<androidx.coordinatorlayout.widget.CoordinatorLayout>(R.id.root_view)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            
            statusBarPlaceholder.updateLayoutParams {
                height = statusBarInsets.top
            }
            
            navigationBarPlaceholder.updateLayoutParams {
                height = navigationBarInsets.bottom
            }
            
            // 消费掉导航栏 insets，防止传递给子视图
            WindowInsetsCompat.Builder(windowInsets)
                .setInsets(WindowInsetsCompat.Type.navigationBars(), androidx.core.graphics.Insets.of(0, 0, 0, 0))
                .build()
        }
    }

    /**
     * 应用主题色到视图
     */
    private fun applyThemeColorToViews() {
        val themeColor = ThemeManager.getThemeColor()
        btnAdd.setColorFilter(themeColor)
    }

    private fun setupRecyclerView() {
        adapter = ProjectDictionaryAdapter(
            projects = projectList,
            onEditClick = { position ->
                showEditDialog(position)
            },
            onDeleteClick = { position ->
                showDeleteConfirmDialog(position)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        btnAdd.setOnClickListener {
            showAddDialog()
        }
    }

    private fun setupBackPressHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                finishWithAnimation()
            }
        } else {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithAnimation()
                }
            })
        }
    }

    private fun setupForwardTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
        }
    }

    private fun finishWithAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        finish()
    }

    private fun loadProjects() {
        lifecycleScope.launch {
            try {
                val projects = repository.getAllProjectsSync()
                projectList.clear()
                // 按项目名称字母排序（使用拼音工具支持中英文混合排序）
                val sortedProjects = projects.sortedWith { p1, p2 ->
                    PinyinUtil.compareForSort(p1.name, p2.name)
                }
                projectList.addAll(sortedProjects)
                adapter.notifyDataSetChanged()
                updateEmptyState()
            } catch (e: Exception) {
                Toast.makeText(this@ProjectDictionaryActivity, "加载失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initializeDefaultData() {
        lifecycleScope.launch {
            try {
                repository.initializeDefaultProjects()
                // 重新加载数据并排序
                val projects = repository.getAllProjectsSync()
                projectList.clear()
                // 按项目名称字母排序（使用拼音工具支持中英文混合排序）
                val sortedProjects = projects.sortedWith { p1, p2 ->
                    PinyinUtil.compareForSort(p1.name, p2.name)
                }
                projectList.addAll(sortedProjects)
                adapter.notifyDataSetChanged()
                updateEmptyState()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateEmptyState() {
        tvEmpty.visibility = if (projectList.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (projectList.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showAddDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_project, null)
        val editText = dialogView.findViewById<EditText>(R.id.et_project_name)
        val themeColor = ThemeManager.getThemeColor()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("添加项目")
            .setView(dialogView)
            .setPositiveButton("添加") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    addProject(name)
                } else {
                    Toast.makeText(this, "项目名称不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .create()

        // 设置圆角背景
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
        dialog.show()

        // 应用主题色到按钮
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(themeColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(themeColor)

        // 应用主题色到输入框
        val textInputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_project_name)
        textInputLayout.boxStrokeColor = themeColor
        textInputLayout.hintTextColor = android.content.res.ColorStateList.valueOf(themeColor)

        // 自动弹出软键盘
        editText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun showEditDialog(position: Int) {
        val project = projectList[position]
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_project, null)
        val editText = dialogView.findViewById<EditText>(R.id.et_project_name)
        editText.setText(project.name)
        editText.setSelection(project.name.length)
        val themeColor = ThemeManager.getThemeColor()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("编辑项目")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != project.name) {
                    updateProject(position, newName)
                }
            }
            .setNegativeButton("取消", null)
            .create()

        // 设置圆角背景
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
        dialog.show()

        // 应用主题色到按钮
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(themeColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(themeColor)

        // 应用主题色到输入框
        val textInputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_project_name)
        textInputLayout.boxStrokeColor = themeColor
        textInputLayout.hintTextColor = android.content.res.ColorStateList.valueOf(themeColor)

        // 自动弹出软键盘
        editText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun showDeleteConfirmDialog(position: Int) {
        val project = projectList[position]
        val themeColor = ThemeManager.getThemeColor()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("删除确认")
            .setMessage("确定要删除\"${project.name}\"吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteProject(position)
            }
            .setNegativeButton("取消", null)
            .create()

        // 设置圆角背景
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
        dialog.show()

        // 应用主题色到按钮
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(themeColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(themeColor)
    }

    private fun addProject(name: String) {
        lifecycleScope.launch {
            try {
                // 检查是否已存在
                if (repository.isProjectNameExists(name)) {
                    Toast.makeText(this@ProjectDictionaryActivity, "该项目已存在", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val project = ProjectDictionary(name = name)
                val id = repository.insertProject(project)

                if (id > 0) {
                    val newProject = project.copy(id = id)
                    // 添加到列表并按名称排序
                    projectList.add(newProject)
                    // 重新排序整个列表
                    val sortedList = projectList.sortedWith { p1, p2 ->
                        PinyinUtil.compareForSort(p1.name, p2.name)
                    }
                    projectList.clear()
                    projectList.addAll(sortedList)
                    adapter.notifyDataSetChanged()
                    // 滚动到新项目位置
                    val newPosition = projectList.indexOfFirst { it.id == id }
                    if (newPosition >= 0) {
                        recyclerView.scrollToPosition(newPosition)
                    }
                    updateEmptyState()
                    Toast.makeText(this@ProjectDictionaryActivity, "添加成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProjectDictionaryActivity, "添加失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateProject(position: Int, newName: String) {
        lifecycleScope.launch {
            try {
                val project = projectList[position]

                // 检查新名称是否已存在（排除自身）
                val existing = repository.getProjectByName(newName)
                if (existing != null && existing.id != project.id) {
                    Toast.makeText(this@ProjectDictionaryActivity, "该项目名称已存在", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val updatedProject = project.copy(
                    name = newName,
                    updateTime = System.currentTimeMillis()
                )
                repository.updateProject(updatedProject)

                // 更新列表并重新排序
                projectList[position] = updatedProject
                val sortedList = projectList.sortedWith { p1, p2 ->
                    PinyinUtil.compareForSort(p1.name, p2.name)
                }
                projectList.clear()
                projectList.addAll(sortedList)
                adapter.notifyDataSetChanged()
                Toast.makeText(this@ProjectDictionaryActivity, "更新成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ProjectDictionaryActivity, "更新失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteProject(position: Int) {
        lifecycleScope.launch {
            try {
                val project = projectList[position]
                repository.deleteProject(project)

                projectList.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, projectList.size - position)
                updateEmptyState()
                Toast.makeText(this@ProjectDictionaryActivity, "删除成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ProjectDictionaryActivity, "删除失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 项目词典适配器
     */
    inner class ProjectDictionaryAdapter(
        private val projects: MutableList<ProjectDictionary>,
        private val onEditClick: (Int) -> Unit,
        private val onDeleteClick: (Int) -> Unit
    ) : RecyclerView.Adapter<ProjectDictionaryAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tv_project_name)
            val btnEdit: ImageButton = itemView.findViewById(R.id.btn_edit)
            val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_project_dictionary_list, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val project = projects[position]
            holder.tvName.text = project.name

            // 应用主题色到编辑按钮
            val themeColor = ThemeManager.getThemeColor()
            holder.btnEdit.setColorFilter(themeColor)

            holder.btnEdit.setOnClickListener {
                onEditClick(position)
            }

            holder.btnDelete.setOnClickListener {
                onDeleteClick(position)
            }
        }

        override fun getItemCount(): Int = projects.size
    }
}
