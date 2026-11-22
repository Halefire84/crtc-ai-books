package com.crtc.books

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.*

class MainActivity : ComponentActivity() {

    lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        billingManager = BillingManager(this)
        setContent {
            CRTCAIBooksTheme {
                AppNav(billingManager, this)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.destroy()
    }
}

@Composable
fun AppNav(billing: BillingManager, activity: Activity) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "library") {
        composable("library") { LibraryScreen(nav, billing, activity) }
        composable("book/{id}") { back ->
            val id = back.arguments?.getString("id") ?: return@composable
            val book = BOOKS.firstOrNull { it.id == id } ?: return@composable
            BookScreen(book, nav, billing, activity)
        }
        composable("quiz/{bookId}") { back ->
            val bookId = back.arguments?.getString("bookId") ?: return@composable
            QuizScreen(bookId, nav)
        }
        composable("upgrade") { UpgradeScreen(nav, billing, activity) }
    }
}

// ─── Library ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(nav: NavController, billing: BillingManager, activity: Activity) {
    val ctx = LocalContext.current
    val isPro by billing.isPro.collectAsState()
    var showImportPicker by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    val epubLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val imported = EpubImporter.parse(ctx, uri)
            // For library-level import we just navigate to first book and load it
            // In a real build, imported books would be added to a local library
            importError = "Imported: ${imported.title} (${imported.chapters.size} chapters) — open a book to listen"
        } catch (e: Exception) {
            importError = "Import failed: ${e.message}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CRTC AI Books", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        if (isPro) ProBadge()
                    }
                },
                actions = {
                    if (!isPro) {
                        TextButton(onClick = { nav.navigate("upgrade") }) {
                            Text("Go Pro", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D1117), titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            if (isPro) {
                FloatingActionButton(
                    onClick = { epubLauncher.launch(arrayOf("application/epub+zip")) },
                    containerColor = Color(0xFF58A6FF), contentColor = Color.White
                ) { Icon(Icons.Default.FileOpen, "Import EPUB") }
            }
        },
        containerColor = Color(0xFF0D1117)
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding)
        ) {
            if (!isPro) {
                item {
                    UpgradeBanner { nav.navigate("upgrade") }
                }
            }
            importError?.let { msg ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2634)),
                        shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Info, null, tint = Color(0xFF58A6FF), modifier = Modifier.size(16.dp))
                            Text(msg, color = Color(0xFFC9D1D9), fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { importError = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, null, tint = Color(0xFF8B949E))
                            }
                        }
                    }
                }
            }
            item {
                Text("5 books on AI, automation and local business",
                    color = Color(0xFF8B949E), fontSize = 13.sp)
            }
            items(BOOKS) { book ->
                BookCard(book) { nav.navigate("book/${book.id}") }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun ProBadge() {
    Box(
        Modifier
            .background(Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500))),
                RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text("PRO", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun UpgradeBanner(onUpgrade: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onUpgrade),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2210))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text("Upgrade to Pro — $PRO_PRICE one-time",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("EPUB import · Chapter summaries · Spaced repetition · Voice commands",
                    color = Color(0xFF8B949E), fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF8B949E))
        }
    }
}

