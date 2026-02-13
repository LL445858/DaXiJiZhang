package com.example.daxijizhang.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.daxijizhang.ui.bill.BillsFragment
import com.example.daxijizhang.ui.statistics.StatisticsFragment
import com.example.daxijizhang.ui.user.UserFragment

class MainFragmentAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    
    companion object {
        const val PAGE_BILLS = 0
        const val PAGE_STATISTICS = 1
        const val PAGE_USER = 2
        const val TOTAL_PAGES = 3
    }
    
    override fun getItemCount(): Int = TOTAL_PAGES
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            PAGE_BILLS -> BillsFragment()
            PAGE_STATISTICS -> StatisticsFragment()
            PAGE_USER -> UserFragment()
            else -> BillsFragment()
        }
    }
    
    fun getPagePosition(pageId: Int): Int {
        return when (pageId) {
            com.example.daxijizhang.R.id.navigation_bills -> PAGE_BILLS
            com.example.daxijizhang.R.id.navigation_statistics -> PAGE_STATISTICS
            com.example.daxijizhang.R.id.navigation_user -> PAGE_USER
            else -> PAGE_BILLS
        }
    }
    
    fun getMenuIdForPosition(position: Int): Int {
        return when (position) {
            PAGE_BILLS -> com.example.daxijizhang.R.id.navigation_bills
            PAGE_STATISTICS -> com.example.daxijizhang.R.id.navigation_statistics
            PAGE_USER -> com.example.daxijizhang.R.id.navigation_user
            else -> com.example.daxijizhang.R.id.navigation_bills
        }
    }
}
