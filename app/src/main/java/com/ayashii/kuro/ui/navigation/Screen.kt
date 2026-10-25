package com.ayashii.kuro.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.ui.graphics.vector.ImageVector
import com.ayashii.kuro.R

sealed class Screen(val route: String, @StringRes val titleRes: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.tab_home, Icons.Default.Home)
    object Search : Screen("search", R.string.tab_search, Icons.Default.Search)
    object Library : Screen("library", R.string.tab_library, Icons.Outlined.QueueMusic)

    companion object {
        val bottomNavItems = listOf(Home, Search, Library)
    }
}
