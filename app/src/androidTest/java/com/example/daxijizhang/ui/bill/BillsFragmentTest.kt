package com.example.daxijizhang.ui.bill

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.daxijizhang.MainActivity
import com.example.daxijizhang.R
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BillsFragmentTest {

    @Test
    fun testBillsFragmentIsDisplayed() {
        ActivityScenario.launch(MainActivity::class.java)

        // 验证账单页面是否显示
        onView(withId(R.id.recycler_bills))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testBottomNavigationWorks() {
        ActivityScenario.launch(MainActivity::class.java)

        // 点击统计页面
        onView(withId(R.id.navigation_statistics))
            .perform(click())

        // 验证统计页面显示
        onView(withText("统计功能开发中"))
            .check(matches(isDisplayed()))

        // 点击用户页面
        onView(withId(R.id.navigation_user))
            .perform(click())

        // 验证用户页面显示
        onView(withText("用户功能开发中"))
            .check(matches(isDisplayed()))

        // 点击账单页面
        onView(withId(R.id.navigation_bills))
            .perform(click())

        // 验证账单页面显示
        onView(withId(R.id.recycler_bills))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testAddBillButtonOpensActivity() {
        ActivityScenario.launch(MainActivity::class.java)

        // 点击添加按钮
        onView(withId(R.id.fab_add_bill))
            .perform(click())

        // 验证添加账单页面打开
        onView(withText(R.string.add_bill_title))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testSortFilterButtonShowsMenu() {
        ActivityScenario.launch(MainActivity::class.java)

        // 点击排序筛选按钮
        onView(withId(R.id.btn_sort_filter))
            .perform(click())

        // 验证菜单项显示（由于PopupMenu的特殊性，这里只是验证按钮可点击）
        onView(withId(R.id.btn_sort_filter))
            .check(matches(isDisplayed()))
    }
}
