package com.hmmodz.sankareader.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.hmmodz.sankareader.R
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.hmmodz.sankareader.data.*
import com.hmmodz.sankareader.model.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

private val bg = Color(0xFF09090D)
private val panel = Color(0xFF171720)
private val accent = Color(0xFFB79BFF)

@Composable fun ReaderApp() {
    val ctx = LocalContext.current
    val api = remember { ApiClient() }; val store = remember { LocalStore(ctx) }; val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("Home") }; var provider by remember { mutableStateOf("all") }
    var items by remember { mutableStateOf<List<Comic>>(emptyList()) }; var selected by remember { mutableStateOf<Comic?>(null) }
    var query by remember { mutableStateOf("") }; var loading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    var chapter by remember { mutableStateOf<Chapter?>(null) }; var images by remember { mutableStateOf<List<String>>(emptyList()) }
    var immersive by remember { mutableStateOf(false) }; var speed by remember { mutableIntStateOf(store.speed()) }; var autoNext by remember { mutableStateOf(store.autoNext()) }
    var detail by remember { mutableStateOf<Comic?>(null) }
    fun load(block: suspend () -> List<Comic>) { scope.launch { loading = true; error = ""; try { items = block() } catch (e: Exception) { error = e.message ?: "Gagal memuat data" } finally { loading = false } } }
    LaunchedEffect(Unit) {
        val cached = store.cachedHome()
        if (cached.isNotEmpty()) items = cached
        load { if (provider == "all") api.home() else api.latestAll() }
    }
    MaterialTheme(colorScheme = darkColorScheme(background = bg, surface = panel, primary = accent, onBackground = Color.White, onSurface = Color.White)) {
        Box(Modifier.fillMaxSize().background(bg)) {
            Column(Modifier.fillMaxSize()) {
                if (page == "Reader") {
                    ReaderScreen(images, chapter, speed, { speed = it; store.setSpeed(it) }, autoNext, { autoNext = it; store.setAutoNext(it) },
                        onExit = { page = "Detail"; immersive = false },
                        onNext = { selected?.let { c -> val idx = c.chapters.indexOfFirst { it.id == chapter?.id }; c.chapters.getOrNull(idx + 1)?.let { openChapter(scope, api, c, it, store, { images = it }) { chapter = it } } } },
                        onPrev = { selected?.let { c -> val idx = c.chapters.indexOfFirst { it.id == chapter?.id }; c.chapters.getOrNull(idx - 1)?.let { openChapter(scope, api, c, it, store, { images = it }) { chapter = it } } } },
                        immersive = immersive, setImmersive = { immersive = it })
                } else if (page == "Detail" && detail != null) {
                    DetailScreen(detail!!, store.isFavorite(detail!!.provider + detail!!.id), { store.toggleFavorite(detail!!); items = items.toList() }, { page = "Home" }, { ch -> openChapter(scope, api, detail!!, ch, store, { images = it }) { chapter = it }; selected = detail; page = "Reader" })
                } else {
                    if (!immersive) TopBar(page)
                    when (page) {
                        "Home" -> HomeScreen(items, query, { query = it }, provider, { provider = it; load { if (it == "all") api.latestAll() else api.latest(it) } }, loading, error,
                            onSearch = { if (query.isNotBlank()) load { api.search(provider, query) } }, onComic = { selected = it; scope.launch { loading = true; try { detail = api.detail(it.provider, it.slug); store.addHistory(it); page = "Detail" } catch (e: Exception) { error = e.message ?: "Gagal membuka detail" } finally { loading = false } } })
                        "WatchList" -> WatchListScreen(store.favorites(), store.history(), { selected = it; scope.launch { try { detail = api.detail(it.provider, it.slug); page = "Detail" } catch (e: Exception) { error = e.message ?: "Gagal" } } })
                        "Profile" -> ProfileScreen()
                    }
                    if (error.isNotBlank()) Text(error, Modifier.fillMaxWidth().padding(14.dp), color = Color(0xFFFF8A8A), fontSize = 12.sp)
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent)
                    if (!immersive) NavigationBar(containerColor = Color(0xFF101016)) {
                        listOf("Home" to Icons.Rounded.Home, "WatchList" to Icons.Rounded.Bookmarks, "Profile" to Icons.Rounded.Person).forEach { (name, icon) ->
                            NavigationBarItem(selected = page == name, onClick = { page = name }, icon = { Icon(icon, null) }, label = { Text(name) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = accent, indicatorColor = accent.copy(.16f), selectedTextColor = accent))
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun TopBar(page: String) { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("SANKA", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp); Text(when(page){"WatchList"->"Your Library";"Profile"->"Developer";else->"Discover stories"}, fontSize = 23.sp, fontWeight = FontWeight.Bold) }; Icon(Icons.Rounded.AutoAwesome, null, tint = accent) } }

@Composable private fun HomeScreen(items: List<Comic>, query: String, setQuery: (String)->Unit, provider: String, setProvider: (String)->Unit, loading: Boolean, error: String, onSearch: ()->Unit, onComic: (Comic)->Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Column(Modifier.padding(horizontal = 18.dp)) {
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF3B2B62), Color(0xFF171722)))).padding(20.dp), contentAlignment = Alignment.BottomStart) {
                Column { Text("THE NEXT CHAPTER", color = Color.White.copy(.65f), fontSize = 10.sp, letterSpacing = 2.sp); Text("Find your\nnext obsession.", fontSize = 26.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold); Text("Discover manga · manhwa · manhua", color = Color.White.copy(.7f), fontSize = 11.sp) }
            }
        } }
        item { Row(Modifier.padding(horizontal = 18.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(panel).padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Search, null, tint = accent); Spacer(Modifier.width(8.dp)); androidx.compose.material3.TextField(query, setQuery, Modifier.weight(1f), placeholder = { Text("Search title...") }, singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)); IconButton(onClick = onSearch) { Icon(Icons.Rounded.ArrowForward, null, tint = accent) } } }
        item { Column(Modifier.padding(horizontal = 18.dp)) { Text("Browse provider", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp)); LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items((listOf("all" to "All providers") + ProviderRegistry.all.filter { it.enabled }.map { it.id to it.label })) { (id, label) -> FilterChip(selected = provider == id, onClick = { setProvider(id) }, label = { Text(label) }) } } } }
        item { SectionTitle("Featured for you", "CURATED") }
        item { LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(items.take(8)) { ComicCard(it, true, onComic) } } }
        item { SectionTitle("Latest updates", "FRESH") }
        items(items) { ComicRow(it, onComic) }
        if (items.isEmpty() && !loading) item { Text("Belum ada data. Pilih provider atau lakukan pencarian.", Modifier.padding(20.dp), color = Color.LightGray) }
    }
}

