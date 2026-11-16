package com.hmmodz.sankareader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.hmmodz.sankareader.R
import com.hmmodz.sankareader.data.*
import com.hmmodz.sankareader.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ReaderPalette(val background: Color, val surface: Color, val accent: Color, val radius: Int)
private fun palette(theme: String) = when (theme) {
    "Neobrutalisme + Dynamic Island" -> ReaderPalette(Color(0xFF101010), Color(0xFFFFE66D), Color(0xFFFF6B6B), 8)
    "Soft UI + Dynamic Island" -> ReaderPalette(Color(0xFFE8EAF0), Color(0xFFF5F6FA), Color(0xFF6750A4), 22)
    "Liquid Glass + Dynamic Island" -> ReaderPalette(Color(0xFF080D18), Color(0xAA202B40), Color(0xFF83D7FF), 24)
    else -> ReaderPalette(Color(0xFF09090D), Color(0xFF171720), Color(0xFFB79BFF), 18)
}

@Composable
fun ReaderApp() {
    val ctx = LocalContext.current
    val api = remember { ApiClient() }
    val store = remember { LocalStore(ctx) }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("Home") }
    var returnPage by remember { mutableStateOf("Home") }
    var provider by remember { mutableStateOf("all") }
    var items by remember { mutableStateOf<List<Comic>>(emptyList()) }
    var selected by remember { mutableStateOf<Comic?>(null) }
    var detail by remember { mutableStateOf<Comic?>(null) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var chapter by remember { mutableStateOf<Chapter?>(null) }
    var images by remember { mutableStateOf<List<String>>(emptyList()) }
    var readerLoading by remember { mutableStateOf(false) }
    var speed by remember { mutableIntStateOf(store.speed()) }
    var autoNext by remember { mutableStateOf(store.autoNext()) }
    var uiTheme by remember { mutableStateOf(store.uiTheme()) }
    val colors = palette(uiTheme)
    val foreground = if (uiTheme == "Soft UI + Dynamic Island" || uiTheme == "Neobrutalisme + Dynamic Island") Color(0xFF191923) else Color.White

    fun load(block: suspend () -> List<Comic>) {
        scope.launch {
            loading = true
            error = ""
            try { items = block() }
            catch (e: Exception) { error = e.message ?: "Gagal memuat data" }
            finally { loading = false }
        }
    }
    fun back() {
        when (page) {
            "Reader" -> page = "Detail"
            "Detail" -> page = returnPage
            "Settings" -> page = returnPage
            else -> page = "Home"
        }
    }
    fun openDetail(comic: Comic, from: String) {
        selected = comic
        returnPage = from
        scope.launch {
            loading = true
            error = ""
            try {
                detail = api.detail(comic.provider, comic.slug)
                store.addHistory(comic)
                page = "Detail"
            } catch (e: Exception) { error = e.message ?: "Gagal membuka detail" }
            finally { loading = false }
        }
    }
    fun startChapter(comic: Comic, ch: Chapter) {
        selected = comic
        page = "Reader"
        readerLoading = true
        openChapter(scope, api, comic, ch, store, { images = it; readerLoading = false }, { chapter = it })
    }

    BackHandler(enabled = page != "Home") { back() }

    LaunchedEffect(Unit) {
        val cached = store.cachedHome()
        if (cached.isNotEmpty()) items = cached
        load {
            val fresh = api.home()
            if (fresh.isNotEmpty()) store.cacheHome(fresh)
            fresh.ifEmpty { cached }
        }
    }

    MaterialTheme(
        colorScheme = if (uiTheme == "Soft UI + Dynamic Island") lightColorScheme(
            background = colors.background, surface = colors.surface, primary = colors.accent,
            onBackground = foreground, onSurface = foreground
        ) else darkColorScheme(
            background = colors.background, surface = colors.surface, primary = colors.accent,
            onBackground = foreground, onSurface = foreground
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            Column(Modifier.fillMaxSize()) {
                when (page) {
                    "Reader" -> ReaderScreen(
                        images = images, chapter = chapter, speed = speed,
                        setSpeed = { speed = it.coerceIn(1, 6); store.setSpeed(it) },
                        autoNext = autoNext, setAutoNext = { autoNext = it; store.setAutoNext(it) },
                        loading = readerLoading,
                        onExit = { page = "Detail" },
                        onNext = {
                            selected?.let { c ->
                                val idx = c.chapters.indexOfFirst { it.id == chapter?.id }
                                c.chapters.getOrNull(idx + 1)?.let { startChapter(c, it) }
                            }
                        },
                        onPrev = {
                            selected?.let { c ->
                                val idx = c.chapters.indexOfFirst { it.id == chapter?.id }
                                c.chapters.getOrNull(idx - 1)?.let { startChapter(c, it) }
                            }
                        },
                        onAutoNext = { selected?.let { c ->
                            val idx = c.chapters.indexOfFirst { it.id == chapter?.id }
                            c.chapters.getOrNull(idx + 1)?.let { startChapter(c, it) }
                        } },
                        colors = colors
                    )
                    else -> {
                        AppTopBar(
                            page = page,
                            foreground = foreground,
                            accent = colors.accent,
                            onSettings = { returnPage = page; page = "Settings" }
                        )
                        Box(Modifier.weight(1f)) {
                        when (page) {
                            "Home" -> HomeScreen(
                                items, query, { query = it }, provider,
                                { id -> provider = id; load { if (id == "all") api.home() else api.latest(id) } },
                                loading, error,
                                onSearch = { if (query.isNotBlank()) load { api.search(provider, query) } },
                                onComic = { openDetail(it, "Home") },
                                colors = colors
                            )
                            "WatchList" -> WatchListScreen(
                                store.favorites(), store.history(),
                                { openDetail(it, "WatchList") }, colors
                            )
                            "Profile" -> ProfileScreen(colors)
                            "Settings" -> SettingsScreen(
                                selectedTheme = uiTheme,
                                onTheme = { uiTheme = it; store.setUiTheme(it) },
                                autoNext = autoNext,
                                onAutoNext = { autoNext = it; store.setAutoNext(it) },
                                colors = colors
                            )
                            "Detail" -> detail?.let { c ->
                                DetailScreen(
                                    c, store.isFavorite(c.provider + c.id),
                                    { store.toggleFavorite(c) }, { back() },
                                    { ch -> startChapter(c, ch) }, colors
                                )
                            }
                        }
                        }
                        if (error.isNotBlank()) Text(
                            error, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            color = Color(0xFFE57373), fontSize = 12.sp
                        )
                        if (page != "Detail" && page != "Settings") {
                            NavigationBar(containerColor = colors.surface) {
                                listOf("Home" to Icons.Rounded.Home, "WatchList" to Icons.Rounded.Bookmarks, "Profile" to Icons.Rounded.Person).forEach { (name, icon) ->
                                    NavigationBarItem(
                                        selected = page == name, onClick = { page = name },
                                        icon = { Icon(icon, null) }, label = { Text(name) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = colors.accent,
                                            indicatorColor = colors.accent.copy(alpha = .16f),
                                            selectedTextColor = colors.accent,
                                            unselectedTextColor = foreground.copy(alpha = .65f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTopBar(page: String, foreground: Color, accent: Color, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("A-+ Reader", color = accent, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                when (page) { "WatchList" -> "Your library"; "Profile" -> "Developer profile"; "Settings" -> "Preferences"; else -> "Discover stories" },
                color = foreground.copy(alpha = .65f), fontSize = 11.sp
            )
        }
        Box(
            Modifier.padding(end = 8.dp).clip(CircleShape).background(accent.copy(alpha = .14f)).padding(horizontal = 12.dp, vertical = 6.dp)
        ) { Text("A+ READER", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings", tint = foreground) }
    }
}

@Composable
private fun HomeScreen(
    items: List<Comic>, query: String, setQuery: (String) -> Unit, provider: String,
    setProvider: (String) -> Unit, loading: Boolean, error: String,
    onSearch: () -> Unit, onComic: (Comic) -> Unit, colors: ReaderPalette
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(142.dp)
                    .clip(RoundedCornerShape(colors.radius.dp))
                    .background(Brush.linearGradient(listOf(colors.accent.copy(.85f), colors.surface)))
                    .padding(18.dp), contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text("YOUR NEXT CHAPTER", color = Color.White.copy(.75f), fontSize = 9.sp, letterSpacing = 2.sp)
                    Text("Stories worth\nstaying up for.", color = Color.White, fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Discover manga · manhwa · manhua", color = Color.White.copy(.78f), fontSize = 10.sp)
                }
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 18.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(colors.surface).padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, tint = colors.accent)
                TextField(
                    value = query, onValueChange = setQuery, modifier = Modifier.weight(1f),
                    placeholder = { Text("Search title...") }, singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = if (colors.background == Color(0xFFE8EAF0) || colors.surface == Color(0xFFFFE66D)) Color(0xFF191923) else Color.White,
                        unfocusedTextColor = if (colors.background == Color(0xFFE8EAF0)) Color(0xFF191923) else Color.White,
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = colors.accent
                    )
                )
                IconButton(onClick = onSearch) { Icon(Icons.Rounded.ArrowForward, null, tint = colors.accent) }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 18.dp)) {
                Text("Browse provider", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("all" to "All providers") + ProviderRegistry.all.filter { it.enabled }.map { it.id to it.label }) { (id, label) ->
                        FilterChip(
                            selected = provider == id, onClick = { setProvider(id) }, label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.accent.copy(.18f),
                                selectedLabelColor = colors.accent
                            )
                        )
                    }
                }
            }
        }
        if (loading && items.isEmpty()) item {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accent)
                Spacer(Modifier.width(10.dp))
                Text("Loading latest comics...", fontSize = 12.sp, color = Color.Gray)
            }
        }
        if (items.isNotEmpty()) {
            item { SectionTitle("Featured for you", "PICKS", colors.accent) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(items.take(8), key = { it.provider + it.id }) { ComicCard(it, onComic, colors) }
                }
            }
            item { SectionTitle("Latest updates", "FRESH", colors.accent) }
            items(items, key = { it.provider + it.id }) { ComicRow(it, onComic, colors) }
        } else if (!loading) item {
            Text(
                if (error.isNotBlank()) "Data belum dapat dimuat. Coba lagi beberapa saat." else "Belum ada data. Pilih provider atau lakukan pencarian.",
                Modifier.padding(20.dp), color = Color.Gray
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, tag: String, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(tag, color = accent, fontSize = 9.sp, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun ComicCard(c: Comic, click: (Comic) -> Unit, colors: ReaderPalette) {
    Column(Modifier.width(124.dp).clickable { click(c) }) {
        AsyncImage(c.cover, null, Modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(14.dp)).background(colors.surface), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(7.dp))
        Text(c.title, maxLines = 2, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Text(c.latestChapter.ifBlank { c.provider }, color = Color.Gray, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun ComicRow(c: Comic, click: (Comic) -> Unit, colors: ReaderPalette) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(15.dp))
            .background(colors.surface).clickable { click(c) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(c.cover, null, Modifier.size(62.dp, 78.dp).clip(RoundedCornerShape(9.dp)).background(colors.background), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(c.provider.uppercase(), color = colors.accent, fontSize = 9.sp)
            Text(c.latestChapter.ifBlank { "Latest update" }, color = Color.Gray, fontSize = 11.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Color.Gray)
    }
}

@Composable
private fun WatchListScreen(favorites: List<Comic>, history: List<Comic>, click: (Comic) -> Unit, colors: ReaderPalette) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(tab, containerColor = colors.background, contentColor = colors.accent) {
            listOf("History", "Favorites").forEachIndexed { i, label -> Tab(tab == i, { tab = i }, text = { Text(label) }) }
        }
        val list = if (tab == 0) history else favorites
        if (list.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Bookmarks, null, tint = colors.accent, modifier = Modifier.size(38.dp))
                Text(if (tab == 0) "Your reading history is empty" else "No favorites yet", color = Color.Gray)
            }
        } else LazyColumn(contentPadding = PaddingValues(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.provider + it.id }) { ComicRow(it, click, colors) }
        }
    }
}

@Composable
private fun DetailScreen(
    c: Comic, favorite: Boolean, toggle: () -> Unit, back: () -> Unit,
    read: (Chapter) -> Unit, colors: ReaderPalette
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(back) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Text("Comic details", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.Top) {
                AsyncImage(
                    c.cover, null, Modifier.size(104.dp, 140.dp).clip(RoundedCornerShape(14.dp)).background(colors.surface),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.title, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(c.provider.uppercase(), color = colors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (c.genres.isNotEmpty()) Text(c.genres.take(3).joinToString(" · "), color = Color.Gray, fontSize = 10.sp, maxLines = 2)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = toggle, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (favorite) "Saved" else "Favorite", fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 18.dp)) {
                Text("Synopsis", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    c.synopsis.ifBlank { "Synopsis is not provided by this endpoint." },
                    color = Color.Gray, fontSize = 13.sp, lineHeight = 19.sp
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Chapters", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${c.chapters.size} chapters", color = Color.Gray, fontSize = 11.sp)
            }
        }
        if (c.chapters.isEmpty()) item {
            Text("Chapter list is unavailable in this response. Try another provider.", Modifier.padding(horizontal = 18.dp), color = Color.Gray, fontSize = 12.sp)
        } else items(c.chapters, key = { it.id }) { ch ->
            ListItem(
                headlineContent = { Text(ch.title, fontSize = 13.sp) },
                supportingContent = { Text(c.provider, color = Color.Gray, fontSize = 10.sp) },
                trailingContent = { Icon(Icons.Rounded.PlayArrow, null, tint = colors.accent) },
                modifier = Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp)).clickable { read(ch) },
                colors = ListItemDefaults.colors(containerColor = colors.surface)
            )
        }
    }
}

