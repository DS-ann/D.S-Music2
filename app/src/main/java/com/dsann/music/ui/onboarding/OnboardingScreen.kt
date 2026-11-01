@file:Suppress("DEPRECATION")

package com.dsann.music.ui.onboarding
    
    import android.Manifest
    import android.annotation.SuppressLint
    import android.content.Context
    import android.content.pm.PackageManager
    import android.os.Build
    import androidx.activity.compose.rememberLauncherForActivityResult
    import androidx.activity.result.contract.ActivityResultContracts
    import androidx.compose.animation.AnimatedContent
    import androidx.compose.animation.SizeTransform
    import androidx.compose.animation.core.FastOutSlowInEasing
    import androidx.compose.animation.core.Spring
    import androidx.compose.animation.core.animateFloat
    import androidx.compose.animation.core.animateFloatAsState
    import androidx.compose.animation.core.spring
    import androidx.compose.animation.core.tween
    import androidx.compose.animation.fadeIn
    import androidx.compose.animation.fadeOut
    import androidx.compose.animation.slideInVertically
    import androidx.compose.animation.slideOutVertically
    import androidx.compose.animation.togetherWith
    import androidx.compose.foundation.ExperimentalFoundationApi
    import androidx.compose.foundation.Image
    import androidx.compose.foundation.background
    import androidx.compose.foundation.layout.Arrangement
    import androidx.compose.foundation.layout.Box
    import androidx.compose.foundation.layout.Column
    import androidx.compose.foundation.layout.Row
    import androidx.compose.foundation.layout.Spacer
    import androidx.compose.foundation.layout.aspectRatio
    import androidx.compose.foundation.layout.fillMaxSize
    import androidx.compose.foundation.layout.fillMaxWidth
    import androidx.compose.foundation.layout.height
    import androidx.compose.foundation.layout.heightIn
    import androidx.compose.foundation.layout.navigationBarsPadding
    import androidx.compose.foundation.layout.padding
    import androidx.compose.foundation.layout.size
    import androidx.compose.foundation.layout.width
    import androidx.compose.foundation.pager.HorizontalPager
    import androidx.compose.foundation.pager.PagerState
    import androidx.compose.foundation.pager.rememberPagerState
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.filled.ArrowBack
    import androidx.compose.material.icons.filled.ArrowForward
    import androidx.compose.material.icons.filled.Check
    import androidx.compose.material.icons.filled.CheckCircle
    import androidx.compose.material.icons.filled.DarkMode
    import androidx.compose.material.icons.filled.Home
    import androidx.compose.material.icons.filled.LibraryMusic
    import androidx.compose.material.icons.filled.LightMode
    import androidx.compose.material.icons.filled.Notifications
    import androidx.compose.material.icons.filled.Search
    import androidx.compose.material.icons.filled.Settings
    import com.dsann.music.ui.theme.Card
    import com.dsann.music.ui.theme.CardDefaults
        import com.dsann.music.ui.theme.FloatingActionButton
    import com.dsann.music.ui.theme.FloatingActionButtonDefaults
    import com.dsann.music.ui.theme.Icon
    import com.dsann.music.ui.theme.DsmusicTheme
    import com.dsann.music.ui.theme.NavigationBar
    import com.dsann.music.ui.theme.NavigationBarItem
    import com.dsann.music.ui.theme.Scaffold
    import com.dsann.music.ui.theme.Surface
    import com.dsann.music.ui.theme.Switch
    import com.dsann.music.ui.theme.Text
    import androidx.compose.runtime.Composable
    import androidx.compose.runtime.LaunchedEffect
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.remember
    import androidx.compose.runtime.rememberCoroutineScope
    import androidx.compose.runtime.saveable.rememberSaveable
    import androidx.compose.runtime.setValue
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.draw.rotate
    import androidx.compose.ui.draw.shadow
    import androidx.compose.ui.graphics.graphicsLayer
    import androidx.compose.ui.layout.ContentScale
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.res.painterResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.core.content.ContextCompat
    import com.dsann.music.R
    import kotlinx.coroutines.launch
    import androidx.core.content.edit

@OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun OnboardingScreen(
        onComplete: () -> Unit
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val pagerState = rememberPagerState(pageCount = { 8 })
        val prefs = remember { context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }
        
        var isLoggedIn by remember { mutableStateOf(com.dsann.music.data.innertube.AccountManager.isLoggedIn(context)) }
        var userName by remember { mutableStateOf("Connected User") }
        var userProfileUrl by remember { mutableStateOf<String?>(null) }
        
        LaunchedEffect(isLoggedIn) {
            if (isLoggedIn) {
                val result = com.dsann.music.data.innertube.YouTubeMusic.accountProfile()
                userName = result.getOrNull()?.name ?: "Connected User"
                userProfileUrl = result.getOrNull()?.avatarUrl
            }
        }
        
        var themeMode by rememberSaveable { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
        var hideNavLabels by rememberSaveable { mutableStateOf(prefs.getBoolean("hide_nav_labels", false)) }
        var dynamicNavStyle by rememberSaveable { mutableStateOf(prefs.getBoolean("dynamic_nav_style", false)) }
    
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        val notifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
    
        var audioGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) }
        var notifGranted by remember { mutableStateOf(notifPermission?.let { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } ?: true) }
        
        val allPermissionsGranted = audioGranted && notifGranted
    
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            audioGranted = permissions[audioPermission] ?: audioGranted
            if (notifPermission != null) {
                notifGranted = permissions[notifPermission] ?: notifGranted
            }
        }
    
        Scaffold(
            bottomBar = {
                val isNextEnabled = if (pagerState.currentPage == 4) allPermissionsGranted else true
                SetupBottomBar(
                    pagerState = pagerState,
                    isNextEnabled = isNextEnabled,
                    onNextClicked = {
                        if (isNextEnabled && pagerState.currentPage < pagerState.pageCount - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage + 1,
                                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                                )
                            }
                        }
                    },
                    onBackClicked = {
                        if (pagerState.currentPage > 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage - 1,
                                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                                )
                            }
                        }
                    },
                    onFinishClicked = {
                        prefs.edit {
                            putBoolean("has_seen_onboarding_1_8", true)
                                .putString("theme_mode", themeMode)
                                .putBoolean("hide_nav_labels", hideNavLabels)
                                .putBoolean("dynamic_nav_style", dynamicNavStyle)
                        }
                        onComplete()
                    }
                )
            }
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) { page ->
                val pageOffsetProvider = {
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                }
    
                Box(modifier = Modifier.fillMaxSize()) {
                    when (page) {
                        0 -> WelcomePage(pageOffsetProvider)
                        1 -> EcosystemPage(pageOffsetProvider)
                        2 -> FeatureListPage(pageOffsetProvider)
                        3 -> SignInPage(
                            pageOffsetProvider = pageOffsetProvider,
                            isLoggedIn = isLoggedIn,
                            userName = userName,
                            userProfileUrl = userProfileUrl,
                            onLoginSuccess = { isLoggedIn = true },
                            onGuestClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(4) 
                                }
                            }
                        )
                        4 -> PermissionsPage(
                            pageOffsetProvider = pageOffsetProvider,
                            audioGranted = audioGranted,
                            notifGranted = notifGranted,
                            onRequestAudio = { permissionLauncher.launch(arrayOf(audioPermission)) },
                            onRequestNotif = { notifPermission?.let { permissionLauncher.launch(arrayOf(it)) } }
                        )
                        5 -> ThemeSelectionPage(
                            pageOffsetProvider = pageOffsetProvider,
                            themeMode = themeMode,
                            onThemeChanged = { 
                                themeMode = it
                                prefs.edit { putString("theme_mode", it) }
                            }
                        )
                        6 -> NavigationStylePage(
                            pageOffsetProvider = pageOffsetProvider,
                            hideNavLabels = hideNavLabels,
                            dynamicNavStyle = dynamicNavStyle,
                            onHideNavLabelsChanged = {
                                hideNavLabels = it
                                prefs.edit { putBoolean("hide_nav_labels", it) }
                            },
                            onDynamicNavStyleChanged = {
                                dynamicNavStyle = it
                                prefs.edit { putBoolean("dynamic_nav_style", it) }
                            }
                        )
                        7 -> FinishPage(
                            pageOffsetProvider = pageOffsetProvider,
                            isLoggedIn = isLoggedIn,
                            userProfileUrl = userProfileUrl
                        )
                    }
                }
            }
        }
    }
    
    @SuppressLint("ConfigurationScreenWidthHeight")
    @Composable
    fun ImmersivePageLayout(
        pageOffsetProvider: () -> Float,
        drawableRes: Int?,
        imageUrl: String? = null,
        imageScale: Float = 1f,
        iconTint: androidx.compose.ui.graphics.Color = DsmusicTheme.colorScheme.onSurface,
        content: @Composable () -> Unit
    ) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
        val horizontalPadding = if (screenWidth < 360.dp) 20.dp else if (screenWidth > 480.dp) 32.dp else 28.dp
        val verticalPadding = if (screenWidth < 360.dp) 16.dp else if (screenWidth > 480.dp) 24.dp else 20.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Image Canvas (takes all remaining space above text)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationX = offset * 15f
                        rotationZ = offset * 2f
                        scaleX = imageScale + kotlin.math.abs(offset * 0.08f)
                        scaleY = imageScale + kotlin.math.abs(offset * 0.08f)
                    }
            ) {
                if (imageUrl != null) {
                    coil.compose.AsyncImage(
                        model = imageUrl,
                        contentDescription = "Background decoration",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(CircleShape)
                    )
                } else if (drawableRes != null) {
                    Image(
                        painter = painterResource(id = drawableRes),
                        contentDescription = "Background decoration",
                        contentScale = ContentScale.Fit,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(iconTint),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                    )
                } else {
                    // Fallback / Placeholder surface if drawable is missing
                    Surface(color = DsmusicTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {}
                }
            }
    
            // Content Area at the bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.Start
            ) {
                content()
            }
        }
    }
    
    @Composable
    fun WelcomePage(pageOffsetProvider: () -> Float) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_applogo,
            iconTint = DsmusicTheme.colorScheme.onSurface
        ) {
            Text(
                text = "Welcome\nto D.S Music.",
                style = DsmusicTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.5).sp,
                color = DsmusicTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * 30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Lets setup the everything for you",
                style = DsmusicTheme.typography.headlineSmall,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 28.sp,
                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * 50f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
    
    @Composable
    fun EcosystemPage(pageOffsetProvider: () -> Float) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_youtube_music,
            iconTint = DsmusicTheme.colorScheme.onSurface
        ) {
            Text(
                text = "Your\nComplete\nEcosystem.",
                style = DsmusicTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.5).sp,
                color = DsmusicTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Stream millions of tracks or play your local library, perfectly synced.",
                style = DsmusicTheme.typography.headlineSmall,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 28.sp,
                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -60f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
    
    @Composable
    fun FeatureListPage(pageOffsetProvider: () -> Float) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_lyrics,
            iconTint = DsmusicTheme.colorScheme.onSurface
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Stream",
                    style = DsmusicTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                    color = DsmusicTheme.colorScheme.secondary,
                    modifier = Modifier.graphicsLayer { 
                        val offset = pageOffsetProvider()
                        translationY = offset * 20f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
                Text(
                    text = "Discover",
                    style = DsmusicTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                    color = DsmusicTheme.colorScheme.tertiary,
                    modifier = Modifier.graphicsLayer { 
                        val offset = pageOffsetProvider()
                        translationY = offset * 40f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
                Text(
                    text = "Sing Along.",
                    style = DsmusicTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                    color = DsmusicTheme.colorScheme.onBackground,
                    modifier = Modifier.graphicsLayer { 
                        val offset = pageOffsetProvider()
                        translationY = offset * 60f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Karaoke lyrics, powerful search, and offline downloads all in one place.",
                style = DsmusicTheme.typography.bodyLarge,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * 80f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
    
    
    @Composable
    fun ThemeSelectionPage(
        pageOffsetProvider: () -> Float,
        themeMode: String,
        onThemeChanged: (String) -> Unit
    ) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_appearance,
            iconTint = DsmusicTheme.colorScheme.onSurface
        ) {
            Text(
                text = "Style it\nyour way.",
                style = DsmusicTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.5).sp,
                color = DsmusicTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
    
            Spacer(modifier = Modifier.height(32.dp))
    
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    Triple("system", "System Default", Icons.Default.Settings),
                    Triple("dark", "Dark Mode", Icons.Default.DarkMode),
                    Triple("light", "Light Mode", Icons.Default.LightMode)
                ).forEachIndexed { index, (mode, label, _) ->
                    Card(
                        onClick = { onThemeChanged(mode) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (themeMode == mode) DsmusicTheme.colorScheme.primaryContainer else DsmusicTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .graphicsLayer {
                                val offset = pageOffsetProvider()
                                translationY = offset * (50f + (index * 40f))
                                alpha = 1f - kotlin.math.abs(offset * 1.5f)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (themeMode == mode) DsmusicTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                if (themeMode == mode) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = DsmusicTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = label,
                                style = DsmusicTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (themeMode == mode) DsmusicTheme.colorScheme.onPrimaryContainer else DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    @Composable
    fun NavigationStylePage(
        pageOffsetProvider: () -> Float,
        hideNavLabels: Boolean,
        dynamicNavStyle: Boolean,
        onHideNavLabelsChanged: (Boolean) -> Unit,
        onDynamicNavStyleChanged: (Boolean) -> Unit
    ) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_settings,
            iconTint = DsmusicTheme.colorScheme.onSurface
        ) {
            Text(
                text = "Navigate\nSeamlessly.",
                style = DsmusicTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.5).sp,
                color = DsmusicTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
    
            Spacer(modifier = Modifier.height(48.dp))
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationY = offset * 25f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    },
                shape = RoundedCornerShape(24.dp),
                color = DsmusicTheme.colorScheme.surfaceContainer
            ) {
                NavigationBar(
                    modifier = Modifier.fillMaxWidth().height(if (dynamicNavStyle) 64.dp else 80.dp),
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    tonalElevation = 0.dp,
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp)
                ) {
                    listOf("Home" to Icons.Default.Home, "Search" to Icons.Default.Search, "Library" to Icons.Default.LibraryMusic).forEachIndexed { index, item ->
                        val labelComposable: (@Composable () -> Unit)? = if (hideNavLabels) null else {
                            @Composable { Text(item.first) }
                        }
                        NavigationBarItem(
                            selected = index == 0,
                            onClick = {},
                            icon = { Icon(item.second, contentDescription = null) },
                            label = labelComposable,
                            alwaysShowLabel = !hideNavLabels
                        )
                    }
                }
            }
    
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationY = offset * 50f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    },
                shape = RoundedCornerShape(24.dp),
                color = DsmusicTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hide Nav Labels",
                            style = DsmusicTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Remove text labels from the bottom navigation bar.",
                            style = DsmusicTheme.typography.bodyMedium,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(checked = hideNavLabels, onCheckedChange = onHideNavLabelsChanged)
                }
            }
    
            Spacer(modifier = Modifier.height(16.dp))
    
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationY = offset * 100f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    },
                shape = RoundedCornerShape(24.dp),
                color = DsmusicTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dynamic Navbar",
                            style = DsmusicTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Compact height with bold, elevated icons.",
                            style = DsmusicTheme.typography.bodyMedium,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(checked = dynamicNavStyle, onCheckedChange = onDynamicNavStyleChanged)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    @Composable
    fun PermissionsPage(
        pageOffsetProvider: () -> Float,
        audioGranted: Boolean,
        notifGranted: Boolean,
        onRequestAudio: () -> Unit,
        onRequestNotif: () -> Unit
    ) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_folder_open,
            iconTint = DsmusicTheme.colorScheme.onSurface
        ) {
            Text(
                text = "Enable\nPermissions.",
                style = DsmusicTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.5).sp,
                color = DsmusicTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
    
            Spacer(modifier = Modifier.height(32.dp))
    
            // Audio Permission Card
            PermissionCard(
                title = "Music Library",
                description = "Access local audio files for offline playback.",
                icon = Icons.Default.LibraryMusic,
                isGranted = audioGranted,
                pageOffsetProvider = pageOffsetProvider,
                offsetMultiplier = 200f,
                onRequest = onRequestAudio
            )
    
            Spacer(modifier = Modifier.height(12.dp))
    
            // Notification Permission Card
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionCard(
                    title = "Playback Notifications",
                    description = "Control music from your lock screen.",
                    icon = Icons.Default.Notifications,
                    isGranted = notifGranted,
                    pageOffsetProvider = pageOffsetProvider,
                    offsetMultiplier = 300f,
                    onRequest = onRequestNotif
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
    
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    @Composable
    fun PermissionCard(
        title: String,
        description: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        isGranted: Boolean,
        pageOffsetProvider: () -> Float,
        offsetMultiplier: Float,
        onRequest: () -> Unit
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 0.dp, vertical = 6.dp)
                .graphicsLayer {
                    val offset = pageOffsetProvider()
                    translationY = offset * offsetMultiplier * 0.2f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                },
            shape = RoundedCornerShape(20.dp),
            color = DsmusicTheme.colorScheme.surfaceContainerHigh,
            onClick = { if (!isGranted) onRequest() }
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGranted) DsmusicTheme.colorScheme.primary else DsmusicTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.Check else icon,
                        contentDescription = "Permission icon",
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                scaleX = if (isGranted) 1.1f else 1f
                                scaleY = if (isGranted) 1.1f else 1f
                                alpha = if (isGranted) 1f else 0.8f
                            },
                        tint = if (isGranted) DsmusicTheme.colorScheme.onPrimary else DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = DsmusicTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = DsmusicTheme.typography.bodySmall,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (!isGranted) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Grant $title permission",
                        modifier = Modifier.size(20.dp),
                        tint = DsmusicTheme.colorScheme.primary
                    )
                }
            }
        }
    }
    
    @Composable
    fun SignInPage(
        pageOffsetProvider: () -> Float,
        isLoggedIn: Boolean,
        userName: String,
        userProfileUrl: String?,
        onLoginSuccess: () -> Unit,
        onGuestClick: () -> Unit
    ) {
        var showWebView by remember { mutableStateOf(false) }

        if (showWebView) {
            val accountViewModel: com.dsann.music.viewmodel.AccountViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showWebView = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                com.dsann.music.ui.explore.GoogleSignInScreen(
                    accountViewModel = accountViewModel,
                    onSignInSuccess = { 
                        showWebView = false 
                        onLoginSuccess()
                    },
                    onNavigateUp = { showWebView = false },
                    isOnboarding = true
                )
            }
        }

        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = if (isLoggedIn && userProfileUrl != null) null else R.drawable.ic_youtube_music,
            imageUrl = if (isLoggedIn) userProfileUrl else null,
            iconTint = DsmusicTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                if (isLoggedIn) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Welcome,\n$userName",
                        style = DsmusicTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        fontSize = 42.sp,
                        lineHeight = 48.sp,
                        letterSpacing = (-0.5).sp,
                        color = DsmusicTheme.colorScheme.onBackground,
                        modifier = Modifier.graphicsLayer { 
                            val offset = pageOffsetProvider()
                            translationY = offset * -30f
                            alpha = 1f - kotlin.math.abs(offset * 1.5f)
                        }
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Surface(
                        color = DsmusicTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.graphicsLayer {
                            val offset = pageOffsetProvider()
                            translationY = offset * -20f
                            alpha = 1f - kotlin.math.abs(offset * 1.5f)
                        }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = DsmusicTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Login Successful", style = DsmusicTheme.typography.labelMedium, color = DsmusicTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(120.dp))
                } else {
                    Text(
                        text = "Sign In &\nSync.",
                        style = DsmusicTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        fontSize = 42.sp,
                        lineHeight = 48.sp,
                        letterSpacing = (-0.5).sp,
                        color = DsmusicTheme.colorScheme.onBackground,
                        modifier = Modifier.graphicsLayer { 
                            val offset = pageOffsetProvider()
                            translationY = offset * -30f
                            alpha = 1f - kotlin.math.abs(offset * 1.5f)
                        }
                    )
        
                    Spacer(modifier = Modifier.height(20.dp))
        
                    Text(
                        text = "Connect your account to sync playlists, liked songs, and preferences across all your devices.",
                        style = DsmusicTheme.typography.bodyLarge,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 24.sp,
                        modifier = Modifier.graphicsLayer {
                            val offset = pageOffsetProvider()
                            translationY = offset * -20f
                            alpha = 1f - kotlin.math.abs(offset * 1.5f)
                        }
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    com.dsann.music.ui.theme.Button(
                        onClick = { showWebView = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .graphicsLayer {
                                val offset = pageOffsetProvider()
                                translationY = offset * -10f
                                alpha = 1f - kotlin.math.abs(offset * 1.5f)
                            },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_youtube_music),
                            contentDescription = "YouTube Music",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Continue with YouTube Music",
                            style = DsmusicTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    com.dsann.music.ui.theme.OutlinedButton(
                        onClick = onGuestClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .graphicsLayer {
                                val offset = pageOffsetProvider()
                                translationY = offset * -5f
                                alpha = 1f - kotlin.math.abs(offset * 1.5f)
                            },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "Continue as Guest",
                            style = DsmusicTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DsmusicTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
    
    @Composable
    fun FinishPage(
        pageOffsetProvider: () -> Float,
        isLoggedIn: Boolean,
        userProfileUrl: String?
    ) {
        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
    
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = if (isLoggedIn && userProfileUrl != null) null else R.drawable.ic_applogo,
            imageUrl = if (isLoggedIn) userProfileUrl else null,
            iconTint = DsmusicTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = DsmusicTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationY = offset * -30f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(DsmusicTheme.colorScheme.primary.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Setup Complete",
                            style = DsmusicTheme.typography.labelMedium,
                            color = DsmusicTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
    
                Text(
                    text = "Ready to\nFlow.",
                    style = DsmusicTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    fontSize = 42.sp,
                    lineHeight = 48.sp,
                    letterSpacing = (-0.5).sp,
                    color = DsmusicTheme.colorScheme.onBackground,
                    modifier = Modifier.graphicsLayer { 
                        val offset = pageOffsetProvider()
                        translationY = offset * -20f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
    
                Spacer(modifier = Modifier.height(20.dp))
    
                Text(
                    text = "Your library is fully initialized and the engine is primed. It's time to immerse yourself in the ultimate auditory experience.",
                    style = DsmusicTheme.typography.bodyLarge,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp,
                    modifier = Modifier.graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationY = offset * -10f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
    
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun SetupBottomBar(
        modifier: Modifier = Modifier,
        pagerState: PagerState,
        isNextEnabled: Boolean = true,
        onNextClicked: () -> Unit,
        onBackClicked: () -> Unit,
        onFinishClicked: () -> Unit
    ) {
        val morphAnimationSpec = tween<Float>(durationMillis = 600, easing = FastOutSlowInEasing)
        val rotationAnimationSpec = tween<Float>(durationMillis = 900, easing = FastOutSlowInEasing)
    
        val targetShapeValues = when (pagerState.currentPage % 3) {
            0 -> listOf(50f, 50f, 50f, 50f)
            1 -> listOf(26f, 26f, 26f, 26f)
            else -> listOf(18f, 50f, 18f, 50f)
        }
    
        val animatedTopStart by animateFloatAsState(targetShapeValues[0], morphAnimationSpec, label = "TopStart")
        val animatedTopEnd by animateFloatAsState(targetShapeValues[1], morphAnimationSpec, label = "TopEnd")
        val animatedBottomStart by animateFloatAsState(targetShapeValues[2], morphAnimationSpec, label = "BottomStart")
        val animatedBottomEnd by animateFloatAsState(targetShapeValues[3], morphAnimationSpec, label = "BottomEnd")
    
        val animatedRotation by animateFloatAsState(
            targetValue = pagerState.currentPage * 360f,
            animationSpec = rotationAnimationSpec,
            label = "Rotation"
        )
    
        val shape = RoundedCornerShape(
            topEnd = 38.dp,
            topStart = 38.dp,
            bottomEnd = 0.dp,
            bottomStart = 0.dp
        )
    
        Surface(
            modifier = modifier.shadow(elevation = 8.dp, shape = shape, clip = true),
            color = DsmusicTheme.colorScheme.surfaceContainer,
            shape = shape
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp)
            ) {
                val animatedProgress by animateFloatAsState(
                    targetValue = (pagerState.currentPage + 1f) / pagerState.pageCount,
                    animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                    label = "progress"
                )
                com.dsann.music.ui.theme.LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = DsmusicTheme.colorScheme.primary,
                    trackColor = DsmusicTheme.colorScheme.surfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = pagerState.currentPage,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInVertically { height -> height } + fadeIn()).togetherWith(slideOutVertically { height -> -height } + fadeOut())
                            } else {
                                (slideInVertically { height -> -height } + fadeIn()).togetherWith(slideOutVertically { height -> height } + fadeOut())
                            }.using(SizeTransform(clip = false))
                        },
                        label = "StepTextAnimation"
                    ) { targetPage ->
                        if (targetPage == 0) {
                            Text(
                                text = "Let's Go!",
                                style = DsmusicTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = DsmusicTheme.colorScheme.primary,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                com.dsann.music.ui.theme.IconButton(
                                    onClick = onBackClicked,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = DsmusicTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Step $targetPage of ${pagerState.pageCount - 1}",
                                    style = DsmusicTheme.typography.bodyLarge,
                                    color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
    
                    val isLastPage = pagerState.currentPage == pagerState.pageCount - 1
                    val fabAlpha by animateFloatAsState(if (isNextEnabled || isLastPage) 1f else 0.4f, label = "fab_alpha")
    
                    AnimatedContent(
                        targetState = isLastPage,
                        label = "FabOrExtended",
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith fadeOut(animationSpec = tween(90))
                        }
                    ) { isFinish ->
                        if (isFinish) {
                            com.dsann.music.ui.theme.ExtendedFloatingActionButton(
                                onClick = onFinishClicked,
                                text = { Text("Start Listening") },
                                icon = { Icon(Icons.Default.Check, contentDescription = "Finish") },
                                containerColor = DsmusicTheme.colorScheme.primaryContainer,
                                contentColor = DsmusicTheme.colorScheme.onPrimaryContainer,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp)
                            )
                        } else {
                            FloatingActionButton(
                                onClick = { if (isNextEnabled) onNextClicked() },
                                shape = RoundedCornerShape(
                                    topStartPercent = animatedTopStart.toInt(),
                                    topEndPercent = animatedTopEnd.toInt(),
                                    bottomStartPercent = animatedBottomStart.toInt(),
                                    bottomEndPercent = animatedBottomEnd.toInt()
                                ),
                                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                                containerColor = DsmusicTheme.colorScheme.primaryContainer,
                                contentColor = DsmusicTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .rotate(animatedRotation)
                                    .graphicsLayer { alpha = fabAlpha }
                            ) {
                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = "Next",
                                    modifier = Modifier.rotate(-animatedRotation)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
