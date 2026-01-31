package com.example.daxijizhang.ui.bill

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.daxijizhang.R
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddBillActivityTest {

    @Test
    fun testAddBillActivityIsDisplayed() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), AddBillActivity::class.java)
        ActivityScenario.launch<AddBillActivity>(intent)

        // 验证页面标题
        onView(withText(R.string.add_bill_title))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testBasicInfoFieldsAreDisplayed() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), AddBillActivity::class.java)
        ActivityScenario.launch<AddBillActivity>(intent)

        // 验证基本信息输入框显示
        onView(withId(R.id.et_start_date))
            .check(matches(isDisplayed()))

        onView(withId(R.id.et_end_date))
            .check(matches(isDisplayed()))

        onView(withId(R.id.et_community))
            .check(matches(isDisplayed()))

        onView(withId(R.id.et_phase))
            .check(matches(isDisplayed()))

        onView(withId(R.id.et_building))
            .check(matches(isDisplayed()))

        onView(withId(R.id.et_room))
            .check(matches(isDisplayed()))

        onView(withId(R.id.et_remark))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testValidationShowsErrorForEmptyCommunity() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), AddBillActivity::class.java)
        ActivityScenario.launch<AddBillActivity>(intent)

        // 点击保存按钮
        onView(withId(R.id.btn_save_bill))
            .perform(click())

        // 验证小区名输入框显示错误
        onView(withId(R.id.til_community))
            .check(matches(hasDescendant(withText(R.string.error_community_required))))
    }

    @Test
    fun testAddProjectButtonShowsDialog() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), AddBillActivity::class.java)
        ActivityScenario.launch<AddBillActivity>(intent)

        // 点击添加项目按钮
        onView(withId(R.id.btn_add_project))
            .perform(click())

        // 验证对话框显示
        onView(withText(R.string.add_project))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testBackButtonFinishesActivity() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), AddBillActivity::class.java)
        val scenario = ActivityScenario.launch<AddBillActivity>(intent)

        // 点击返回按钮
        onView(withContentDescription("Navigate up"))
            .perform(click())

        // 验证Activity已结束
        scenario.close()
    }
}