@Composable
private fun ReaderScreen(
    images: List<String>, chapter: Chapter?, speed: Int, setSpeed: (Int) -> Unit,
    autoNext: Boolean, setAutoNext: (Boolean) -> Unit, loading: Boolean,
    onExit: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onAutoNext: () -> Unit,
    colors: ReaderPalette
) {
    val state = rememberLazyListState()
    var running by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(running, speed, images.size) {
        while (running && images.isNotEmpty()) {
            delay((100L / speed).coerceAtLeast(12L))
            if (state.canScrollForward) {
                state.scrollBy((2.2f * speed).coerceAtMost(14f))
            } else {
                running = false
                if (autoNext) onAutoNext()
            }
        }
    }
    LaunchedEffect(chapter?.id) {
        state.scrollToItem(0)
        running = false
        controlsVisible = true
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (images.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(color = colors.accent)
                else Text("Chapter images are unavailable", color = Color.White.copy(.7f), fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(images, key = { it }) { url ->
                    AsyncImage(
                        url, null,
                        Modifier.fillMaxWidth().clickable { controlsVisible = !controlsVisible },
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }

        if (controlsVisible) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Color.Black.copy(.72f)).statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onExit) { Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White) }
                Text(chapter?.title ?: "Reader", Modifier.weight(1f), color = Color.White, maxLines = 1, fontSize = 13.sp)
                IconButton({ setAutoNext(!autoNext) }) {
                    Icon(Icons.Rounded.SkipNext, "Auto next", tint = if (autoNext) colors.accent else Color.Gray)
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color.Black.copy(.72f)).navigationBarsPadding().padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onPrev) { Icon(Icons.Rounded.SkipPrevious, null); Text("Prev", fontSize = 11.sp) }
                Text("X$speed  ·  ${if (autoNext) "Auto next ON" else "Auto next OFF"}", color = Color.White.copy(.8f), fontSize = 10.sp)
                TextButton(onClick = onNext) { Text("Next", fontSize = 11.sp); Icon(Icons.Rounded.SkipNext, null) }
            }
        }

        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            FloatingActionButton(
                onClick = { running = !running },
                modifier = Modifier.size(48.dp),
                containerColor = colors.accent,
                contentColor = Color(0xFF101018)
            ) { Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (running) "Pause auto scroll" else "Start auto scroll") }
            Surface(color = Color.Black.copy(.78f), shape = RoundedCornerShape(12.dp)) {
                Text("X$speed", Modifier.padding(horizontal = 11.dp, vertical = 6.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Surface(color = Color.Black.copy(.78f), shape = CircleShape) {
                IconButton(onClick = { setSpeed(if (speed >= 6) 1 else speed + 1) }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.Speed, "Increase scroll speed", tint = Color.White)
                }
            }
        }
        if (!controlsVisible) {
            IconButton(
                onClick = { controlsVisible = true },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
                    .background(Color.Black.copy(.45f), CircleShape)
            ) { Icon(Icons.Rounded.Visibility, "Show reader controls", tint = Color.White) }
        }
    }
}

