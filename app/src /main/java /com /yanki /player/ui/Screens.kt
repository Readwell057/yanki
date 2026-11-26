package com.yanki.player.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.yanki.player.R
import com.yanki.player.data.MediaInfo
import com.yanki.player.data.ProgressEntity
import com.yanki.player.service.PlayerConnection

data class Group(val title: String, val subtitle: String, val detail: Detail)

@Composable
fun YankiRoot(vm: MainViewModel, widthClass: WindowWidthSizeClass) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionChanged(ctx)
    }
    LaunchedEffect(Unit) {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray())
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!vm.granted) {
            PermissionScreen { launcher.launch(requiredPermissions()) }
        } else {
            BackHandler(enabled = vm.showPlayer || vm.detail != null || vm.searching) {
                when {
                    vm.showPlayer -> vm.showPlayer = false
                    vm.detail != null -> vm.detail = null
                    else -> { vm.searching = false; vm.query = "" }
                }
            }
            if (widthClass == WindowWidthSizeClass.Compact) PhoneLayout(vm) else TabletLayout(vm)
            PlaylistDialogs(vm)
        }
    }
}

@Composable
fun PermissionScreen(onAsk: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.yanki_logo), null, Modifier.size(120.dp))
        Spacer(Modifier.height(24.dp))
        Text("Yankı", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Müzik ve videolarını listeleyebilmek için ses ve video dosyalarına erişim izni gerekiyor.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAsk) { Text("İzin ver") }
        TextButton(onClick = {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
            )
        }) { Text("Uygulama ayarlarını aç") }
    }
}