@Composable private fun SectionTitle(title: String, tag: String) { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(tag, color = accent, fontSize = 9.sp, letterSpacing = 1.5.sp) } }
@Composable private fun ComicCard(c: Comic, wide: Boolean, click: (Comic)->Unit) { Column(Modifier.width(if(wide) 124.dp else 110.dp).clickable { click(c) }) { AsyncImage(c.cover, null, Modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(15.dp)).background(panel), contentScale = ContentScale.Crop); Spacer(Modifier.height(7.dp)); Text(c.title, maxLines = 2, fontWeight = FontWeight.SemiBold, fontSize = 12.sp); Text(c.latestChapter.ifBlank { c.provider }, color = Color.Gray, fontSize = 10.sp, maxLines = 1) } }
@Composable private fun ComicRow(c: Comic, click: (Comic)->Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(16.dp)).background(panel).clickable { click(c) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(c.cover, null, Modifier.size(66.dp, 82.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentScale = ContentScale.Crop); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(c.title, fontWeight = FontWeight.SemiBold, maxLines = 2); Text(c.provider.uppercase(), color = accent, fontSize = 9.sp); Text(c.latestChapter.ifBlank { "Latest update" }, color = Color.Gray, fontSize = 11.sp) }; Icon(Icons.Rounded.ChevronRight, null, tint = Color.Gray) } }

@Composable private fun WatchListScreen(favorites: List<Comic>, history: List<Comic>, click: (Comic)->Unit) { var tab by remember { mutableIntStateOf(0) }; Column { TabRow(tab, containerColor = bg, contentColor = accent) { listOf("History", "Favorites").forEachIndexed { i, s -> Tab(tab == i, { tab = i }, text = { Text(s) }) } }; val list = if(tab == 0) history else favorites; if(list.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.Bookmarks, null, tint = accent, modifier = Modifier.size(40.dp)); Text(if(tab==0) "Your reading history is empty" else "No favorites yet", color = Color.LightGray) } } else LazyColumn(contentPadding = PaddingValues(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(list) { ComicRow(it, click) } } } }