@Composable
fun BookCard(book: Book, onClick: () -> Unit) {
    val accent = Color(book.accentHex)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(accent))
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top) {
                Text(book.title, color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 17.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(book.price, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(book.words, color = Color(0xFF8B949E), fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Text(book.tagline, color = Color(0xFFC9D1D9), fontSize = 13.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─── Book Detail ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(book: Book, nav: NavController, billing: BillingManager, activity: Activity) {
    val ctx = LocalContext.current
    val vm: BookViewModel = viewModel(factory = BookViewModelFactory(ctx, book, billing))

    val ttsState by vm.ttsState.collectAsState()
    val ttsProgress by vm.ttsProgress.collectAsState()
    val speedIndex by vm.speedIndex.collectAsState()
    val genProgress by vm.genProgress.collectAsState()
    val isPro by vm.isPro.collectAsState()
    val voiceListening by vm.voiceListening.collectAsState()
    val voiceError by vm.voiceError.collectAsState()
    val importedBook by vm.importedBook.collectAsState()
    val chapters by vm.chapters.collectAsState()
    val currentChapterIndex by vm.currentChapterIndex.collectAsState()
    val summaries by vm.summaries.collectAsState()
    val dueCardCount by vm.dueCardCount.collectAsState()
    val sleepMinutes by vm.sleepMinutes.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    val accent = Color(book.accentHex)

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = buildList {
        add("Listen")
        if (chapters.isNotEmpty()) add("Chapters")
        if (isPro && summaries.isNotEmpty()) add("Summary")
        if (isPro) add("Quiz")
    }

    // EPUB picker (Pro only)
    val epubLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val imported = EpubImporter.parse(ctx, uri)
                vm.importEpub(imported)
            } catch (_: Exception) {}
        }
    }

    // Record audio permission
    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.startVoiceCommand() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(book.title.replace('\n', ' '), maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        if (isPro) ProBadge()
                    }
                },
                navigationIcon = {
                    IconButton({ nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.addBookmark() }) {
                        Icon(Icons.Default.BookmarkBorder, "Bookmark", tint = Color(0xFFC9D1D9))
                    }
                    if (isPro) {
                        IconButton(onClick = { epubLauncher.launch(arrayOf("application/epub+zip")) }) {
                            Icon(Icons.Default.FileOpen, "Import EPUB", tint = Color(0xFFC9D1D9))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D1117), titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White)
            )
        },
        containerColor = Color(0xFF0D1117)
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // Tab row
            if (tabs.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab.coerceAtMost(tabs.size - 1),
                    containerColor = Color(0xFF0D1117),
                    contentColor = Color.White,
                    edgePadding = 16.dp
                ) {
                    tabs.forEachIndexed { i, label ->
                        Tab(selected = selectedTab == i,
                            onClick = { selectedTab = i },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(label, fontSize = 13.sp)
                                    if (label == "Quiz" && dueCardCount > 0) {
                                        DueBadge(dueCardCount)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (tabs.getOrNull(selectedTab)) {
                    "Listen", null -> {
                        // Book header
                        item {
                            BookHeader(book, importedBook, accent)
                        }
                        // TTS Player
                        item {
                            TtsPlayerCard(
                                book = book,
                                vm = vm,
                                ttsState = ttsState,
                                ttsProgress = ttsProgress,
                                speedIndex = speedIndex,
                                genProgress = genProgress,
                                voiceListening = voiceListening,
                                voiceError = voiceError,
                                sleepMinutes = sleepMinutes,
                                accent = accent,
                                onVoiceTap = {
                                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onUpgrade = { nav.navigate("upgrade") },
                                isPro = isPro
                            )
                        }
                        // Bookmarks
                        if (bookmarks.isNotEmpty()) {
                            item {
                                BookmarksCard(bookmarks, accent) { bm ->
                                    vm.jumpToChapter(bm.chapterIndex)
                                }
                            }
                        }
                        // Buy buttons
                        item {
                            BuyButtons(book, ctx, accent)
                        }
                    }
                    "Chapters" -> {
                        itemsIndexed(chapters) { i, ch ->
                            ChapterRow(
                                chapter = ch,
                                index = i,
                                isActive = i == currentChapterIndex,
                                accent = accent
                            ) {
                                vm.jumpToChapter(i)
                                vm.play()
                                selectedTab = 0
                            }
                        }
                    }
                    "Summary" -> {
                        if (summaries.isEmpty()) {
                            item {
                                EmptyState("Summaries generate after you import an EPUB",
                                    Icons.Default.Summarize)
                            }
                        } else {
                            items(summaries.entries.toList()) { (title, summary) ->
                                SummaryCard(title, summary, accent)
                            }
                        }
                    }
                    "Quiz" -> {
                        if (dueCardCount == 0) {
                            item {
                                EmptyState(
                                    "No cards due — import an EPUB to generate quiz cards",
                                    Icons.Default.School
                                )
                            }
                        } else {
                            item {
                                QuizDeckCard(book.id, dueCardCount, accent) {
                                    nav.navigate("quiz/${book.id}")
                                }
                            }
                        }
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                                shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("How it works", color = Color.White,
                                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    QuizFeatureRow("Auto-generated from your book chapters")
                                    QuizFeatureRow("SM-2 spaced repetition scheduling")
                                    QuizFeatureRow("Fill-in-the-blank from key sentences")
                                    QuizFeatureRow("Reviews due today shown first")
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun BookHeader(book: Book, importedBook: ImportedBook?, accent: Color) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(
                listOf(Color(book.accentHex).copy(alpha = 0.25f), Color(0xFF161B22))))
            .padding(20.dp)
    ) {
        Column {
            val displayTitle = importedBook?.title ?: book.title
            val displayAuthor = importedBook?.author ?: "Chris Hale"
            Text(displayTitle.replace('\n', ' '), color = Color.White,
                fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp)
            Spacer(Modifier.height(4.dp))
            Text(displayAuthor, color = Color(0xFF8B949E), fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(book.subtitle, color = Color(0xFFC9D1D9), fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(book.price, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text(
                    if (importedBook != null)
                        "${EpubImporter.totalWords(importedBook).formatThousands()} words · ${importedBook.chapters.size} chapters"
                    else book.words,
                    color = Color(0xFF8B949E), fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}

@Composable
fun TtsPlayerCard(
    book: Book,
    vm: BookViewModel,
    ttsState: TtsState,
    ttsProgress: Float,
    speedIndex: Int,
    genProgress: GenerationProgress,
    voiceListening: Boolean,
    voiceError: String?,
    sleepMinutes: Int,
    accent: Color,
    onVoiceTap: () -> Unit,
    onUpgrade: () -> Unit,
    isPro: Boolean
) {
    var showSleepPicker by remember { mutableStateOf(false) }
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val micScale by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "micScale"
    )

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Listen", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Sleep timer toggle
                    IconButton(onClick = { showSleepPicker = !showSleepPicker },
                        modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Bedtime, "Sleep timer",
                            tint = if (sleepMinutes > 0) accent else Color(0xFF8B949E),
                            modifier = Modifier.size(18.dp))
                    }
                    // Voice command button
                    if (isPro) {
                        IconButton(onClick = onVoiceTap,
                            modifier = Modifier.size(32.dp)
                                .then(if (voiceListening) Modifier.scale(micScale) else Modifier)) {
                            Icon(Icons.Default.Mic, "Voice command",
                                tint = if (voiceListening) accent else Color(0xFF8B949E),
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Playback progress bar
            if (ttsState != TtsState.IDLE || ttsProgress > 0f) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { ttsProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent, trackColor = Color(0xFF30363D)
                )
            }

            Spacer(Modifier.height(10.dp))

            // Audio generation
            if (!genProgress.done && genProgress.total == 0) {
                OutlinedButton(onClick = { vm.generateAudio() }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)) {
                    Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Generate Audio Files", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
            }
            if (genProgress.total > 0 && !genProgress.done) {
                LinearProgressIndicator(progress = { genProgress.fraction }, modifier = Modifier.fillMaxWidth(),
                    color = accent, trackColor = Color(0xFF30363D))
                Spacer(Modifier.height(4.dp))
                Text("Generating… ${genProgress.pct}%", color = Color(0xFF8B949E), fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
            }
            if (genProgress.done) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(14.dp))
                    Text("Audio ready", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
            }

            // Main playback controls
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.skipBack() }) {
                    Icon(Icons.Default.FastRewind, "Skip back", tint = Color(0xFFC9D1D9),
                        modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(6.dp))
                FloatingActionButton(
                    onClick = { if (ttsState == TtsState.PLAYING) vm.pause() else vm.play() },
                    containerColor = accent, contentColor = Color.White,
                    modifier = Modifier.size(58.dp)
                ) {
                    Icon(if (ttsState == TtsState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null, Modifier.size(30.dp))
                }
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = { vm.skipForward() }) {
                    Icon(Icons.Default.FastForward, "Skip forward", tint = Color(0xFFC9D1D9),
                        modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = { vm.stop() }) {
                    Icon(Icons.Default.Stop, "Stop", tint = Color(0xFFC9D1D9),
                        modifier = Modifier.size(22.dp))
                }
            }

            // Speed control
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                SPEED_LABELS.forEachIndexed { i, label ->
                    val selected = i == speedIndex
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .background(
                                if (selected) accent else Color(0xFF30363D),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { vm.setSpeed(i) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label,
                            color = if (selected) Color.White else Color(0xFF8B949E),
                            fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            // Sleep timer picker
            AnimatedVisibility(visible = showSleepPicker) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF30363D))
                    Spacer(Modifier.height(8.dp))
                    Text("Sleep timer", color = Color(0xFF8B949E), fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Off", 15 to "15m", 30 to "30m", 45 to "45m", 60 to "1h").forEach { (m, lbl) ->
                            val sel = sleepMinutes == m
                            Box(
                                Modifier
                                    .background(if (sel) accent else Color(0xFF30363D), RoundedCornerShape(6.dp))
                                    .clickable { vm.setSleepTimer(m); showSleepPicker = false }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(lbl, color = if (sel) Color.White else Color(0xFF8B949E),
                                    fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            // Voice error
            voiceError?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(err, color = Color(0xFF8B949E), fontSize = 11.sp)
            }

            // Voice command hint
            if (isPro && voiceListening) {
                Spacer(Modifier.height(8.dp))
                Text("Listening… say: play, pause, stop, faster, slower, skip, back",
                    color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            // Pro upsell for voice
            if (!isPro) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onUpgrade)
                        .background(Color(0xFF1A2210), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Mic, null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                    Text("Pro: Voice commands, EPUB import, chapter summaries",
                        color = Color(0xFFC9D1D9), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("$PRO_PRICE", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            if (ttsState == TtsState.ERROR) {
                Spacer(Modifier.height(8.dp))
                Text("TTS error — check Settings › Accessibility › Text-to-Speech",
                    color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun BookmarksCard(bookmarks: List<Bookmark>, accent: Color, onJump: (Bookmark) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Bookmark, null, tint = accent, modifier = Modifier.size(16.dp))
                Text("Bookmarks", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            bookmarks.takeLast(5).reversed().forEach { bm ->
                Row(
                    Modifier.fillMaxWidth().clickable { onJump(bm) }.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = accent, modifier = Modifier.size(14.dp))
                    Text(bm.chapterTitle, color = Color(0xFFC9D1D9), fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("ch.${bm.chapterIndex + 1}", color = Color(0xFF8B949E), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun BuyButtons(book: Book, ctx: android.content.Context, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Get the Full Book", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Button(
            onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(book.gumroadUrl))) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.ShoppingCart, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Buy on Gumroad — ${book.price}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        OutlinedButton(
            onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(book.kdpUrl))) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF9900))
        ) {
            Text("Buy on Amazon Kindle — ${book.price}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
fun ChapterRow(chapter: EpubChapter, index: Int, isActive: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(
                if (isActive) accent.copy(alpha = 0.1f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            ).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(28.dp).background(if (isActive) accent else Color(0xFF30363D), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("${index + 1}", color = if (isActive) Color.White else Color(0xFF8B949E),
                fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(chapter.title, color = if (isActive) Color.White else Color(0xFFC9D1D9),
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${chapter.body.split(" ").size.formatThousands()} words",
                color = Color(0xFF8B949E), fontSize = 11.sp)
        }
        if (isActive) {
            Icon(Icons.Default.VolumeUp, null, tint = accent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SummaryCard(title: String, summary: String, accent: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(3.dp).background(accent))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Text(summary, color = Color(0xFFC9D1D9), fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun QuizDeckCard(bookId: String, dueCount: Int, accent: Color, onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onStart),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(48.dp).background(accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("$dueCount", color = accent, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f)) {
                Text("Review Due", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("$dueCount card${if (dueCount != 1) "s" else ""} ready for spaced-rep review",
                    color = Color(0xFF8B949E), fontSize = 13.sp)
            }
            Icon(Icons.Default.ArrowForward, null, tint = accent)
        }
    }
}

@Composable
fun QuizFeatureRow(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Check, null, tint = Color(0xFF3FB950), modifier = Modifier.size(14.dp))
        Text(text, color = Color(0xFF8B949E), fontSize = 13.sp)
    }
}

@Composable
fun DueBadge(count: Int) {
    Box(
        Modifier.size(16.dp).background(Color(0xFFDA3633), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(if (count > 9) "9+" else "$count", color = Color.White, fontSize = 8.sp,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EmptyState(message: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = Color(0xFF30363D), modifier = Modifier.size(48.dp))
            Text(message, color = Color(0xFF8B949E), fontSize = 14.sp,
                lineHeight = 20.sp, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ─── Quiz Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(bookId: String, nav: NavController) {
    val ctx = LocalContext.current
    var dueCards by remember { mutableStateOf(SpacedRepetitionEngine.dueCards(ctx, bookId)) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var sessionDone by remember { mutableStateOf(false) }
    var reviewed by remember { mutableIntStateOf(0) }

    val card = dueCards.getOrNull(currentIndex)

    fun advance() {
        reviewed++
        val next = currentIndex + 1
        if (next >= dueCards.size) {
            sessionDone = true
        } else {
            currentIndex = next
            revealed = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Session", color = Color.White) },
                navigationIcon = {
                    IconButton({ nav.popBackStack() }) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D1117))
            )
        },
        containerColor = Color(0xFF0D1117)
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            // Progress
            val total = dueCards.size + reviewed
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { reviewed.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF58A6FF), trackColor = Color(0xFF30363D)
                )
                Spacer(Modifier.height(4.dp))
                Text("$reviewed / $total reviewed", color = Color(0xFF8B949E), fontSize = 12.sp)
            }
            Spacer(Modifier.height(24.dp))

            if (sessionDone || card == null) {
                // Done screen
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF3FB950),
                            modifier = Modifier.size(64.dp))
                        Text("Session complete!", color = Color.White,
                            fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        Text("Reviewed $reviewed card${if (reviewed != 1) "s" else ""}",
                            color = Color(0xFF8B949E), fontSize = 16.sp)
                        Button(
                            onClick = { nav.popBackStack() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF58A6FF)),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Back to Book", fontWeight = FontWeight.Bold) }
                    }
                }
            } else {
                // Flashcard
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
                ) {
                    Column(Modifier.padding(24.dp).fillMaxSize()) {
                        Text("Chapter: ${card.chapterTitle}", color = Color(0xFF8B949E),
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(16.dp))
                        Text(card.question, color = Color.White, fontSize = 16.sp,
                            lineHeight = 24.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        if (revealed) {
                            HorizontalDivider(color = Color(0xFF30363D))
                            Spacer(Modifier.height(12.dp))
                            Text("Answer", color = Color(0xFF8B949E), fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(card.answer, color = Color(0xFF3FB950),
                                fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (!revealed) {
                    Button(
                        onClick = { revealed = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF58A6FF)),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Show Answer", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                } else {
                    Text("How well did you know it?", color = Color(0xFF8B949E),
                        fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReviewButton("Again", Color(0xFFDA3633), Modifier.weight(1f)) {
                            SpacedRepetitionEngine.reviewCard(ctx, card, ReviewResult.AGAIN)
                            dueCards = SpacedRepetitionEngine.dueCards(ctx, bookId)
                            advance()
                        }
                        ReviewButton("Hard", Color(0xFFD29922), Modifier.weight(1f)) {
                            SpacedRepetitionEngine.reviewCard(ctx, card, ReviewResult.HARD)
                            dueCards = SpacedRepetitionEngine.dueCards(ctx, bookId)
                            advance()
                        }
                        ReviewButton("Good", Color(0xFF3FB950), Modifier.weight(1f)) {
                            SpacedRepetitionEngine.reviewCard(ctx, card, ReviewResult.GOOD)
                            dueCards = SpacedRepetitionEngine.dueCards(ctx, bookId)
                            advance()
                        }
                        ReviewButton("Easy", Color(0xFF58A6FF), Modifier.weight(1f)) {
                            SpacedRepetitionEngine.reviewCard(ctx, card, ReviewResult.EASY)
                            dueCards = SpacedRepetitionEngine.dueCards(ctx, bookId)
                            advance()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.15f),
            contentColor = color),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

// ─── Upgrade Screen ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeScreen(nav: NavController, billing: BillingManager, activity: Activity) {
    val isPro by billing.isPro.collectAsState()
    val billingReady by billing.billingReady.collectAsState()

    LaunchedEffect(isPro) { if (isPro) nav.popBackStack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upgrade to Pro", color = Color.White) },
                navigationIcon = {
                    IconButton({ nav.popBackStack() }) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D1117))
            )
        },
        containerColor = Color(0xFF0D1117)
    ) { padding ->
        Column(Modifier.padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Price hero
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A2634), Color(0xFF0D1117))))
                .padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700),
                        modifier = Modifier.size(40.dp))
                    Text("CRTC AI Books Pro", color = Color.White,
                        fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Text(PRO_PRICE, color = Color(0xFFFFD700),
                        fontWeight = FontWeight.ExtraBold, fontSize = 36.sp)
                    Text("One-time purchase — no subscription",
                        color = Color(0xFF8B949E), fontSize = 13.sp)
                }
            }

            // Features list
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProFeatureRow(Icons.Default.FileOpen, "Import any EPUB",
                        "Listen to your own books, not just previews")
                    ProFeatureRow(Icons.Default.Mic, "Voice commands",
                        "Say 'play', 'pause', 'faster', 'skip' — hands-free control")
                    ProFeatureRow(Icons.Default.Summarize, "Chapter summaries",
                        "Auto-generated extractive summary for every chapter")
                    ProFeatureRow(Icons.Default.School, "Spaced repetition quiz",
                        "SM-2 flashcards — retain what you read. No competitor has this.")
                    ProFeatureRow(Icons.Default.Bookmark, "Unlimited bookmarks",
                        "Jump back to any point instantly")
                    ProFeatureRow(Icons.Default.Bedtime, "Sleep timer",
                        "Auto-stop after 15, 30, 45 or 60 minutes")
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { billing.launchBillingFlow(activity) },
                modifier = Modifier.fillMaxWidth(),
                enabled = billingReady,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700), contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Unlock Pro — $PRO_PRICE", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }

            if (!billingReady) {
                Text("Connecting to Play Store…", color = Color(0xFF8B949E),
                    fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            Text("Purchase restores automatically on reinstall. One-time, no subscription.",
                color = Color(0xFF8B949E), fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
fun ProFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp)
            .padding(top = 2.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(desc, color = Color(0xFF8B949E), fontSize = 12.sp)
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

fun Int.formatThousands(): String = when {
    this >= 1_000_000 -> "${this / 1_000_000}M"
    this >= 1_000 -> "${this / 1_000}K"
    else -> "$this"
}
