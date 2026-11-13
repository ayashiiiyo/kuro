package com.ayashii.kuro

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.data.update.UpdateInfo
import com.ayashii.kuro.data.update.UpdateManager
import com.ayashii.kuro.ui.components.FullPlayerSheet
import com.ayashii.kuro.ui.components.MiniPlayer
import com.ayashii.kuro.ui.components.UpdateDialog
import com.ayashii.kuro.ui.navigation.Screen
import com.ayashii.kuro.ui.screens.HomeScreen
import com.ayashii.kuro.ui.screens.LibraryScreen
import com.ayashii.kuro.ui.screens.SearchScreen
import com.ayashii.kuro.ui.screens.VibeScreen
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.KuroMotion
import com.ayashii.kuro.ui.theme.KuroTheme
import com.ayashii.kuro.viewmodel.MusicViewModel

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /**
     * AppCompat's per-app locales only reach AppCompatActivity on Android 12 and below.
     * Since Kuro uses ComponentActivity (platform theme), apply the saved language here
     * without accessing applicationContext before attachment.
     */
    override fun attachBaseContext(newBase: Context) {
        val language = try {
            newBase.getSharedPreferences("kuro_settings", Context.MODE_PRIVATE)
                .getString("app_language", "") ?: ""
        } catch (_: Throwable) {
            ""
        }
        if (language.isEmpty()) {
            super.attachBaseContext(newBase)
            return
        }
        val wrappedContext = try {
            val locale = if (language == "in" || language == "id") {
                java.util.Locale("in", "ID")
            } else {
                java.util.Locale(language)
            }
            java.util.Locale.setDefault(locale)
            val configuration = android.content.res.Configuration(newBase.resources.configuration)
            configuration.setLocale(locale)
            newBase.createConfigurationContext(configuration)
        } catch (_: Throwable) {
            newBase
        }
        super.attachBaseContext(wrappedContext)
    }

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } catch (_: Throwable) {}
        }

        if (intent?.getBooleanExtra(EXTRA_EXPAND_PLAYER, false) == true) {
            viewModel.expandPlayer(true)
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            KuroTheme(themeMode = themeMode) {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_EXPAND_PLAYER, false)) {
            viewModel.expandPlayer(true)
        }
    }

    companion object {
        const val EXTRA_EXPAND_PLAYER = "EXTRA_EXPAND_PLAYER"
    }
}

