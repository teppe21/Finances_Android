package com.teppe21.finances

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableLongStateOf
import com.teppe21.finances.core.ui.Motion
import com.teppe21.finances.core.ui.rememberReduceMotion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.teppe21.finances.core.AppReset
import com.teppe21.finances.core.navigation.Screen
import com.teppe21.finances.core.theme.FinancesTheme
import com.teppe21.finances.data.local.preferences.LockType
import com.teppe21.finances.data.local.preferences.PinCheck
import com.teppe21.finances.data.local.preferences.UserPreferences
import com.teppe21.finances.feature.accounts.AccountsScreen
import com.teppe21.finances.feature.analytics.AnalyticsScreen
import com.teppe21.finances.feature.categories.CategoriesScreen
import com.teppe21.finances.feature.dashboard.DashboardScreen
import com.teppe21.finances.feature.importcsv.CsvImportScreen
import com.teppe21.finances.feature.more.MoreScreen
import com.teppe21.finances.feature.notifications.NotificationAutomationScreen
import com.teppe21.finances.feature.notifications.ReviewScreen
import com.teppe21.finances.feature.receipt.ScanReceiptScreen
import com.teppe21.finances.feature.settings.PrivacyPolicyScreen
import com.teppe21.finances.feature.settings.SettingsScreen
import com.teppe21.finances.feature.subscreens.BudgetsScreen
import com.teppe21.finances.feature.subscreens.RecurringScreen
import com.teppe21.finances.feature.transactions.TransactionsScreen
import com.teppe21.finances.stocks.ui.StockDetailScreen
import com.teppe21.finances.stocks.ui.StocksScreen
import com.teppe21.finances.stocks.ui.StocksSetupScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder

class MainActivity : FragmentActivity() {

    private val app get() = application as FinancesApp

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15+ enforces edge-to-edge for targetSdk 35+; opting in on every API level keeps the
        // insets handling identical everywhere. The theme-aware style is applied once preferences load.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by app.preferencesRepository.preferencesFlow.collectAsState(initial = null)
            val unlocked by app.appLock.unlocked.collectAsState()