@Composable private fun DetailScreen(c: Comic, favorite: Boolean, toggle: ()->Unit, back: ()->Unit, read: (Chapter)->Unit) { Column(Modifier.fillMaxSize()) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(back) { Icon(Icons.Rounded.ArrowBack, null) }; Text("Comic details", fontWeight = FontWeight.Bold, fontSize = 18.sp) }; Row(Modifier.padding(18.dp)) { AsyncImage(c.cover, null, Modifier.size(120.dp, 160.dp).clip(RoundedCornerShape(16.dp)).background(panel), contentScale = ContentScale.Crop); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(c.title, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(c.provider.uppercase(), color = accent, fontSize = 10.sp); Spacer(Modifier.height(12.dp)); OutlinedButton(toggle) { Icon(if(favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null); Text(if(favorite) "Saved" else "Favorite") } } }; Text(c.synopsis.ifBlank { "Synopsis is not provided by this endpoint." }, Modifier.padding(horizontal = 18.dp), color = Color.LightGray, fontSize = 13.sp); Text("Chapters", Modifier.padding(18.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp); if(c.chapters.isEmpty()) Text("Chapter list is unavailable in this response. Try another provider.", Modifier.padding(18.dp), color = Color.Gray) else LazyColumn { items(c.chapters) { ch -> ListItem(headlineContent = { Text(ch.title) }, supportingContent = { Text(c.provider, color = Color.Gray) }, trailingContent = { Icon(Icons.Rounded.PlayArrow, null, tint = accent) }, modifier = Modifier.clickable { read(ch) }, colors = ListItemDefaults.colors(containerColor = bg)) } } } }

@Composable private fun ReaderScreen(images: List<String>, chapter: Chapter?, speed: Int, setSpeed: (Int)->Unit, autoNext: Boolean, setAutoNext: (Boolean)->Unit, onExit: ()->Unit, onNext: ()->Unit, onPrev: ()->Unit, immersive: Boolean, setImmersive: (Boolean)->Unit) {
    val state = rememberLazyListState(); var running by remember { mutableStateOf(false) }
    LaunchedEffect(running, speed, images.size) { while (running && images.isNotEmpty()) { kotlinx.coroutines.delay((180L / speed).coerceAtLeast(25)); if (state.canScrollForward) state.scrollBy(5f) else { running = false; if (autoNext) onNext() } } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if(images.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent) }
        else LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) { items(images) { url -> AsyncImage(url, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) } }
        if(!immersive) Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(.75f)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onExit) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }; Text(chapter?.title ?: "Reader", Modifier.weight(1f), color = Color.White, maxLines = 1); IconButton({ setImmersive(true) }) { Icon(Icons.Rounded.VisibilityOff, null, tint = Color.White) } }
        if(immersive) FloatingActionButton(onClick = { setImmersive(false) }, Modifier.align(Alignment.TopEnd).padding(14.dp), containerColor = panel, contentColor = accent) { Icon(Icons.Rounded.Visibility, "Show controls") }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(.8f)).navigationBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onPrev) { Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White) }
            IconButton({ running = !running }) { Icon(if(running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = accent) }
            IconButton(onNext) { Icon(Icons.Rounded.SkipNext, null, tint = Color.White) }
            TextButton(onClick = { setSpeed(if(speed >= 6) 1 else speed + 1) }) { Text("X$speed", color = accent, fontWeight = FontWeight.Bold) }
            IconButton({ setAutoNext(!autoNext) }) { Icon(if(autoNext) Icons.Rounded.SkipNext else Icons.Rounded.SkipNext, "Auto next", tint = if(autoNext) accent else Color.Gray) }
        }
    }
}

@Composable private fun ProfileScreen() { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = panel)) { Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painter = painterResource(R.drawable.icon), contentDescription = "Developer icon", modifier = Modifier.size(92.dp).clip(RoundedCornerShape(28.dp)).background(bg), contentScale = ContentScale.Crop)
        Text("Nama Developer", Modifier.padding(top = 14.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Building thoughtful digital experiences with a focus on software engineering and technology.", Modifier.padding(top = 8.dp), color = Color.LightGray, fontSize = 13.sp)
    } }
    SkillCard("Keahlian Developer", listOf("C++", "Java", "Kotlin"))
    SkillCard("Bidang Teknologi", listOf("Cyber Security", "Reverse Engineer", "Software Developer", "Web Development"))
    Text("Sanka Reader · Version 1.0.0", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
} }
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun SkillCard(title: String, skills: List<String>) { Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp); Spacer(Modifier.height(12.dp)); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { skills.forEach { AssistChip(onClick = {}, label = { Text(it) }, leadingIcon = { Icon(Icons.Rounded.CheckCircle, null, tint = accent) }) } } } } }

private fun openChapter(scope: CoroutineScope, api: ApiClient, comic: Comic, ch: Chapter, store: com.hmmodz.sankareader.data.LocalStore, setImages: (List<String>)->Unit, setChapter: (Chapter)->Unit) {
    scope.launch { setChapter(ch); setImages(emptyList()); try { setImages(api.read(comic.provider, comic.slug, ch.slug)); store.addHistory(comic) } catch (_: Exception) { setImages(emptyList()) } }
}
