package `in`.drarunbabu.drsharmila.jananisync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.drarunbabu.drsharmila.jananisync.ui.navigation.SignedInRoute
import `in`.drarunbabu.drsharmila.jananisync.data.model.UserMembershipState
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import `in`.drarunbabu.drsharmila.jananisync.data.auth.AuthRepository
import `in`.drarunbabu.drsharmila.jananisync.data.auth.AuthSessionState
import `in`.drarunbabu.drsharmila.jananisync.data.privacy.PrivacyDecision
import `in`.drarunbabu.drsharmila.jananisync.di.appModule
import `in`.drarunbabu.drsharmila.jananisync.ui.components.*
import `in`.drarunbabu.drsharmila.jananisync.ui.screens.ClinicalCareFlow
import `in`.drarunbabu.drsharmila.jananisync.ui.screens.LeaveOrganizationAction
import `in`.drarunbabu.drsharmila.jananisync.ui.screens.OrganizationAccessScreen
import `in`.drarunbabu.drsharmila.jananisync.ui.screens.SettingsScreen
import `in`.drarunbabu.drsharmila.jananisync.ui.screens.ResetPasswordScreen
import `in`.drarunbabu.drsharmila.jananisync.ui.theme.JananiSyncTheme
import `in`.drarunbabu.drsharmila.jananisync.ui.theme.LocalAppearanceSettings
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.*
import jananisync.shared.generated.resources.Res
import jananisync.shared.generated.resources.janani_sync_logo
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.koinConfiguration

private const val APP_NAME = "JananiSync"
private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

@Serializable
@SerialName("login")
private data object LoginRoute

@Serializable
@SerialName("signup")
private data object SignUpRoute

@Serializable
@SerialName("settings")
private data object SettingsRoute

@Serializable
@SerialName("reset-password")
private data class ResetPasswordRoute(val email: String = "")

@Serializable
@SerialName("verify-email")
private data object EmailVerificationRoute

@Composable
fun App(
    onNavHostReady: suspend (NavController) -> Unit = {},
    onBrowserBack: (() -> Unit)? = null,
    onBrowserForward: (() -> Unit)? = null
) {
    KoinApplication(configuration = koinConfiguration { modules(appModule) }) {
        val appViewModel: AppViewModel = koinViewModel()
        val appearance by appViewModel.appearance.collectAsState()
        appearance?.let { settings ->
            JananiSyncTheme(settings) {
                Column(Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Text(
                            text = "Work in progress — Dummy data only. Do not enter actual patient or clinical data. Not for clinical use.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AdaptiveLayout { JananiSyncNavigation(onNavHostReady, appViewModel, onBrowserBack, onBrowserForward) }
                    }
                }
            }
        }
    }
}

