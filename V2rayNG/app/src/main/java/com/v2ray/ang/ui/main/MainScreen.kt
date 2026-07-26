package com.v2ray.ang.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.compose.LocalDarkTheme
import com.v2ray.ang.compose.QRCodeDialog
import com.v2ray.ang.dto.entities.ProfileItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (String) -> Unit,
    shareMethodEntries: List<String>,
    shareMethodMoreEntries: List<String>
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isRunning = uiState.isRunning
    val displayText = uiState.statusText
    val selectedGuid = uiState.selectedGuid
    val doubleColumnDisplay = uiState.doubleColumnDisplay
    val confirmRemove = uiState.confirmRemove
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap

    val isDarkTheme = LocalDarkTheme.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf<String?>(null) }

    var shareTarget by remember { mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null) }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { groups.size.coerceAtLeast(1) }
    )

    val lazyListStates = remember { mutableStateMapOf<String, LazyListState>() }
    val lazyGridStates = remember { mutableStateMapOf<String, LazyGridState>() }

    var locateInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(groups) {
        val validGroupIds = groups.map { it.id }.toSet()
        lazyListStates.keys.retainAll(validGroupIds)
        lazyGridStates.keys.retainAll(validGroupIds)
    }

    val latestDoubleColumnDisplay by rememberUpdatedState(doubleColumnDisplay)

    LaunchedEffect(groups, uiState.selectedGroupId) {
        if (groups.isEmpty()) return@LaunchedEffect
        val selectedIndex = groups.indexOfFirst { it.id == uiState.selectedGroupId }
            .takeIf { it >= 0 } ?: 0
        if (!pagerState.isScrollInProgress && pagerState.settledPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    val latestGroups by rememberUpdatedState(groups)
    val latestLocateInProgress by rememberUpdatedState(locateInProgress)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val currentGroups = latestGroups
                if (!latestLocateInProgress && page in currentGroups.indices) {
                    onAction(MainAction.SelectGroup(currentGroups[page].id))
                }
            }
    }

    LaunchedEffect(uiState.locateTarget) {
        val target = uiState.locateTarget ?: return@LaunchedEffect
        if (target.groupIndex !in 0 until pagerState.pageCount) {
            mainViewModel.onAction(MainAction.LocateHandled(target))
            return@LaunchedEffect
        }

        locateInProgress = true
        try {
            if (pagerState.settledPage != target.groupIndex) {
                pagerState.navigateToPageOptimized(
                    targetPage = target.groupIndex,
                    animateAdjacentPage = false
                )
            }
            onAction(MainAction.SelectGroup(target.groupId))

            repeat(10) {
                val ready = if (latestDoubleColumnDisplay) {
                    lazyGridStates[target.groupId] != null
                } else {
                    lazyListStates[target.groupId] != null
                }
                if (ready) return@repeat
                delay(16L)
            }

            if (latestDoubleColumnDisplay) {
                lazyGridStates[target.groupId]?.let { gridState ->
                    gridState.scrollToItem(
                        index = target.itemPosition,
                        scrollOffset = -gridState.layoutInfo.viewportSize.height / 3
                    )
                }
            } else {
                lazyListStates[target.groupId]?.let { listState ->
                    listState.scrollToItem(
                        index = target.itemPosition,
                        scrollOffset = -listState.layoutInfo.viewportSize.height / 3
                    )
                }
            }
        } finally {
            delay(32L)
            locateInProgress = false
            mainViewModel.onAction(MainAction.LocateHandled(target))
        }
    }

    MainDialogs(
        showDelAllConfirm = showDelAllConfirm,
        onDismissDelAll = { showDelAllConfirm = false },
        onConfirmDelAll = { showDelAllConfirm = false; onAction(MainAction.RemoveAllServers) },
        showDelDuplicateConfirm = showDelDuplicateConfirm,
        onDismissDelDuplicate = { showDelDuplicateConfirm = false },
        onConfirmDelDuplicate = { showDelDuplicateConfirm = false; onAction(MainAction.RemoveDuplicateServers) },
        showDelInvalidConfirm = showDelInvalidConfirm,
        onDismissDelInvalid = { showDelInvalidConfirm = false },
        onConfirmDelInvalid = { showDelInvalidConfirm = false; onAction(MainAction.RemoveInvalidServers) },
        showRemoveConfirm = showRemoveConfirm,
        onDismissRemove = { showRemoveConfirm = null },
        onConfirmRemove = { guid -> showRemoveConfirm = null; onAction(MainAction.RemoveServer(guid)) }
    )

    if (shareTarget != null) {
        val (guid, profile, more) = shareTarget!!
        ShareMethodDialog(
            guid = guid,
            profile = profile,
            more = more,
            shareMethodEntries = shareMethodEntries,
            shareMethodMoreEntries = shareMethodMoreEntries,
            onDismiss = { shareTarget = null },
            onAction = onAction
        )
    }
    if (shareQRCodeBitmap != null) {
        QRCodeDialog(bitmap = shareQRCodeBitmap, onDismiss = { onAction(MainAction.DismissQRCodeDialog) })
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawerContent(
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    onNavigate(route)
                }
            )
        }
    ) {
        Scaffold(
            containerColor = Color.Black, // АБСОЛЮТНО ЧЕРНЫЙ ФОН OLED
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            topBar = {
                MainTopBar(
                    isLoading = isLoading,
                    showSearch = showSearch,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { query: String ->
                        searchQuery = query
                        onAction(MainAction.Search(query))
                    },
                    onSearchClose = {
                        searchQuery = ""
                        onAction(MainAction.Search(""))
                        showSearch = false
                    },
                    onSearchToggle = { show: Boolean -> showSearch = show },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onAction = onAction,
                    onDelAllConfig = { showDelAllConfirm = true },
                    onDelDuplicateConfig = { showDelDuplicateConfirm = true },
                    onDelInvalidConfig = { showDelInvalidConfirm = true }
                )
            },
            bottomBar = {
                MainBottomBar(
                    displayText = displayText,
                    isRunning = isRunning,
                    isDarkTheme = isDarkTheme,
                    onAction = onAction
                )
            },
            floatingActionButton = {}, // Оставляем пустым, кнопка теперь по центру
        ) { innerPadding ->
            val layoutDirection = LocalLayoutDirection.current

            if (groups.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(Color.Black) // Подложка для кибер-стиля
                ) {
                    if (groups.size > 1) {
                        GroupTabBar(
                            groups = groups,
                            selectedTabIndex = pagerState.currentPage.coerceIn(0, groups.lastIndex),
                            mainViewModel = mainViewModel,
                            onTabClick = { targetIndex ->
                                scope.launch {
                                    pagerState.navigateToPageOptimized(
                                        targetPage = targetIndex,
                                        animateAdjacentPage = true
                                    )
                                }
                            }
                        )
                    }

                    // --- ФУТУРИСТИЧНАЯ КНОПКА ЗАПУСКА ---
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.35f), // Занимает 35% верхней части экрана
                        contentAlignment = Alignment.Center
                    ) {
                        CyberPowerButton(
                            isRunning = isRunning,
                            onClick = { onAction(MainAction.ToggleService) }
                        )
                    }

                    // --- СПИСОК СЕРВЕРОВ ---
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(0.65f), // Списки занимают нижние 65% экрана
                        userScrollEnabled = true,
                        beyondViewportPageCount = 1,
                        key = { page -> groups.getOrNull(page)?.id ?: "group-page-$page" }
                    ) { page ->
                        val group = groups.getOrNull(page) ?: return@HorizontalPager

                        GroupPagerPage(
                            groupId = group.id,
                            mainViewModel = mainViewModel,
                            selectedGuid = selectedGuid,
                            doubleColumnDisplay = doubleColumnDisplay,
                            confirmRemove = confirmRemove,
                            searchQuery = searchQuery,
                            lazyListStates = lazyListStates,
                            lazyGridStates = lazyGridStates,
                            onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) },
                            onEditServer = { guid, profile -> onAction(MainAction.EditServer(guid, profile)) },
                            onShareServer = { guid, profile ->
                                shareTarget = Triple(guid, profile, false)
                            },
                            onMoreServer = { guid, profile ->
                                shareTarget = Triple(guid, profile, true)
                            },
                            onRemoveServer = { guid ->
                                if (confirmRemove) showRemoveConfirm = guid
                                else onAction(MainAction.RemoveServer(guid))
                            },
                            contentPadding = PaddingValues(
                                start = 0.dp,
                                top = 0.dp,
                                end = 0.dp,
                                bottom = 80.dp
                            )
                        )
                    }
                }
            }
        }
    }
}

