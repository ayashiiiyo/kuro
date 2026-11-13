package com.ayashii.kuro

import android.Manifest
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.ayashii.kuro.ui.theme.KuroTheme
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.ui.theme.rememberDynamicDominantColor
import com.ayashii.kuro.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        val language = try {
            com.ayashii.kuro.data.settings.SettingsManager.init(newBase).language.value.code
        } catch (_: Exception) {
            ""
        }
        if (language.isEmpty()) {
            super.attachBaseContext(newBase)
            return
        }
        val locale = java.util.Locale(language)
        java.util.Locale.setDefault(locale)
        val configuration = android.content.res.Configuration(newBase.resources.configuration)
        configuration.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
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
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

    var hasPlayedIntro by rememberSaveable { mutableStateOf(false) }
    val contentAlpha = remember { Animatable(if (hasPlayedIntro) 1f else 0f) }
    val contentScale = remember { Animatable(if (hasPlayedIntro) 1f else 0.965f) }
    val dockOffsetY = remember { Animatable(if (hasPlayedIntro) 0f else 90f) }
    val introOverlayAlpha = remember { Animatable(if (hasPlayedIntro) 0f else 1f) }
    val introMarkProgress = remember { Animatable(if (hasPlayedIntro) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!hasPlayedIntro) {
            launch {
                introMarkProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
                )
            }
            delay(220L)
            launch {
                contentAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing)
                )
            }
            launch {
                contentScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            launch {
                dockOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            introOverlayAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
            hasPlayedIntro = true
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

    BackHandler(enabled = isExpanded) {
        viewModel.expandPlayer(false)
    }

    val colors = KuroAppTheme.colors

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Scaffold(
            containerColor = colors.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    enterTransition = {
                        fadeIn(animationSpec = tween(180)) +
                            scaleIn(initialScale = 0.97f, animationSpec = tween(180, easing = FastOutSlowInEasing))
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(130))
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = contentAlpha.value
                            scaleX = contentScale.value
                            scaleY = contentScale.value
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

                if (!isExpanded) {
                    KuroUnifiedBottomDock(
                        viewModel = viewModel,
                        currentRoute = currentRoute,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        isLoading = isLoadingTrack,
                        onNavigate = { route ->
                            if (currentRoute != route) {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .graphicsLayer {
                                translationY = dockOffsetY.value
                                alpha = contentAlpha.value
                            }
                    )
                }
            }
        }

        if (introOverlayAlpha.value > 0.01f) {
            KuroLaunchRevealOverlay(
                progress = introMarkProgress.value,
                overlayAlpha = introOverlayAlpha.value
            )
        }

        AnimatedVisibility(
            visible = isExpanded && currentTrack != null,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            )
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
private fun KuroLaunchRevealOverlay(
    progress: Float,
    overlayAlpha: Float
) {
    val colors = KuroAppTheme.colors
    val markColor = colors.textPrimary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha }
            .background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                val s = 0.88f + (progress * 0.12f) + ((1f - overlayAlpha) * 0.06f)
                scaleX = s
                scaleY = s
            }
        ) {
            Canvas(modifier = Modifier.size(64.dp)) {
                val strokeW = 3.2.dp.toPx()
                val sweep = 290f * progress
                drawArc(
                    color = markColor,
                    startAngle = -55f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )

                val cx = size.width * 0.5f
                val cy = size.height * 0.5f
                val barHeights = listOf(0.28f, 0.52f, 0.38f)
                val barOffsets = listOf(-10.dp.toPx(), 0f, 10.dp.toPx())
                barHeights.forEachIndexed { idx, hRatio ->
                    val halfH = (size.height * hRatio * 0.5f) * progress
                    val x = cx + barOffsets[idx]
                    drawLine(
                        color = markColor,
                        start = Offset(x, cy - halfH),
                        end = Offset(x, cy + halfH),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "KURO",
                style = Typography.titleLarge.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (4f + (1f - progress) * 4f).sp
                ),
                color = colors.textPrimary,
                modifier = Modifier.graphicsLayer { alpha = progress }
            )
        }
    }
}

@Composable
fun KuroUnifiedBottomDock(
    viewModel: MusicViewModel,
    currentRoute: String?,
    currentTrack: Track?,
    isPlaying: Boolean,
    isLoading: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KuroAppTheme.colors
    val dominantColor = rememberDynamicDominantColor(currentTrack?.thumbnailUrlForDisplay(144))

    val dockBrush = remember(dominantColor, colors.card, colors.surface, currentTrack != null, colors.isDark) {
        if (currentTrack != null) {
            val topTint = androidx.compose.ui.graphics.lerp(
                colors.card,
                dominantColor,
                if (colors.isDark) 0.22f else 0.10f
            )
            Brush.verticalGradient(listOf(topTint, colors.surface))
        } else {
            Brush.verticalGradient(listOf(colors.card, colors.surface))
        }
    }

    val borderColor = remember(dominantColor, colors.border, currentTrack != null) {
        if (currentTrack != null) {
            androidx.compose.ui.graphics.lerp(colors.border, dominantColor, 0.28f)
        } else {
            colors.border
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(dockBrush)
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
    ) {
        AnimatedVisibility(
            visible = currentTrack != null,
            enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(180)),
            exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(tween(140))
        ) {
            currentTrack?.let { track ->
                MiniPlayerContainer(
                    viewModel = viewModel,
                    track = track,
                    isPlaying = isPlaying,
                    isLoading = isLoading
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Screen.bottomNavItems.forEach { screen ->
                val selected = currentRoute == screen.route
                val itemScale by animateFloatAsState(
                    targetValue = if (selected) 1.06f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "dock_tab_scale"
                )

                val pillBg = if (selected) colors.accent.copy(alpha = if (colors.isDark) 0.14f else 0.10f) else androidx.compose.ui.graphics.Color.Transparent

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(pillBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onNavigate(screen.route) }
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .graphicsLayer {
                            scaleX = itemScale
                            scaleY = itemScale
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = stringResource(screen.titleRes),
                        tint = if (selected) colors.textPrimary else colors.textMuted,
                        modifier = Modifier.size(21.dp)
                    )
                    AnimatedVisibility(visible = selected) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(screen.titleRes),
                                style = Typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colors.textPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
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
        progressProvider = { viewModel.getProgress() },
        onPlayPauseClick = { viewModel.togglePlayPause() },
        onSkipNextClick = { viewModel.skipNext() },
        onSkipPreviousClick = { viewModel.skipPrevious() },
        onFavoriteClick = { viewModel.toggleFavorite(track) },
        onClick = { viewModel.expandPlayer(true) },
        modifier = modifier
    )
}