@Composable
private fun JananiSyncNavigation(
    onNavHostReady: suspend (NavController) -> Unit,
    appViewModel: AppViewModel,
    onBrowserBack: (() -> Unit)?,
    onBrowserForward: (() -> Unit)?
) {
    val navController = rememberNavController()
    val uriHandler = LocalUriHandler.current
    val authRepository: AuthRepository = koinInject()
    // Keep the sign-in operation alive while authState changes. Firebase may notify
    // onAuthStateChanged before signInWithEmailAndPassword's Promise completes; if the
    // login destination owned this ViewModel, navigation could cancel its in-flight await.
    val loginViewModel: LoginViewModel = koinViewModel()
    val loginState by loginViewModel.uiState.collectAsState()
    // Account creation must finish sending verification mail before navigation can dispose its screen.
    val signUpViewModel: SignUpViewModel = koinViewModel()
    val signUpState by signUpViewModel.uiState.collectAsState()
    val authSession by authRepository.sessionState.collectAsState()
    val privacyViewModel: PrivacyConsentViewModel = koinViewModel()
    val privacyState by privacyViewModel.uiState.collectAsState()
    val accountUid = (authSession as? AuthSessionState.SignedIn)?.userUid
    val privacyAccepted = privacyState.allowsAccount(accountUid)
    val privacyGateRequired = accountUid != null && !privacyAccepted
    var showPrivacy by remember { mutableStateOf(false) }
    var reviewPrivacyDecision by remember(accountUid) { mutableStateOf(false) }
    var showPrivacyHistory by remember(accountUid) { mutableStateOf(false) }
    // Shared across roster destinations and Settings; selecting a ward must not
    // restart membership observation or discard the active clinical session.
    val organizationAccessViewModel: OrganizationAccessViewModel = koinViewModel()
    val labourCareViewModel: LabourCareViewModel = koinViewModel()
    val observedAccessState by organizationAccessViewModel.uiState.collectAsState()
    val labourCareState by labourCareViewModel.uiState.collectAsState()
    val userUid = (authSession as? AuthSessionState.SignedIn)
        ?.takeIf { privacyAccepted && it.emailVerified && it.userUid == authRepository.currentVerifiedUserUid() }?.userUid
    val accessState = if (observedAccessState.userUid == userUid) observedAccessState
        else OrganizationAccessUiState()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentClinicalRoute = currentEntry?.takeIf { it.destination.hasRoute<SignedInRoute>() }
        ?.toRoute<SignedInRoute>()
    val canGoBack = navController.previousBackStackEntry != null || currentClinicalRoute?.parent() != null

    fun goBack() {
        if (navController.previousBackStackEntry != null) {
            // The browser binding restores NavController from the popstate event.
            // Popping directly would push a new browser entry for the parent.
            if (onBrowserBack != null) onBrowserBack() else navController.popBackStack()
        } else {
            currentClinicalRoute?.parent()?.let { parent ->
                navController.navigate(parent) {
                    popUpTo(navController.graph.id) { inclusive = false }
                }
            }
        }
    }

    fun resetClinicalNavigation() {
        navController.navigate(SignedInRoute()) {
            popUpTo(navController.graph.id) { inclusive = false }
            launchSingleTop = true
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var hasVerifiedSession by remember { mutableStateOf(false) }
    fun notifyUser(message: String) {
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    var isSigningOut by remember(userUid) { mutableStateOf(false) }
    fun signOut() {
        if (!isSigningOut) {
            coroutineScope.launch {
                isSigningOut = true
                organizationAccessViewModel.clearSensitiveState()
                labourCareViewModel.stopObserving()
                val result = authRepository.signOut()
                isSigningOut = false
                notifyUser(result.message)
                if (result.isSuccess) {
                    hasVerifiedSession = false
                    navController.navigate(LoginRoute) {
                        popUpTo(navController.graph.id) { inclusive = false }
                        launchSingleTop = true
                    }
                } else {
                    val currentUid = authRepository.currentVerifiedUserUid()
                    if (privacyViewModel.uiState.value.allowsAccount(currentUid)) {
                        organizationAccessViewModel.startObserving(currentUid)
                    }
                }
            }
        }
    }

    LaunchedEffect(userUid) {
        if (userUid != null) organizationAccessViewModel.startObserving(userUid)
        else organizationAccessViewModel.clearSensitiveState()
    }
    LaunchedEffect(accessState.membership, userUid, accessState.maintenance.active) {
        val member = accessState.membership as? UserMembershipState.ActiveMember
        if (member != null && member.role in setOf("admin", "doctor", "nurse") && userUid != null && !accessState.maintenance.active) {
            labourCareViewModel.setRecorderName(member.name)
            labourCareViewModel.observe(member.orgId, userUid, member.accessLevel)
        } else {
            labourCareViewModel.setRecorderName("")
            labourCareViewModel.stopObserving()
        }
    }
    LaunchedEffect(signUpState.message) {
        signUpState.message?.let {
            if (authSession == AuthSessionState.SignedOut && !signUpState.isLoading) privacyViewModel.cancelSignupPreparation()
            notifyUser(it)
            signUpViewModel.clearMessage()
        }
    }
    LaunchedEffect(accessState.message) {
        accessState.message?.let {
            notifyUser(it)
            organizationAccessViewModel.clearMessage()
        }
    }
    LaunchedEffect(accessState.lastLeftOrganizationId) {
        if (accessState.lastLeftOrganizationId != null && userUid != null) {
            labourCareViewModel.clearSelectedPatient()
            resetClinicalNavigation()
            organizationAccessViewModel.clearLeaveEvent()
        }
    }
    LaunchedEffect(labourCareState.feed.message, labourCareState.orgId, userUid) {
        // Keep this snackbar owned by the current feed. Clearing the warning or
        // changing sessions cancels showSnackbar and removes any queued warning.
        labourCareState.feed.message?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(navController) {
        onNavHostReady(navController)
    }

    LaunchedEffect(authSession, privacyAccepted, loginState.isLoading, signUpState.isLoading) {
        if (authSession == AuthSessionState.Loading) return@LaunchedEffect
        if (authSession is AuthSessionState.SignedIn && !privacyAccepted) {
            hasVerifiedSession = false
            return@LaunchedEffect
        }
        // Do not dispose the login destination while Firebase's sign-in Promise is still
        // completing. Auth state can be published before the sign-in request returns.
        if (authSession is AuthSessionState.SignedIn && (loginState.isLoading || signUpState.isLoading)) return@LaunchedEffect
        withFrameNanos { }
        when (val session = authSession) {
            AuthSessionState.Loading -> Unit
            AuthSessionState.SignedOut -> {
                val hadVerifiedSession = hasVerifiedSession
                hasVerifiedSession = false
                if (hadVerifiedSession) {
                    navController.navigate(LoginRoute) {
                        popUpTo(navController.graph.id) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
            is AuthSessionState.SignedIn -> {
                if (session.emailVerified) {
                    val wasVerified = hasVerifiedSession
                    hasVerifiedSession = true
                    if (!wasVerified && currentClinicalRoute == null) {
                        resetClinicalNavigation()
                    }
                } else {
                    hasVerifiedSession = false
                    navController.navigate(EmailVerificationRoute) {
                        popUpTo(navController.graph.id) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    LaunchedEffect(currentEntry?.id, authSession, privacyAccepted, loginState.isLoading, signUpState.isLoading) {
        val destination = currentEntry?.destination
        if (userUid != null && !loginState.isLoading && !signUpState.isLoading && destination != null &&
            (destination.hasRoute<LoginRoute>() || destination.hasRoute<SignUpRoute>() ||
                destination.hasRoute<EmailVerificationRoute>())) {
            resetClinicalNavigation()
        }
    }

    val appearanceError by appViewModel.appearanceError.collectAsState()
    LaunchedEffect(appearanceError) {
        appearanceError?.let {
            notifyUser(it)
            appViewModel.clearAppearanceError()
        }
    }

    run {
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                    Snackbar(snackbarData = snackbarData)
                }
            },
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        Row(
                            modifier = Modifier.padding(start = 4.dp, end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = ::goBack, enabled = canGoBack && !privacyGateRequired, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                            }
                            // The browser owns the forward stack, including native Back/Forward
                            // gestures. Forward is a safe no-op when that stack is empty.
                            IconButton(onClick = { onBrowserForward?.invoke() }, enabled = onBrowserForward != null && !privacyGateRequired,
                                modifier = Modifier.size(48.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go forward")
                            }
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (LocalAppWindowSize.current != AppWindowSize.Compact) {
                                Image(
                                    painter = painterResource(Res.drawable.janani_sync_logo),
                                    contentDescription = "$APP_NAME logo",
                                    modifier = Modifier.size(30.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(APP_NAME, style = if (LocalAppWindowSize.current == AppWindowSize.Compact)
                                MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    actions = {
                        val activeMember = accessState.membership as? UserMembershipState.ActiveMember
                        if (LocalAppWindowSize.current == AppWindowSize.Compact) {
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("User manual") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
                                        onClick = { menuExpanded = false; uriHandler.openUri("user-manual.html") }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Privacy policy") },
                                        leadingIcon = { Icon(Icons.Default.PrivacyTip, contentDescription = null) },
                                        onClick = { menuExpanded = false; showPrivacy = true }
                                    )
                                    if (userUid != null && activeMember?.role == "admin") {
                                        DropdownMenuItem(
                                            text = { Text(if (currentClinicalRoute?.adminConsole == true) "Patient care" else "Admin console") },
                                            leadingIcon = {
                                                Icon(
                                                    if (currentClinicalRoute?.adminConsole == true) Icons.Default.MedicalServices else Icons.Default.AdminPanelSettings,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            },
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            onClick = {
                                                menuExpanded = false
                                                navController.navigate(SignedInRoute(adminConsole = currentClinicalRoute?.adminConsole != true)) {
                                                    launchSingleTop = true
                                                }
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        enabled = !privacyGateRequired,
                                        onClick = {
                                            menuExpanded = false
                                            navController.navigate(SettingsRoute) { launchSingleTop = true }
                                        }
                                    )
                                }
                            }
                        } else {
                            IconButton(onClick = { uriHandler.openUri("user-manual.html") }) {
                                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "User manual")
                            }
                            IconButton(onClick = { showPrivacy = true }) {
                                Icon(Icons.Default.PrivacyTip, contentDescription = "Privacy policy")
                            }
                            if (userUid != null && activeMember?.role == "admin") {
                                MgButton(
                                    text = if (currentClinicalRoute?.adminConsole == true) "Patient dashboard" else "Admin console",
                                    leadingIcon = {
                                        Icon(
                                            if (currentClinicalRoute?.adminConsole == true) Icons.Default.MedicalServices else Icons.Default.AdminPanelSettings,
                                            contentDescription = null,
                                            modifier = Modifier.size(ButtonDefaults.IconSize)
                                        )
                                    },
                                    onClick = {
                                        navController.navigate(SignedInRoute(adminConsole = currentClinicalRoute?.adminConsole != true)) {
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                            IconButton(enabled = !privacyGateRequired, onClick = {
                                navController.navigate(SettingsRoute) { launchSingleTop = true }
                            }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
                        if (userUid != null) {
                            AccountMenu(
                                membership = accessState.membership,
                                fallbackName = accessState.name.ifBlank { "Account" },
                                isSigningOut = isSigningOut,
                                isLeavingOrganization = accessState.leavingOrganizationId != null,
                                maintenance = accessState.maintenance,
                                onLeaveOrganization = organizationAccessViewModel::leaveOrganization,
                                onSignOut = ::signOut,
                                onSwitchOrganization = {
                                    labourCareViewModel.clearSelectedPatient()
                                    organizationAccessViewModel.cancelJoiningOrganization()
                                    organizationAccessViewModel.selectOrganization(null)
                                    resetClinicalNavigation()
                                },
                                onJoinAnotherOrganization = {
                                    val destination = if (currentClinicalRoute == null || currentClinicalRoute.adminConsole || currentClinicalRoute.archivedWomen)
                                        SignedInRoute(joiningOrganization = true)
                                    else currentClinicalRoute.copy(joiningOrganization = true)
                                    navController.navigate(destination) { launchSingleTop = true }
                                }
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding).imePadding()) {
                NavHost(
                    navController = navController,
                    startDestination = LoginRoute,
                    modifier = Modifier.fillMaxSize()
                ) {
                composable<LoginRoute> {
                    if (privacyGateRequired) return@composable
                    LaunchedEffect(loginState.message) {
                        loginState.message?.let {
                            notifyUser(it)
                            loginViewModel.clearMessage()
                        }
                    }

                    LoginScreen(
                        isLoading = loginState.isLoading,
                        onLogin = { email, password ->
                            when {
                                email.isBlank() -> notifyUser("Enter your username to continue.")
                                !EMAIL_PATTERN.matches(email.trim()) -> notifyUser("Enter a valid email address as your username.")
                                password.isBlank() -> notifyUser("Enter your password to continue.")
                                else -> loginViewModel.signIn(email, password)
                            }
                        },
                        onResetPassword = { email ->
                            navController.navigate(ResetPasswordRoute(email.trim()))
                        },
                        onSignUp = { navController.navigate(SignUpRoute) }
                    )
                }
                composable<ResetPasswordRoute> { backStackEntry ->
                    if (privacyGateRequired) return@composable
                    val route = backStackEntry.toRoute<ResetPasswordRoute>()
                    val resetViewModel: ResetPasswordViewModel = koinViewModel()
                    val resetState by resetViewModel.uiState.collectAsState()
                    ResetPasswordScreen(
                        initialEmail = route.email,
                        state = resetState,
                        onSubmit = resetViewModel::sendResetLink,
                        onEmailChange = resetViewModel::clearMessage,
                        onBackToLogin = {
                            navController.navigate(LoginRoute) {
                                popUpTo(navController.graph.id) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable<SignUpRoute> {
                    if (privacyGateRequired) return@composable
                    SignUpScreen(
                        isLoading = signUpState.isLoading,
                        onCreateAccount = { email, password, confirmPassword ->
                            when {
                                email.isBlank() -> notifyUser("Enter your username (your email address).")
                                !EMAIL_PATTERN.matches(email.trim()) -> notifyUser("Enter a valid email address as your username.")
                                password.isBlank() -> notifyUser("Enter a password.")
                                password != confirmPassword -> notifyUser("Passwords do not match.")
                                else -> {
                                    privacyViewModel.preparingSignup()
                                    signUpViewModel.createAccount(email, password, confirmPassword)
                                }
                            }
                        }
                    )
                }
                composable<EmailVerificationRoute> {
                    if (privacyGateRequired) return@composable
                    val verificationViewModel: EmailVerificationViewModel = koinViewModel()
                    val verificationState by verificationViewModel.uiState.collectAsState()
                    LaunchedEffect(verificationState.message) {
                        verificationState.message?.let {
                            notifyUser(it)
                            verificationViewModel.clearMessage()
                        }
                    }
                    EmailVerificationScreen(
                        isLoading = verificationState.isLoading,
                        onResend = verificationViewModel::resend,
                        onCheckVerification = verificationViewModel::checkVerification
                    )
                }
                composable<SettingsRoute> {
                    if (privacyGateRequired) return@composable
                    SettingsScreen(
                        settings = LocalAppearanceSettings.current,
                        onThemeMode = appViewModel::setThemeMode,
                        onFontSize = appViewModel::setBasicFontSize0,
                        onAccentColour = appViewModel::setAccentColour,
                        onDone = ::goBack,
                        onPrivacyPolicy = { showPrivacy = true },
                        onPrivacyDecision = { reviewPrivacyDecision = true },
                        onPrivacyHistory = { privacyViewModel.loadHistory(); showPrivacyHistory = true },
                        authenticatedUserUid = (authSession as? AuthSessionState.SignedIn)?.userUid,
                        publicKeyFingerprint = accessState.ownFingerprint,
                        canVerifyFingerprint = userUid != null && (accessState.membership is UserMembershipState.ActiveMember ||
                            accessState.membership is UserMembershipState.PendingApproval),
                        isVerifyingFingerprint = accessState.isVerifyingFingerprint,
                        fingerprintError = accessState.fingerprintError,
                        onVerifyFingerprint = organizationAccessViewModel::verifyOwnFingerprint,
                        onDismissFingerprint = organizationAccessViewModel::clearFingerprintError,
                        organizationAccessState = accessState,
                        onPrepareReplacementKey = organizationAccessViewModel::prepareReplacementKey,
                        onReplacementPhraseSaved = organizationAccessViewModel::confirmReplacementPhraseSaved,
                        onDiscardReplacementSetup = organizationAccessViewModel::discardReplacementSetup,
                        onSubmitKeyReplacement = organizationAccessViewModel::submitKeyReplacement,
                        onResolveKeyReplacement = organizationAccessViewModel::resolveKeyReplacement,
                        onRetryKeyReplacements = organizationAccessViewModel::retryKeyReplacements
                    )
                }
                composable<SignedInRoute> { backStackEntry ->
                    if (privacyGateRequired) return@composable
                    val route = backStackEntry.toRoute<SignedInRoute>()
                    // Browser Forward can revisit old authenticated entries after logout.
                    if (userUid == null) {
                        LaunchedEffect(authSession) {
                            when (val session = authSession) {
                                AuthSessionState.Loading -> Unit
                                AuthSessionState.SignedOut -> navController.navigate(LoginRoute) {
                                    popUpTo(navController.graph.id) { inclusive = false }
                                }
                                is AuthSessionState.SignedIn -> {
                                    if (!session.emailVerified) navController.navigate(EmailVerificationRoute) {
                                        popUpTo(navController.graph.id) { inclusive = false }
                                    } else navController.navigate(LoginRoute) {
                                        popUpTo(navController.graph.id) { inclusive = false }
                                    }
                                }
                            }
                        }
                        return@composable
                    }
                    if (!route.isWellFormed()) {
                        LaunchedEffect(backStackEntry.id) {
                            labourCareViewModel.clearSelectedPatient()
                            resetClinicalNavigation()
                            notifyUser("This page is unavailable.")
                        }
                        return@composable
                    }
                    val isCurrentDestination = currentEntry?.id == backStackEntry.id
                    LaunchedEffect(isCurrentDestination, route.adminConsole) {
                        if (isCurrentDestination && route.adminConsole) organizationAccessViewModel.refreshMemberKeyGrants()
                    }
                    LaunchedEffect(isCurrentDestination, route.joiningOrganization) {
                        if (!isCurrentDestination) return@LaunchedEffect
                        if (route.joiningOrganization && !accessState.isRequestingAdditionalOrganization) {
                            organizationAccessViewModel.beginJoiningOrganization()
                        } else if (!route.joiningOrganization && accessState.isRequestingAdditionalOrganization) {
                            organizationAccessViewModel.cancelJoiningOrganization()
                        }
                    }
                    var wasSubmitting by remember { mutableStateOf(false) }
                    LaunchedEffect(accessState.isSubmitting) {
                        if (isCurrentDestination && wasSubmitting && !accessState.isSubmitting && route.joiningOrganization &&
                            !accessState.isRequestingAdditionalOrganization) goBack()
                        wasSubmitting = accessState.isSubmitting
                    }

                    OrganizationAccessScreen(
                        state = accessState.copy(isRequestingAdditionalOrganization = route.joiningOrganization),
                        showAdminConsole = route.adminConsole,
                        onAccessCodeChange = organizationAccessViewModel::setAccessCode,
                        onNameChange = organizationAccessViewModel::setName,
                        onRoleSelected = organizationAccessViewModel::selectRole,
                        onPrepareSecureAccess = organizationAccessViewModel::prepareSecureAccess,
                        onPrepareSecureAccessFromOrganization = organizationAccessViewModel::prepareSecureAccessFromOrganization,
                        onRecoveryPhraseConfirmed = organizationAccessViewModel::confirmRecoveryPhraseSaved,
                        onAdminRecoveryPhraseChange = organizationAccessViewModel::setAdminRecoveryPhraseInput,
                        onInitializeOrganizationKey = organizationAccessViewModel::initializeOrganizationKey,
                        onUnlockOrganizationKey = organizationAccessViewModel::unlockOrganizationKey,
                        onGrantOrganizationKey = organizationAccessViewModel::grantOrganizationKey,
                        onRefreshKeyGrants = organizationAccessViewModel::refreshMemberKeyGrants,
                        onResumeMaintenance = organizationAccessViewModel::resumeMaintenance,
                        onRotateOrganizationKey = organizationAccessViewModel::rotateOrganizationKey,
                        onCancelPendingRequest = organizationAccessViewModel::cancelPendingRequest,
                        onLeaveOrganization = organizationAccessViewModel::leaveOrganization,
                        onResolveKeyReplacement = organizationAccessViewModel::resolveKeyReplacement,
                        onRetryKeyReplacements = organizationAccessViewModel::retryKeyReplacements,
                        onSubmitRequest = organizationAccessViewModel::submitAccessRequest,
                        onApprove = organizationAccessViewModel::approve,
                        onUpdateMemberAccessLevel = organizationAccessViewModel::updateAccessLevel,
                        onUpdateMemberRole = organizationAccessViewModel::updateMemberRole,
                        onSetMemberActive = organizationAccessViewModel::setMemberActive,
                        onSelectOrganization = { orgId ->
                            labourCareViewModel.clearSelectedPatient()
                            organizationAccessViewModel.selectOrganization(orgId)
                            resetClinicalNavigation()
                        },
                        onRetry = { organizationAccessViewModel.retry(userUid) },
                        organizationKeyAvailable = labourCareState.orgId ==
                            (accessState.membership as? UserMembershipState.ActiveMember)?.orgId &&
                            labourCareState.organizationKeyAvailable,
                        onUnlockMemberOrganizationKey = { phrase, _ ->
                            organizationAccessViewModel.unlockOrganizationKey(phrase)
                        },
                        clinicalContent = { member ->
                            if (!route.isAllowedFor(member)) {
                                Text("This page is unavailable.")
                            } else {
                                ClinicalCareFlow(
                                    member = member,
                                    archivedWomen = route.archivedWomen,
                                    onArchivedWomen = { navController.navigate(SignedInRoute(archivedWomen = true)) },
                                    adminConsole = route.adminConsole || (member.role == "admin" && !accessState.organizationKeyExists),
                                    onEmptyOrganization = {
                                        navController.navigate(SignedInRoute(adminConsole = true)) {
                                            popUpTo(backStackEntry.destination.id) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    isCurrentDestination = isCurrentDestination,
                                    wardId = route.wardId,
                                    admissionId = route.admissionId,
                                    partograph = route.partograph,
                                    onSwitchChart = {
                                        navController.navigate(route.copy(partograph = !route.partograph)) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onPartographSelected = { woman ->
                                        navController.navigate(SignedInRoute(
                                            wardId = woman.admittedWard.wardId, admissionId = woman.admissionId,
                                            partograph = true, returnToArchivedWomen = route.archivedWomen || woman.isArchived
                                        ))
                                    },
                                    onWardSelected = { ward ->
                                        navController.navigate(SignedInRoute(wardId = ward.wardId))
                                    },
                                    onWomanSelected = { woman ->
                                        navController.navigate(SignedInRoute(
                                            wardId = woman.admittedWard.wardId, admissionId = woman.admissionId,
                                            returnToArchivedWomen = route.archivedWomen || woman.isArchived
                                        ))
                                    },
                                    state = labourCareState,
                                    onPatientSelected = { woman -> labourCareViewModel.selectPatient(woman.admissionId, woman.name, woman.firestoreDocumentId, woman.isArchived) },
                                    onBindAdmissionColumns = labourCareViewModel::bindAdmissionColumns,
                                    onPatientCleared = labourCareViewModel::clearSelectedPatient,
                                    onUnlockOrganizationKey = { phrase, _ ->
                                        organizationAccessViewModel.unlockOrganizationKey(phrase)
                                    },
                                    isUnlockingOrganizationKey = accessState.isOrganizationKeyBusy,
                                    onCellSave = { column, field, value ->
                                        if (field in `in`.drarunbabu.drsharmila.jananisync.data.model.allHeaderFields.map { it.first })
                                            labourCareViewModel.saveHeaderCell(field, value, expectedSessionId = route.admissionId, expectedOrgId = member.orgId)
                                        else labourCareViewModel.saveCell(column, field, value)
                                    },
                                    onConfirmColumn = labourCareViewModel::confirmColumn,
                                    onConfirmFullDilation = labourCareViewModel::confirmFullDilation,
                                    onConfirmBirth = labourCareViewModel::confirmBirth,
                                    onPartographSave = labourCareViewModel::savePartographMark,
                                    signedInUserId = userUid,
                                    onCorrection = labourCareViewModel::recordCorrection,
                                    onSessionChange = labourCareViewModel::setSessionField,
                                    onCellChange = labourCareViewModel::setCell
                                )
                            }
                        },

                    )
                }
                }
                if (privacyGateRequired) {
                    PrivacyAccountGate(privacyState, privacyViewModel::reviewAgain, privacyViewModel::refresh,
                        onHistory = { privacyViewModel.loadHistory(); showPrivacyHistory = true },
                        onSignOut = ::signOut, signingOut = isSigningOut)
                }
                if (authSession == AuthSessionState.Loading) {
                    Column(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Text("Restoring your sign-in…", modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }
    val decisionDialogRequired = privacyGateRequired && privacyState.userId == accountUid &&
        privacyState.gate in setOf(PrivacyGate.REQUIRED, PrivacyGate.ERROR)
    if ((decisionDialogRequired || reviewPrivacyDecision) && !showPrivacyHistory) {
        PrivacyPolicyDialog(requireDecision = true, isSaving = privacyState.isSaving,
            error = privacyState.error,
            onAccept = { privacyViewModel.decide(PrivacyDecision.ACCEPTED, reviewPrivacyDecision); reviewPrivacyDecision = false },
            onDeny = { privacyViewModel.decide(PrivacyDecision.DENIED, reviewPrivacyDecision); reviewPrivacyDecision = false },
            onDismiss = {},
            onExit = ::signOut,
            onHistory = { privacyViewModel.loadHistory(); showPrivacyHistory = true },
            onCancelReview = if (reviewPrivacyDecision) ({ reviewPrivacyDecision = false }) else null)
    } else if (showPrivacy) {
        PrivacyPolicyDialog(requireDecision = false, isSaving = false, error = null,
            onAccept = {}, onDeny = {}, onDismiss = { showPrivacy = false })
    }
    if (showPrivacyHistory && accountUid != null) {
        PrivacyHistoryDialog(privacyState, privacyViewModel::loadHistory, onDismiss = { showPrivacyHistory = false })
    }
}

@Composable
private fun LoginScreen(
    isLoading: Boolean,
    onLogin: (email: String, password: String) -> Unit,
    onResetPassword: (email: String) -> Unit,
    onSignUp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MgLoginForm(
            appName = APP_NAME,
            onLogin = onLogin,
            onResetPassword = onResetPassword,
            onSignUp = onSignUp,
            isLoading = isLoading,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun SignUpScreen(
    isLoading: Boolean,
    onCreateAccount: (email: String, password: String, confirmPassword: String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MgSignUpForm(
            appName = APP_NAME,
            isLoading = isLoading,
            onCreateAccount = onCreateAccount,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun EmailVerificationScreen(
    isLoading: Boolean,
    onResend: () -> Unit,
    onCheckVerification: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MgTextStartAligned(
            text = "Verify your email",
            basicFontSize = 28.sp,
            isBold = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Text("Open the verification link sent to your email. JananiSync data stays locked until your email is verified.")
        Spacer(Modifier.height(24.dp))
        MgButton(
            onClick = onCheckVerification,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            basicFontSize0 = LocalAppearanceSettings.current.basicFontSize0,
            text = if (isLoading) "Checking…" else "I've verified my email"
        )
        Spacer(Modifier.height(8.dp))
        MgButton(
            onClick = onResend,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            basicFontSize0 = LocalAppearanceSettings.current.basicFontSize0,
            text = "Resend verification email"
        )
    }
}

expect fun getPlatform()

@Composable
internal fun AccountMenu(
    membership: UserMembershipState,
    fallbackName: String,
    isSigningOut: Boolean,
    onSignOut: () -> Unit,
    onJoinAnotherOrganization: () -> Unit,
    onSwitchOrganization: () -> Unit,
    isLeavingOrganization: Boolean,
    maintenance: `in`.drarunbabu.drsharmila.jananisync.data.model.OrganizationMaintenance,
    onLeaveOrganization: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorHeight by remember { mutableStateOf(0) }
    val member = membership as? UserMembershipState.ActiveMember
    val accountName = member?.name?.ifBlank { fallbackName } ?: fallbackName
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bubbleShape = remember(density) {
        GenericShape { size, _ ->
            val radius = with(density) { 12.dp.toPx() }
            val tail = with(density) { 10.dp.toPx() }
            val pointer = size.width - with(density) { 32.dp.toPx() }
            moveTo(radius, tail)
            lineTo(pointer - tail, tail)
            lineTo(pointer, 0f)
            lineTo(pointer + tail, tail)
            lineTo(size.width - radius, tail)
            quadraticTo(size.width, tail, size.width, tail + radius)
            lineTo(size.width, size.height - radius)
            quadraticTo(size.width, size.height, size.width - radius, size.height)
            lineTo(radius, size.height)
            quadraticTo(0f, size.height, 0f, size.height - radius)
            lineTo(0f, tail + radius)
            quadraticTo(0f, tail, radius, tail)
            close()
        }
    }
    Box(Modifier.padding(end = 8.dp).onSizeChanged { anchorHeight = it.height }) {
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.widthIn(max = if (LocalAppWindowSize.current == AppWindowSize.Compact) 100.dp else 200.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(accountName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, anchorHeight),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    modifier = Modifier.width(320.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                    shape = bubbleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(accountName, style = MaterialTheme.typography.titleMedium)
                        if (member != null) {
                            Text("Organisation: ${member.organizationName}")
                            Text("Role: ${member.role.uppercase()}")
                            AccessLevelText("Organisation access level: ${member.accessLevel}")
                            Text("Your membership is active.")
                            if (member.availableOrganizations.size > 1) {
                                TextButton(
                                    onClick = {
                                        expanded = false
                                        onSwitchOrganization()
                                    },
                                    enabled = !isSigningOut && !isLeavingOrganization,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Switch organisation")
                                }
                            }
                            val maintenanceActive = maintenance.orgId == member.orgId && maintenance.active
                            LeaveOrganizationAction(
                                orgId = member.orgId,
                                name = member.organizationName,
                                role = member.role,
                                busy = isLeavingOrganization,
                                enabled = !isSigningOut && (!maintenanceActive || maintenance.awaitingAdmin),
                                onLeave = { onLeaveOrganization(member.orgId) }
                            )
                            TextButton(
                                onClick = {
                                    expanded = false
                                    onJoinAnotherOrganization()
                                },
                                enabled = !isSigningOut,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Join another organisation")
                            }
                        }
                        MgButton(
                            onClick = onSignOut,
                            enabled = !isSigningOut,
                            modifier = Modifier.fillMaxWidth(),
                            text = if (isSigningOut) "Logging out…" else "Logout",
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828), contentColor = Color.White)
                        )
                    }
                }
            }
        }
    }
}