@Composable
fun MainAppScreen(viewModel: MusicViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLoadingTrack by viewModel.isLoadingTrack.collectAsState()
    val isExpanded by viewModel.isPlayerExpanded.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var navSwipeOffsetX by remember { mutableFloatStateOf(0f) }

    val navigateToTab: (String) -> Unit = { targetRoute ->
        if (currentRoute != targetRoute) {
            navController.navigate(targetRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!UpdateManager.hasCheckedThisSession) {
            UpdateManager.hasCheckedThisSession = true
            val update = UpdateManager.getInstance(context).checkForUpdate(force = false)
            if (update != null) {
                availableUpdate = update
            }
        }
    }

    val colors = KuroAppTheme.colors

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Scaffold(
            containerColor = colors.background,
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.35f to colors.background.copy(alpha = 0.84f),
                                1.0f to colors.background
                            )
                        )
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp)
                ) {
                    val pillShape = RoundedCornerShape(28.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .shadow(elevation = 14.dp, shape = pillShape, clip = false)
                            .clip(pillShape)
                            .background(colors.surface.copy(alpha = 0.94f))
                            .border(1.dp, colors.border.copy(alpha = 0.65f), pillShape)
                            .pointerInput(currentRoute) {
                                detectHorizontalDragGestures(
                                    onDragStart = { navSwipeOffsetX = 0f },
                                    onDragEnd = {
                                        val items = Screen.bottomNavItems
                                        val currentIdx = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
                                        if (navSwipeOffsetX < -64f && currentIdx + 1 < items.size) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            navigateToTab(items[currentIdx + 1].route)
                                        } else if (navSwipeOffsetX > 64f && currentIdx - 1 >= 0) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            navigateToTab(items[currentIdx - 1].route)
                                        }
                                        navSwipeOffsetX = 0f
                                    },
                                    onDragCancel = { navSwipeOffsetX = 0f },
                                    onHorizontalDrag = { change, dragAmount ->
                                        navSwipeOffsetX += dragAmount
                                        change.consume()
                                    }
                                )
                            }
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Screen.bottomNavItems.forEach { screen ->
                            val selected = currentRoute == screen.route
                            val itemAlpha by animateFloatAsState(
                                targetValue = if (selected) 1f else 0.56f,
                                animationSpec = tween(durationMillis = 240, easing = KuroMotion.EmphasizedDecelerate),
                                label = "nav_item_alpha"
                            )
                            val itemScale by animateFloatAsState(
                                targetValue = if (selected) 1.04f else 0.96f,
                                animationSpec = tween(durationMillis = 240, easing = KuroMotion.EmphasizedDecelerate),
                                label = "nav_item_scale"
                            )
                            val interactionSource = remember { MutableInteractionSource() }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(if (selected) colors.card.copy(alpha = 0.9f) else Color.Transparent)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                    ) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        navigateToTab(screen.route)
                                    }
                                    .padding(vertical = 7.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = stringResource(screen.titleRes),
                                    tint = if (selected) colors.textPrimary else colors.textMuted,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .graphicsLayer {
                                            alpha = itemAlpha
                                            scaleX = itemScale
                                            scaleY = itemScale
                                        }
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(screen.titleRes),
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) colors.textPrimary else colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.graphicsLayer {
                                        alpha = itemAlpha
                                    }
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.0f to Color.White,
                                    0.95f to Color.White,
                                    1.0f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        },
                    enterTransition = {
                        fadeIn(
                            animationSpec = tween(300, easing = KuroMotion.EmphasizedDecelerate)
                        ) + scaleIn(
                            initialScale = 0.988f,
                            animationSpec = tween(300, easing = KuroMotion.EmphasizedDecelerate)
                        )
                    },
                    exitTransition = {
                        fadeOut(
                            animationSpec = tween(180, easing = KuroMotion.EmphasizedAccelerate)
                        ) + scaleOut(
                            targetScale = 0.992f,
                            animationSpec = tween(180, easing = KuroMotion.EmphasizedAccelerate)
                        )
                    },
                    popEnterTransition = {
                        fadeIn(
                            animationSpec = tween(300, easing = KuroMotion.EmphasizedDecelerate)
                        ) + scaleIn(
                            initialScale = 0.988f,
                            animationSpec = tween(300, easing = KuroMotion.EmphasizedDecelerate)
                        )
                    },
                    popExitTransition = {
                        fadeOut(
                            animationSpec = tween(180, easing = KuroMotion.EmphasizedAccelerate)
                        ) + scaleOut(
                            targetScale = 0.992f,
                            animationSpec = tween(180, easing = KuroMotion.EmphasizedAccelerate)
                        )
                    }
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(viewModel = viewModel)
                    }
                    composable(Screen.Search.route) {
                        SearchScreen(viewModel = viewModel)
                    }
                    composable(Screen.Vibe.route) {
                        VibeScreen(viewModel = viewModel)
                    }
                    composable(Screen.Library.route) {
                        LibraryScreen(viewModel = viewModel)
                    }
                }

                AnimatedVisibility(
                    visible = currentTrack != null && !isExpanded,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(
                        animationSpec = tween(260, easing = KuroMotion.EmphasizedDecelerate),
                        initialOffsetY = { it }
                    ) + fadeIn(tween(220)),
                    exit = slideOutVertically(
                        animationSpec = tween(220, easing = KuroMotion.EmphasizedAccelerate),
                        targetOffsetY = { it }
                    ) + fadeOut(tween(180))
                ) {
                    currentTrack?.let { activeTrack ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            MiniPlayerContainer(
                                viewModel = viewModel,
                                track = activeTrack,
                                isPlaying = isPlaying,
                                isLoading = isLoadingTrack
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded && currentTrack != null,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 320, easing = KuroMotion.EmphasizedDecelerate),
                initialOffsetY = { it }
            ) + fadeIn(animationSpec = tween(220, easing = KuroMotion.EmphasizedDecelerate)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 220, easing = KuroMotion.EmphasizedDecelerate),
                targetOffsetY = { it }
            ) + fadeOut(animationSpec = tween(180, easing = KuroMotion.EmphasizedDecelerate))
        ) {
            currentTrack?.let { track ->
                FullPlayerSheet(
                    track = track,
                    viewModel = viewModel,
                    isPlaying = isPlaying,
                    isLoading = isLoadingTrack,
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onSkipNextClick = { viewModel.skipNext() },
                    onSkipPreviousClick = { viewModel.skipPrevious() },
                    onShuffleClick = { viewModel.toggleShuffle() },
                    onRepeatClick = { viewModel.toggleRepeat() },
                    onFavoriteClick = { viewModel.toggleFavorite(track) },
                    onDismiss = { viewModel.expandPlayer(false) }
                )
            }
        }

        /**
         * Declared after Scaffold and NavHost so LIFO BackHandler dispatch
         * always collapses FullPlayerSheet before any child screen intercepts Back.
         */
        BackHandler(enabled = isExpanded) {
            viewModel.expandPlayer(false)
        }

        if (availableUpdate != null) {
            UpdateDialog(
                updateInfo = availableUpdate!!,
                onDownloadClick = {
                    UpdateManager.getInstance(context).startDownloadAndInstall(availableUpdate!!)
                    availableUpdate = null
                },
                onDismiss = {
                    availableUpdate = null
                }
            )
        }
    }
}

@Composable
fun MiniPlayerContainer(
    viewModel: MusicViewModel,
    track: Track,
    isPlaying: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val currentPosMs by viewModel.playbackPosition.collectAsState()
    val durMs by viewModel.duration.collectAsState()
    val effDur = if (durMs > 0) durMs else (track.durationSeconds * 1000L).coerceAtLeast(1L)
    val progressFloat = (currentPosMs.toFloat() / effDur.toFloat()).coerceIn(0f, 1f)

    MiniPlayer(
        track = track,
        isPlaying = isPlaying,
        isLoading = isLoading,
        progress = progressFloat,
        onPlayPauseClick = { viewModel.togglePlayPause() },
        onSkipNextClick = { viewModel.skipNext() },
        onSkipPreviousClick = { viewModel.skipPrevious() },
        onFavoriteClick = { viewModel.toggleFavorite(track) },
        onSwipeDown = {
            if (isPlaying) {
                viewModel.togglePlayPause()
            }
        },
        onClick = { viewModel.expandPlayer(true) },
        modifier = modifier
    )
}