// --- НОВЫЙ КОМПОНЕНТ ДЛЯ КИБЕР-КНОПКИ ---
@Composable
fun CyberPowerButton(
    isRunning: Boolean,
    onClick: () -> Unit
) {
    // Анимация пульсации (дыхание)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Цвета: Выключено (Темно-серый) / Включено (Кибер-Циан)
    val neonColor by animateColorAsState(
        targetValue = if (isRunning) Color(0xFF00E5FF) else Color(0xFF333333),
        animationSpec = tween(500),
        label = "color"
    )
    
    // Прозрачность свечения
    val glowAlpha by animateFloatAsState(
        targetValue = if (isRunning) 0.35f else 0f,
        animationSpec = tween(500),
        label = "glow"
    )

    val scaleModifier = if (isRunning) Modifier.scale(pulseScale) else Modifier

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Внешнее радиальное свечение (Glow Effect)
        Box(
            modifier = Modifier
                .size(200.dp)
                .then(scaleModifier)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(neonColor.copy(alpha = glowAlpha), Color.Transparent)
                    )
                )
        )

        // Физическая кнопка (Матовый темный круг)
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color(0xFF121212)) // Почти черный для контраста
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, // Убираем стандартный андроид-ripple, оставляем чистоту
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            // Отрисовка значка Power в векторе (Canvas)
            Canvas(modifier = Modifier.size(46.dp)) {
                // Кольцо Power
                drawArc(
                    color = neonColor,
                    startAngle = -240f,
                    sweepAngle = 300f,
                    useCenter = false,
                    style = Stroke(width = 8f, cap = StrokeCap.Round)
                )
                // Линия Power
                drawLine(
                    color = neonColor,
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height / 2.5f),
                    strokeWidth = 8f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