@Composable
private fun ProfileScreen(colors: ReaderPalette) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = colors.surface)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.icon), contentDescription = "Developer icon",
                    modifier = Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(colors.background),
                    contentScale = ContentScale.Crop
                )
                Text("HM MODZ TEAM", Modifier.padding(top = 13.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Software development team focused on building applications, digital products, and practical technology solutions.",
                    Modifier.padding(top = 7.dp), color = Color.Gray, fontSize = 12.sp, lineHeight = 18.sp
                )
            }
        }
        SkillCard("Programming Skills", listOf("C++", "Java", "Kotlin"), colors)
        SkillCard("Technology Fields", listOf("Cyber Security", "Reverse Engineer", "Software Developer", "Web Development"), colors)
        Text("A-+ Reader · Version 1.0.0", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun SettingsScreen(
    selectedTheme: String, onTheme: (String) -> Unit,
    autoNext: Boolean, onAutoNext: (Boolean) -> Unit, colors: ReaderPalette
) {
    val themes = listOf(
        "Neobrutalisme + Dynamic Island",
        "Soft UI + Dynamic Island",
        "Material 3 + Dynamic Island",
        "Liquid Glass + Dynamic Island"
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("UI / UX Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Choose the visual style used across A-+ Reader.", color = Color.Gray, fontSize = 12.sp)
        themes.forEach { theme ->
            val selected = theme == selectedTheme
            Card(
                onClick = { onTheme(theme) },
                shape = RoundedCornerShape(if (theme.startsWith("Neobrutalisme")) 10.dp else 18.dp),
                colors = CardDefaults.cardColors(containerColor = if (selected) colors.accent.copy(.18f) else colors.surface),
                border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, colors.accent) else null
            ) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(theme, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            when {
                                theme.startsWith("Neobrutalisme") -> "Bold contrast and hard edges"
                                theme.startsWith("Soft UI") -> "Soft surfaces and gentle depth"
                                theme.startsWith("Liquid Glass") -> "Translucent layers and luminous accents"
                                else -> "Clean Android-native components"
                            },
                            color = Color.Gray, fontSize = 10.sp
                        )
                    }
                    if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = colors.accent)
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 5.dp))
        Text("Reader", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Auto next chapter", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text("Automatically open the next chapter when scrolling ends.", color = Color.Gray, fontSize = 10.sp)
            }
            Switch(checked = autoNext, onCheckedChange = onAutoNext)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillCard(title: String, skills: List<String>, colors: ReaderPalette) {
    Card(colors = CardDefaults.cardColors(containerColor = colors.surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(17.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                skills.forEach { AssistChip(onClick = {}, label = { Text(it, fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Rounded.CheckCircle, null, tint = colors.accent) }) }
            }
        }
    }
}

private fun openChapter(
    scope: CoroutineScope, api: ApiClient, comic: Comic, ch: Chapter,
    store: LocalStore, setImages: (List<String>) -> Unit, setChapter: (Chapter) -> Unit
) {
    scope.launch {
        setChapter(ch)
        setImages(emptyList())
        try {
            val pages = api.read(comic.provider, comic.slug, ch.slug)
            setImages(pages)
            store.addHistory(comic)
        } catch (_: Exception) {
            setImages(emptyList())
        }
    }
}
