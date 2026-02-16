package com.example.daxijizhang.ui.view

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.daxijizhang.R
import com.example.daxijizhang.data.model.ProjectDictionary
import com.example.daxijizhang.data.repository.ProjectDictionaryRepository
import com.example.daxijizhang.util.PinyinUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 项目名称智能提示输入框
 * 支持实时搜索项目词典并显示候选词列表
 */
class ProjectAutoCompleteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var editText: EditText
    private lateinit var repository: ProjectDictionaryRepository
    private var popupWindow: PopupWindow? = null
    private var recyclerView: RecyclerView? = null
    private var cardView: com.google.android.material.card.MaterialCardView? = null
    private var adapter: SuggestionAdapter? = null
    private var suggestions = mutableListOf<ProjectDictionary>()

    private val handler = Handler(Looper.getMainLooper())
    private var searchJob: Job? = null
    private var searchRunnable: Runnable? = null

    // 防抖延迟时间（毫秒）
    private val DEBOUNCE_DELAY = 300L

    // 最大显示候选词数量
    private val MAX_SUGGESTIONS = 10

    // 当前选中的位置
    private var selectedPosition = -1

    // 回调监听
    private var onSuggestionSelectedListener: ((String) -> Unit)? = null
    private var onTextChangedListener: ((String) -> Unit)? = null

    init {
        // 获取EditText引用（需要在布局中设置）
        // 实际使用时通过attachToEditText方法绑定
    }

    /**
     * 绑定到EditText
     * @param editText 要绑定的输入框
     * @param repository 项目词典仓库
     */
    fun attachToEditText(editText: EditText, repository: ProjectDictionaryRepository) {
        this.editText = editText
        this.repository = repository

        setupEditText()
        initPopupWindow()
    }

    /**
     * 设置输入框监听
     */
    private fun setupEditText() {
        // 文本变化监听（带防抖）
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onTextChangedListener?.invoke(s?.toString() ?: "")
            }

            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""

                // 取消之前的搜索任务
                searchRunnable?.let { handler.removeCallbacks(it) }

                if (query.isEmpty()) {
                    hideSuggestions()
                    return
                }

                // 创建新的搜索任务
                searchRunnable = Runnable {
                    performSearch(query)
                }
                handler.postDelayed(searchRunnable!!, DEBOUNCE_DELAY)
            }
        })

        // 焦点变化监听
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                hideSuggestions()
            }
        }

        // 键盘事件监听（上下键选择，回车确认）
        editText.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (suggestions.isNotEmpty()) {
                            selectedPosition = (selectedPosition + 1).coerceAtMost(suggestions.size - 1)
                            updateSelection()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (suggestions.isNotEmpty()) {
                            selectedPosition = (selectedPosition - 1).coerceAtLeast(0)
                            updateSelection()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_ENTER -> {
                        if (selectedPosition >= 0 && selectedPosition < suggestions.size) {
                            selectSuggestion(suggestions[selectedPosition])
                            return@setOnKeyListener true
                        }
                    }
                }
            }
            false
        }
    }

    /**
     * 初始化弹出窗口
     */
    private fun initPopupWindow() {
        val popupView = LayoutInflater.from(context).inflate(R.layout.popup_project_suggestions, null)
        recyclerView = popupView.findViewById(R.id.recycler_suggestions)
        cardView = popupView.findViewById(R.id.card_suggestions)

        adapter = SuggestionAdapter(suggestions) { position ->
            selectSuggestion(suggestions[position])
        }

        recyclerView?.layoutManager = LinearLayoutManager(context)
        recyclerView?.adapter = adapter

        // 获取屏幕宽度，计算弹窗宽度为屏幕宽度的70%
        val screenWidth = context.resources.displayMetrics.widthPixels
        val popupWidth = (screenWidth * 0.7).toInt()

        popupWindow = PopupWindow(
            popupView,
            popupWidth,
            LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = false
            setBackgroundDrawable(null)
        }
    }

    /**
     * 执行搜索
     */
    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val results = repository.searchProjects(query)
                    .sortedWith { p1, p2 ->
                        // 按项目名称拼音排序
                        PinyinUtil.compareForSort(p1.name, p2.name)
                    }
                    .take(MAX_SUGGESTIONS)

                withContext(Dispatchers.Main) {
                    if (results.isEmpty()) {
                        hideSuggestions()
                    } else {
                        showSuggestions(results, query)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 显示候选词列表
     */
    private fun showSuggestions(results: List<ProjectDictionary>, query: String = "") {
        // 保存当前查询文本用于高亮显示
        currentQuery = query
        suggestions.clear()
        suggestions.addAll(results)
        selectedPosition = -1
        adapter?.notifyDataSetChanged()

        // 根据当前主题模式设置弹窗背景色
        // 浅色模式：浅灰色(#F5F5F5)，深色模式：纯黑色(#000000)
        val isDarkMode = isDarkModeEnabled()
        val backgroundColor = if (isDarkMode) Color.BLACK else Color.parseColor("#F5F5F5")
        cardView?.setCardBackgroundColor(backgroundColor)

        if (popupWindow?.isShowing == true) {
            popupWindow?.update()
        } else {
            // 计算弹出窗口位置，使其左侧与输入框左侧对齐
            val location = IntArray(2)
            editText.getLocationOnScreen(location)

            // 获取输入框在窗口中的位置
            val editTextLocation = IntArray(2)
            editText.getLocationInWindow(editTextLocation)

            // 计算x偏移量，使弹窗左侧与输入框左侧对齐
            // 由于PopupWindow默认与anchorView左对齐，这里不需要额外偏移
            // 但需要确保宽度正确
            popupWindow?.showAsDropDown(editText, 0, 8)
        }
    }

    /**
     * 隐藏候选词列表
     */
    fun hideSuggestions() {
        popupWindow?.dismiss()
        selectedPosition = -1
    }

    /**
     * 更新选中状态
     */
    private fun updateSelection() {
        adapter?.setSelectedPosition(selectedPosition)
        recyclerView?.scrollToPosition(selectedPosition)
    }

    /**
     * 选择候选词
     */
    private fun selectSuggestion(project: ProjectDictionary) {
        editText.setText(project.name)
        editText.setSelection(project.name.length)
        hideSuggestions()

        onSuggestionSelectedListener?.invoke(project.name)

        // 保持输入法打开状态，确保用户可以继续输入单价和数量
        editText.post {
            editText.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /**
     * 设置候选词选择监听
     */
    fun setOnSuggestionSelectedListener(listener: (String) -> Unit) {
        onSuggestionSelectedListener = listener
    }

    /**
     * 设置文本变化监听
     */
    fun setOnTextChangedListener(listener: (String) -> Unit) {
        onTextChangedListener = listener
    }

    /**
     * 获取当前输入的文本
     */
    fun getText(): String {
        return editText.text?.toString() ?: ""
    }

    /**
     * 设置输入文本
     */
    fun setText(text: String) {
        editText.setText(text)
        editText.setSelection(text.length)
    }

    // 当前查询文本，用于高亮显示
    private var currentQuery: String = ""

    /**
     * 设置当前查询文本
     */
    fun setCurrentQuery(query: String) {
        currentQuery = query
    }

    /**
     * 候选词适配器
     */
    inner class SuggestionAdapter(
        private val items: List<ProjectDictionary>,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<SuggestionAdapter.ViewHolder>() {

        private var selectedPos = -1

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tv_suggestion_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_project_suggestion, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            // 设置高亮文本
            holder.tvName.text = buildHighlightedText(item.name, currentQuery)

            // 设置选中状态背景
            if (position == selectedPos) {
                holder.itemView.setBackgroundColor(context.getColor(R.color.primary_light))
            } else {
                holder.itemView.setBackgroundColor(context.getColor(android.R.color.transparent))
            }

            holder.itemView.setOnClickListener {
                onItemClick(position)
            }
        }

        override fun getItemCount(): Int = items.size

        fun setSelectedPosition(position: Int) {
            val previousPos = selectedPos
            selectedPos = position
            notifyItemChanged(previousPos)
            notifyItemChanged(selectedPos)
        }

        /**
         * 构建高亮文本
         * 浅色模式：匹配部分黑色(#000000)，非匹配部分灰色(#909090)
         * 深色模式：匹配部分白色(#FFFFFF)，非匹配部分浅白色(#A0A0A0)
         */
        private fun buildHighlightedText(fullText: String, query: String): SpannableStringBuilder {
            val builder = SpannableStringBuilder(fullText)

            // 判断当前是否为深色模式
            val isDarkMode = this@ProjectAutoCompleteView.isDarkModeEnabled()

            // 定义颜色
            val matchColor = if (isDarkMode) Color.WHITE else Color.BLACK
            val nonMatchColor = if (isDarkMode) Color.parseColor("#A0A0A0") else Color.parseColor("#909090")
            
            if (query.isEmpty()) {
                // 没有查询文本，全部显示为非匹配颜色
                builder.setSpan(
                    ForegroundColorSpan(nonMatchColor),
                    0,
                    fullText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                return builder
            }

            // 查找匹配位置（不区分大小写）
            val lowerFullText = fullText.lowercase()
            val lowerQuery = query.lowercase()
            var startIndex = 0

            while (true) {
                val matchIndex = lowerFullText.indexOf(lowerQuery, startIndex)
                if (matchIndex == -1) {
                    // 剩余部分设置为非匹配颜色
                    if (startIndex < fullText.length) {
                        builder.setSpan(
                            ForegroundColorSpan(nonMatchColor),
                            startIndex,
                            fullText.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    break
                }

                // 匹配前的部分设置为非匹配颜色
                if (matchIndex > startIndex) {
                    builder.setSpan(
                        ForegroundColorSpan(nonMatchColor),
                        startIndex,
                        matchIndex,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                // 匹配部分设置为匹配颜色（黑色/白色）
                val matchEnd = matchIndex + query.length
                builder.setSpan(
                    ForegroundColorSpan(matchColor),
                    matchIndex,
                    matchEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                startIndex = matchEnd
            }

            return builder
        }
    }

    /**
     * 判断当前是否为深色模式
     */
    private fun isDarkModeEnabled(): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
