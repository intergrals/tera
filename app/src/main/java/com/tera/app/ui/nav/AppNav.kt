package com.tera.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tera.app.BudgetApp
import com.tera.app.ui.LocalAppContainer
import com.tera.app.ui.accounts.AccountDetailScreen
import com.tera.app.ui.accounts.AccountsScreen
import com.tera.app.ui.budget.BudgetScreen
import com.tera.app.ui.categories.ManageEnvelopesScreen
import com.tera.app.ui.onboarding.OnboardingScreen
import com.tera.app.ui.reports.ReflectScreen
import com.tera.app.ui.scheduled.UpcomingScreen
import com.tera.app.ui.settings.DisclosureScreen
import com.tera.app.ui.settings.MoreScreen
import com.tera.app.ui.settings.SettingsScreen
import com.tera.app.ui.tx.TransactionEditorScreen
import androidx.compose.runtime.CompositionLocalProvider

enum class TopDestination(val route: String, val label: String, val icon: ImageVector) {
    Budget("budget", "Budget", Icons.Outlined.Savings),
    Accounts("accounts", "Accounts", Icons.Outlined.AccountBalanceWallet),
    Reflect("reflect", "Reflect", Icons.Outlined.Insights),
    More("more", "More", Icons.Outlined.MoreHoriz),
}

object Routes {
    const val BUDGET = "budget"
    const val ACCOUNTS = "accounts"
    const val REFLECT = "reflect"
    const val MORE = "more"
    const val UPCOMING = "upcoming"
    const val SETTINGS = "settings"
    const val DISCLOSURE = "disclosure"
    const val MANAGE_ENVELOPES = "manage-envelopes"
    const val ACCOUNT_DETAIL = "account/{accountId}"
    const val EDITOR = "editor?transactionId={transactionId}&accountId={accountId}"

    fun accountDetail(accountId: Long): String = "account/$accountId"

    fun editor(transactionId: Long? = null, accountId: Long? = null): String =
        "editor?transactionId=${transactionId ?: -1L}&accountId=${accountId ?: -1L}"
}

@Composable
fun TeraApp() {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as BudgetApp).container }
    CompositionLocalProvider(LocalAppContainer provides container) {
        val settings by container.settings.settings.collectAsStateWithLifecycle()
        if (!settings.onboardingDone) {
            OnboardingScreen()
        } else {
            MainNavigation()
        }
    }
}

@Composable
private fun MainNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = TopDestination.entries.firstOrNull { it.route == currentRoute }
    val isSubScreen = currentRoute != null && topLevel == null

    Scaffold(
        bottomBar = {
            if (!isSubScreen) {
                NavigationBar {
                    TopDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == topLevel,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.BUDGET,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.BUDGET) {
                BudgetScreen(
                    onAddTransaction = { navController.navigate(Routes.editor()) },
                    onOpenUpcoming = { navController.navigate(Routes.UPCOMING) },
                    onOpenManageEnvelopes = { navController.navigate(Routes.MANAGE_ENVELOPES) },
                )
            }
            composable(Routes.ACCOUNTS) {
                AccountsScreen(
                    onOpenAccount = { accountId ->
                        navController.navigate(Routes.accountDetail(accountId))
                    },
                )
            }
            composable(Routes.REFLECT) { ReflectScreen() }
            composable(Routes.MORE) {
                MoreScreen(
                    onOpenUpcoming = { navController.navigate(Routes.UPCOMING) },
                    onOpenEnvelopes = { navController.navigate(Routes.MANAGE_ENVELOPES) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenDisclosure = { navController.navigate(Routes.DISCLOSURE) },
                )
            }
            composable(
                route = Routes.ACCOUNT_DETAIL,
                arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
            ) { entry ->
                val accountId = entry.arguments?.getLong("accountId") ?: 0L
                AccountDetailScreen(
                    accountId = accountId,
                    onBack = { navController.popBackStack() },
                    onAddTransaction = { id ->
                        navController.navigate(Routes.editor(accountId = id))
                    },
                    onEditTransaction = { transactionId, acctId ->
                        navController.navigate(
                            Routes.editor(transactionId = transactionId, accountId = acctId),
                        )
                    },
                )
            }
            composable(
                route = Routes.EDITOR,
                arguments = listOf(
                    navArgument("transactionId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("accountId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) { entry ->
                val transactionId = entry.arguments?.getLong("transactionId")?.takeIf { it > 0 }
                val accountId = entry.arguments?.getLong("accountId")?.takeIf { it > 0 }
                TransactionEditorScreen(
                    transactionId = transactionId,
                    initialAccountId = accountId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.MANAGE_ENVELOPES) {
                ManageEnvelopesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.UPCOMING) {
                UpcomingScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDisclosure = { navController.navigate(Routes.DISCLOSURE) },
                )
            }
            composable(Routes.DISCLOSURE) {
                DisclosureScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