            val isDark = when (prefs?.theme) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            val lockEnabled = prefs?.lockEnabled == true

            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(NAV_BAR_LIGHT_SCRIM, NAV_BAR_DARK_SCRIM) { isDark }
                )
                onDispose { }
            }

            LaunchedEffect(lockEnabled) {
                // Keep financial screens out of screenshots and the Recents preview while the lock is on.
                if (lockEnabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            FinancesTheme(darkTheme = isDark) {
                val current = prefs
                when {
                    // Preferences not loaded yet: show nothing rather than briefly showing data.
                    current == null -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                    current.lockEnabled && !unlocked -> LockGateScreen(current, this@MainActivity)
                    else -> MainAppNavigation()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.appLock.onAppStarted()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) app.appLock.onAppStopped()
    }

    private companion object {
        // Same scrims as the androidx.activity defaults, used behind three-button navigation.
        val NAV_BAR_LIGHT_SCRIM = AndroidColor.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val NAV_BAR_DARK_SCRIM = AndroidColor.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}

@Composable
fun LockGateScreen(prefs: UserPreferences, activity: FragmentActivity) {
    val app = FinancesApp.instance
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var lockedSeconds by remember { mutableLongStateOf(0L) }
    var checking by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }

    val biometricAvailable = remember {
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val useBiometric = prefs.appLockType == LockType.BIOMETRIC && (biometricAvailable || !prefs.hasPin)

    val launchBiometric: () -> Unit = {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    scope.launch { app.preferencesRepository.resetPinFailures() }
                    app.appLock.markUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        message = errString.toString()
                    }
                }
            }
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Finances")
            .setSubtitle("Confirm it is you to view your finances")
        if (prefs.hasPin) {
            builder.setAllowedAuthenticators(BIOMETRIC_WEAK).setNegativeButtonText("Use PIN")
        } else {
            // Lock created by version 2.0.0 without a PIN: allow the device screen lock as fallback.
            builder.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        }
        prompt.authenticate(builder.build())
    }

    LaunchedEffect(Unit) {
        if (useBiometric) launchBiometric()
    }

    LaunchedEffect(lockedSeconds) {
        if (lockedSeconds > 0) {
            delay(1000)
            lockedSeconds -= 1
            if (lockedSeconds == 0L) message = null
        }
    }

    fun submit() {
        if (checking || lockedSeconds > 0 || pin.length < 4) return
        checking = true
        scope.launch {
            when (val result = app.preferencesRepository.verifyPin(pin)) {
                PinCheck.Success -> app.appLock.markUnlocked()
                is PinCheck.Wrong -> message = if (result.attemptsBeforeLockout > 0) {
                    "Incorrect PIN. ${result.attemptsBeforeLockout} attempts left before a temporary lockout."
                } else {
                    "Incorrect PIN."
                }
                is PinCheck.LockedOut -> {
                    lockedSeconds = result.remaining.seconds.coerceAtLeast(1)
                    message = "Too many attempts. Try again in a moment."
                }
                PinCheck.NoPinSet -> message = "No PIN is set. Use biometric unlock."
            }
            pin = ""
            checking = false
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // System bars and the keyboard are consumed here once; the content scrolls when the keyboard,
        // a large font or a small screen leaves too little room.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Finances is locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(24.dp))

            if (prefs.hasPin) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { input -> if (input.length <= 8 && input.all { it.isDigit() }) pin = input },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    singleLine = true,
                    enabled = lockedSeconds == 0L,
                    modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { submit() },
                    enabled = pin.length >= 4 && lockedSeconds == 0L && !checking,
                    modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth()
                ) { Text(if (lockedSeconds > 0) "Locked for ${lockedSeconds}s" else "Unlock") }
            }

            if (useBiometric) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = launchBiometric, modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth()) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Use biometrics")
                }
            }

            // The message fades in place; it never delays or replaces the PIN field.
            AnimatedContent(
                targetState = message,
                transitionSpec = { Motion.fadeInShort() togetherWith Motion.fadeOutShort() },
                label = "lockMessage"
            ) { current ->
                if (current != null) {
                    Text(
                        current,
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Spacer(Modifier.height(0.dp))
                }
            }

            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { showReset = true }) { Text("Forgot PIN?") }
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Reset Finances?") },
            text = {
                Text(
                    "The PIN cannot be recovered. Resetting erases every transaction, account, category, " +
                        "receipt record, the stock watchlist, the market data API key and all settings on this device. " +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showReset = false
                    scope.launch { AppReset.fullReset(app) }
                }) { Text("Erase everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("Cancel") } }
        )
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val tabs = listOf(
        Tab(Screen.Dashboard.route, stringResource(R.string.nav_home), Icons.Default.Home),
        Tab(Screen.Transactions.route, stringResource(R.string.nav_transactions), Icons.AutoMirrored.Filled.ReceiptLong),
        Tab(Screen.Analytics.route, stringResource(R.string.nav_analytics), Icons.Default.PieChart),
        Tab(Screen.More.route, stringResource(R.string.nav_more), Icons.Default.Menu)
    )
    val isTopLevel = tabs.any { it.route == currentRoute }
    val hideBars = currentRoute == Screen.ScanReceipt.route

    fun navigateTab(route: String) {
        if (route == currentRoute) return
        navController.navigate(if (route == Screen.Transactions.route) Screen.Transactions.BASE else route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val reduceMotion = rememberReduceMotion()
    val topLevelRoutes = remember(tabs) { tabs.map { it.route }.toSet() }
    // Tab switches fade (the destinations are peers); drilling in or out slides a short distance.
    // The camera screen only fades so the viewfinder never moves, and reduced motion always fades.
    fun fadesOnly(from: String?, to: String?): Boolean =
        reduceMotion ||
            (from in topLevelRoutes && to in topLevelRoutes) ||
            from == Screen.ScanReceipt.route || to == Screen.ScanReceipt.route

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = !hideBars,
                enter = Motion.appear(),
                exit = Motion.disappear()
            ) {
                TopAppBar(
                    title = {
                        Crossfade(targetState = screenTitle(currentRoute), animationSpec = Motion.short(), label = "title") { title ->
                            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    navigationIcon = {
                        AnimatedVisibility(
                            visible = !isTopLevel,
                            enter = Motion.fadeInShort(),
                            exit = Motion.fadeOutShort()
                        ) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !hideBars && isTopLevel,
                enter = slideInVertically(Motion.medium()) { it } + fadeIn(Motion.short()),
                exit = slideOutVertically(Motion.exit()) { it } + fadeOut(Motion.exit())
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navigateTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, maxLines = 1, softWrap = false) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(if (hideBars) PaddingValues() else innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                enterTransition = {
                    if (fadesOnly(initialState.destination.route, targetState.destination.route)) {
                        fadeIn(tween(Motion.DURATION_MEDIUM, delayMillis = 60, easing = Motion.Decelerate))
                    } else {
                        slideInHorizontally(Motion.long()) { it / 10 } + fadeIn(tween(Motion.DURATION_MEDIUM, easing = Motion.Decelerate))
                    }
                },
                exitTransition = {
                    if (fadesOnly(initialState.destination.route, targetState.destination.route)) {
                        fadeOut(Motion.exit())
                    } else {
                        slideOutHorizontally(Motion.long()) { -it / 14 } + fadeOut(Motion.exit())
                    }
                },
                popEnterTransition = {
                    if (fadesOnly(initialState.destination.route, targetState.destination.route)) {
                        fadeIn(tween(Motion.DURATION_MEDIUM, delayMillis = 60, easing = Motion.Decelerate))
                    } else {
                        slideInHorizontally(Motion.long()) { -it / 14 } + fadeIn(tween(Motion.DURATION_MEDIUM, easing = Motion.Decelerate))
                    }
                },
                popExitTransition = {
                    if (fadesOnly(initialState.destination.route, targetState.destination.route)) {
                        fadeOut(Motion.exit())
                    } else {
                        slideOutHorizontally(Motion.long()) { it / 10 } + fadeOut(Motion.exit())
                    }
                }
            ) {
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        onNavigateToScanReceipt = { navController.navigate(Screen.ScanReceipt.route) },
                        onNavigateToImportCsv = { navController.navigate(Screen.CsvImport.route) },
                        onNavigateToReview = { navController.navigate(Screen.Review.route) },
                        onNavigateToTransactions = { navigateTab(Screen.Transactions.route) },
                        onNavigateToStocks = { navController.navigate(Screen.Stocks.route) },
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                        onAddTransaction = { navController.navigate(Screen.Transactions.withAddDialog()) }
                    )
                }
                composable(
                    Screen.Transactions.route,
                    arguments = listOf(navArgument("add") { type = NavType.BoolType; defaultValue = false })
                ) { entry ->
                    TransactionsScreen(openAddDialog = entry.arguments?.getBoolean("add") == true)
                }
                composable(Screen.Analytics.route) { AnalyticsScreen() }
                composable(Screen.Accounts.route) { AccountsScreen() }
                composable(Screen.More.route) {
                    MoreScreen(
                        onNavigateToStocks = { navController.navigate(Screen.Stocks.route) },
                        onNavigateToAccounts = { navController.navigate(Screen.Accounts.route) },
                        onNavigateToNotifications = { navController.navigate(Screen.NotificationAutomation.route) },
                        onNavigateToCsvImport = { navController.navigate(Screen.CsvImport.route) },
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                        onNavigateToBudgets = { navController.navigate(Screen.Budgets.route) },
                        onNavigateToRecurring = { navController.navigate(Screen.Recurring.route) },
                        onNavigateToCategories = { navController.navigate(Screen.Categories.route) }
                    )
                }
                composable(Screen.ScanReceipt.route) { ScanReceiptScreen(onNavigateBack = { navController.popBackStack() }) }
                composable(Screen.NotificationAutomation.route) {
                    NotificationAutomationScreen(onOpenReview = { navController.navigate(Screen.Review.route) })
                }
                composable(Screen.Review.route) { ReviewScreen() }
                composable(Screen.CsvImport.route) {
                    CsvImportScreen(onImportFinished = { navigateTab(Screen.Transactions.route) })
                }
                composable(Screen.Budgets.route) { BudgetsScreen() }
                composable(Screen.Recurring.route) { RecurringScreen() }
                composable(Screen.Categories.route) { CategoriesScreen() }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onOpenNotifications = { navController.navigate(Screen.NotificationAutomation.route) },
                        onOpenMarketData = { navController.navigate(Screen.StocksSetup.route) },
                        onOpenPrivacyPolicy = { navController.navigate(Screen.PrivacyPolicy.route) }
                    )
                }
                composable(Screen.PrivacyPolicy.route) { PrivacyPolicyScreen() }
                composable(Screen.Stocks.route) {
                    StocksScreen(
                        onOpenStock = { navController.navigate(Screen.StockDetail.of(it)) },
                        onOpenSetup = { navController.navigate(Screen.StocksSetup.route) }
                    )
                }
                composable(Screen.StockDetail.route, arguments = listOf(navArgument("symbol") { type = NavType.StringType })) { entry ->
                    val symbol = URLDecoder.decode(entry.arguments?.getString("symbol").orEmpty(), "UTF-8")
                    StockDetailScreen(symbol = symbol, onRemoved = { navController.popBackStack() })
                }
                composable(Screen.StocksSetup.route) { StocksSetupScreen(onDone = { navController.popBackStack() }) }
            }
        }
    }
}

@Composable
private fun screenTitle(route: String?): String = when (route) {
    Screen.Dashboard.route -> stringResource(R.string.app_name)
    Screen.Transactions.route -> stringResource(R.string.transactions_title)
    Screen.Analytics.route -> stringResource(R.string.analytics_title)
    Screen.Accounts.route -> stringResource(R.string.accounts_title)
    Screen.More.route -> stringResource(R.string.more_title)
    Screen.NotificationAutomation.route -> stringResource(R.string.notif_title)
    Screen.Review.route -> "Review queue"
    Screen.CsvImport.route -> stringResource(R.string.import_title)
    Screen.Budgets.route -> stringResource(R.string.budgets)
    Screen.Recurring.route -> stringResource(R.string.recurring)
    Screen.Categories.route -> stringResource(R.string.categories)
    Screen.Settings.route -> stringResource(R.string.settings)
    Screen.Stocks.route -> "Stocks"
    Screen.StockDetail.route -> "Stock details"
    Screen.StocksSetup.route -> "Market data"
    Screen.PrivacyPolicy.route -> "Privacy policy"
    else -> stringResource(R.string.app_name)
}