@Composable
fun PhoneLayout(vm: MainViewModel) {
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { LibraryHeader(vm) },
            bottomBar = {
                Column {
                    MiniPlayer(vm)
                    NavigationBar {
                        NavItem(vm, Dest.MUSIC, "Müzik", Icons.Rounded.LibraryMusic)
                        NavItem(vm, Dest.VIDEO, "Video", Icons.Rounded.VideoLibrary)
                        NavItem(vm, Dest.FAVORITES, "Favoriler", Icons.Rounded.Favorite)
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) { LibraryContent(vm) }
        }
        AnimatedVisibility(
            visible = vm.showPlayer,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                PlayerPanel(vm, Modifier.fillMaxSize().systemBarsPadding(), onClose = { vm.showPlayer = false })
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(vm: MainViewModel, d: Dest, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    NavigationBarItem(
        selected = vm.dest == d,
        onClick = { vm.selectDest(d) },
        icon = { Icon(icon, null) },
        label = { Text(label) }
    )
}

@Composable
fun TabletLayout(vm: MainViewModel) {
    Row(Modifier.fillMaxSize()) {
        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
            Spacer(Modifier.height(12.dp))
            Image(painterResource(R.drawable.yanki_logo), null, Modifier.size(44.dp))
            Spacer(Modifier.height(16.dp))
            listOf(
                Triple(Dest.MUSIC, "Müzik", Icons.Rounded.LibraryMusic),
                Triple(Dest.VIDEO, "Video", Icons.Rounded.VideoLibrary),
                Triple(Dest.FAVORITES, "Favoriler", Icons.Rounded.Favorite)
            ).forEach { (d, l, i) ->
                NavigationRailItem(
                    selected = vm.dest == d,
                    onClick = { vm.selectDest(d) },
                    icon = { Icon(i, null) },
                    label = { Text(l) }
                )
            }
        }
        Column(Modifier.weight(0.42f).fillMaxHeight()) {
            LibraryHeader(vm)
            Box(Modifier.weight(1f).navigationBarsPadding()) { LibraryContent(vm) }
        }
        VerticalDivider()
        PlayerPanel(
            vm,
            Modifier.weight(0.58f).fillMaxHeight().systemBarsPadding(),
            onClose = null
        )
    }
}

@Composable
fun LibraryHeader(vm: MainViewModel) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(vm.searching) { if (vm.searching) runCatching { focus.requestFocus() } }
    val detail = vm.detail
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                vm.searching -> {
                    TextField(
                        value = vm.query,
                        onValueChange = { vm.query = it },
                        placeholder = { Text("Şarkı, sanatçı veya video ara…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    IconButton({ vm.query = ""; vm.searching = false }) { Icon(Icons.Rounded.Close, "Kapat") }
                }
                detail != null -> {
                    IconButton({ vm.detail = null }) { Icon(Icons.Rounded.ArrowBack, "Geri") }
                    val t = when (detail) {
                        is Detail.Folder -> detail.path.substringAfterLast('/')
                        is Detail.Artist -> detail.name
                        is Detail.Playlist -> detail.name
                    }
                    Text(t, style = MaterialTheme.typography.titleLarge, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (detail is Detail.Playlist) {
                        IconButton({ vm.deletePlaylist(detail.id); vm.detail = null }) {
                            Icon(Icons.Rounded.Delete, "Listeyi sil")
                        }
                    }
                }
                else -> {
                    Spacer(Modifier.width(8.dp))
                    Image(painterResource(R.drawable.yanki_logo), null, Modifier.size(32.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Yankı", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    if (vm.dest == Dest.MUSIC && vm.musicTab == 3) {
                        IconButton({ vm.playlistTarget = null; vm.showNewPlaylist = true }) {
                            Icon(Icons.Rounded.Add, "Yeni liste")
                        }
                    }
                    IconButton({ vm.searching = true }) { Icon(Icons.Rounded.Search, "Ara") }
                }
            }
        }
        if (detail == null && !vm.searching) {
            val tabs = when (vm.dest) {
                Dest.MUSIC -> listOf("Tüm Şarkılar", "Klasörler", "Sanatçılar", "Çalma Listeleri")
                Dest.VIDEO -> listOf("Tüm Videolar", "Klasörler")
                Dest.FAVORITES -> emptyList()
            }
            if (tabs.isNotEmpty()) {
                val sel = if (vm.dest == Dest.MUSIC) vm.musicTab else vm.videoTab
                ScrollableTabRow(
                    selectedTabIndex = sel.coerceIn(0, tabs.size - 1),
                    edgePadding = 8.dp,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    tabs.forEachIndexed { i, t ->
                        Tab(
                            selected = i == sel,
                            onClick = { if (vm.dest == Dest.MUSIC) vm.musicTab = i else vm.videoTab = i },
                            text = { Text(t) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryContent(vm: MainViewModel) {
    val ctx = LocalContext.current
    val audio by vm.audio.collectAsState()
    val videos by vm.videos.collectAsState()
    val favs by vm.favorites.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val plItems by vm.playlistItems.collectAsState()
    val progress by vm.progress.collectAsState()
    val ui by PlayerConnection.state.collectAsState()

    val byKey = remember(audio, videos) { (audio + videos).associateBy { it.key } }
    val q = vm.query.trim()
    val detail = vm.detail

    val onPlay: (List<MediaInfo>, MediaInfo) -> Unit = { list, item ->
        val queue = list.filter { it.isVideo == item.isVideo }
        vm.playList(ctx, queue, queue.indexOf(item).coerceAtLeast(0))
        if (item.isVideo) ctx.startActivity(Intent(ctx, VideoPlayerActivity::class.java))
    }

    val folders = remember(audio) {
        audio.groupBy { it.folderPath }.map { (p, l) ->
            Group(l.first().folderName, "${l.size} şarkı • $p", Detail.Folder(p, false))
        }.sortedBy { it.title.lowercase() }
    }
    val videoFolders = remember(videos) {
        videos.groupBy { it.folderPath }.map { (p, l) ->
            Group(l.first().folderName, "${l.size} video • $p", Detail.Folder(p, true))
        }.sortedBy { it.title.lowercase() }
    }
    val artists = remember(audio) {
        audio.groupBy { it.artist }.map { (n, l) -> Group(n, "${l.size} şarkı", Detail.Artist(n)) }
            .sortedBy { it.title.lowercase() }
    }
    val plGroups = remember(playlists, plItems) {
        playlists.map { p ->
            Group(p.name, "${plItems.count { it.playlistId == p.id }} öğe", Detail.Playlist(p.id, p.name))
        }
    }
    val detailList = remember(detail, audio, videos, plItems, byKey) {
        when (detail) {
            is Detail.Folder -> (if (detail.video) videos else audio).filter { it.folderPath == detail.path }
            is Detail.Artist -> audio.filter { it.artist == detail.name }
            is Detail.Playlist -> plItems.filter { it.playlistId == detail.id }.mapNotNull { byKey[it.mediaKey] }
            null -> emptyList()
        }
    }

    @Composable
    fun list(items: List<MediaInfo>) {
        val playlistId = (detail as? Detail.Playlist)?.id
        MediaList(
            list = items, currentKey = ui.key, favs = favs, progress = progress,
            onClick = { onPlay(items, it) },
            onFav = { vm.toggleFavorite(it.key) },
            onAdd = { vm.playlistTarget = it.key },
            onRemove = if (playlistId != null) ({ m: MediaInfo -> vm.removeFromPlaylist(playlistId, m.key) }) else null
        )
    }

    Box(Modifier.fillMaxSize()) {
        when {
            vm.loading && audio.isEmpty() && videos.isEmpty() ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            q.isNotEmpty() -> {
                val base = when (vm.dest) {
                    Dest.MUSIC -> audio
                    Dest.VIDEO -> videos
                    Dest.FAVORITES -> (audio + videos).filter { it.key in favs }
                }
                list(base.filter {
                    it.title.contains(q, true) || it.artist.contains(q, true) ||
                        it.album.contains(q, true) || it.folderName.contains(q, true)
                })
            }
            detail != null -> list(detailList)
            vm.dest == Dest.MUSIC -> when (vm.musicTab) {
                0 -> list(audio)
                1 -> GroupList(folders, Icons.Rounded.Folder) { vm.detail = it.detail }
                2 -> GroupList(artists, Icons.Rounded.Person) { vm.detail = it.detail }
                else -> GroupList(plGroups, Icons.Rounded.QueueMusic, "Henüz çalma listesi yok. Üstteki + ile oluştur.") {
                    vm.detail = it.detail
                }
            }
            vm.dest == Dest.VIDEO -> when (vm.videoTab) {
                0 -> list(videos)
                else -> GroupList(videoFolders, Icons.Rounded.Folder) { vm.detail = it.detail }
            }
            else -> list((audio + videos).filter { it.key in favs }.sortedBy { it.title.lowercase() })
        }
    }
}

@Composable
fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun GroupList(
    groups: List<Group>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyText: String = "Burada gösterilecek bir şey yok",
    onClick: (Group) -> Unit
) {
    if (groups.isEmpty()) { EmptyMessage(emptyText); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        items(groups, key = { it.detail.toString() }) { g ->
            Row(
                Modifier.fillMaxWidth().clickable { onClick(g) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(g.subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MediaList(
    list: List<MediaInfo>,
    currentKey: String?,
    favs: Set<String>,
    progress: Map<String, ProgressEntity>,
    onClick: (MediaInfo) -> Unit,
    onFav: (MediaInfo) -> Unit,
    onAdd: (MediaInfo) -> Unit,
    onRemove: ((MediaInfo) -> Unit)?
) {
    if (list.isEmpty()) { EmptyMessage("Burada gösterilecek bir şey yok"); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        items(list, key = { it.key }) { m ->
            MediaRow(
                m = m,
                current = m.key == currentKey,
                fav = m.key in favs,
                progress = progress[m.key],
                onClick = { onClick(m) },
                onFav = { onFav(m) },
                onAdd = { onAdd(m) },
                onRemove = onRemove?.let { r -> { r(m) } }
            )
        }
    }
}

@Composable
fun Thumb(model: Any?, video: Boolean, modifier: Modifier, radius: Dp = 12.dp) {
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (video) Icons.Rounded.Movie else Icons.Rounded.MusicNote, null,
            tint = MaterialTheme.colorScheme.outline
        )
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun MediaRow(
    m: MediaInfo,
    current: Boolean,
    fav: Boolean,
    progress: ProgressEntity?,
    onClick: () -> Unit,
    onFav: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (() -> Unit)?
) {
    var menu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(m.thumb, m.isVideo, Modifier.size(54.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                m.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (current) scheme.primary else scheme.onSurface,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal
            )
            val sub = (if (m.isVideo) m.folderName else m.artist) + " • " + formatTime(m.durationMs)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (progress != null && progress.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp).height(2.dp)
                )
            }
        }
        IconButton(onFav) {
            Icon(
                if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favori",
                tint = if (fav) Color(0xFFFF6B8A) else scheme.onSurfaceVariant
            )
        }
        Box {
            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Daha fazla") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Çalma listesine ekle") }, onClick = { menu = false; onAdd() })
                if (onRemove != null) {
                    DropdownMenuItem(text = { Text("Listeden çıkar") }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(vm: MainViewModel) {
    val ui by PlayerConnection.state.collectAsState()
    val ctx = LocalContext.current
    if (ui.key == null) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable {
            if (ui.isVideo) ctx.startActivity(Intent(ctx, VideoPlayerActivity::class.java))
            else vm.showPlayer = true
        }
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(ui.thumb, ui.isVideo, Modifier.size(44.dp), 10.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(ui.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(ui.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton({ PlayerConnection.controller.value?.seekToPrevious() }) { Icon(Icons.Rounded.SkipPrevious, "Önceki") }
            IconButton({ PlayerConnection.togglePlay() }) {
                Icon(if (ui.showPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Oynat/Duraklat",
                    tint = MaterialTheme.colorScheme.primary)
            }
            IconButton({ PlayerConnection.controller.value?.seekToNext() }) { Icon(Icons.Rounded.SkipNext, "Sonraki") }
        }
    }
}
