package dev.asimonyan.reposcout.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.asimonyan.reposcout.data.Repo
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoutScreen(model: ScoutViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val favorites by model.favorites.collectAsStateWithLifecycle()
    val favoriteIds = remember(favorites) { favorites.map { it.id }.toSet() }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Repo?>(null) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(model) { model.messages.collect { snack.showSnackbar(it) } }
    val visible = if (savedOnly) favorites else state.items
    Scaffold(topBar = { TopAppBar(title = { Text("RepoScout", fontWeight = FontWeight.Bold) },
        actions = { Icon(Icons.Outlined.TravelExplore, null, Modifier.padding(end = 20.dp)) }) },
        snackbarHost = { SnackbarHost(snack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Найди код.\nРазберись в идее.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Исследуй открытые проекты на GitHub.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !savedOnly, onClick = { savedOnly = false }, label = { Text("Поиск") })
                    FilterChip(selected = savedOnly, onClick = { savedOnly = true }, label = { Text("Избранное · ${favorites.size}") })
                }
            }
            if (!savedOnly) {
                item {
                    OutlinedTextField(value = state.query, onValueChange = { if (it.length <= 256) model.setQuery(it) },
                        label = { Text("Название, тема или language:kotlin") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { model.setQuery("") }) { Icon(Icons.Outlined.Close, "Очистить") } },
                        modifier = Modifier.fillMaxWidth())
                }
                if (state.query.isBlank()) item {
                    Text("Начни с запроса", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("compose", "android", "algorithms").forEach { term ->
                            SuggestionChip(onClick = { model.setQuery(term) }, label = { Text(term) })
                        }
                    }
                    Text("Избранное сохраняется только на устройстве. Вход в GitHub не нужен.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.cachedAt?.let { timestamp -> item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                        Text("Показаны сохранённые данные · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))}", Modifier.padding(12.dp))
                    }
                } }
                state.warning?.let { warning -> item { Text(warning, style = MaterialTheme.typography.bodySmall) } }
                if (state.incomplete) item { Text("GitHub вернул неполные результаты. Попробуйте уточнить запрос.") }
                state.error?.let { error -> item {
                    OutlinedCard { Column(Modifier.padding(16.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = model::retry, enabled = !state.loading) { Text("Повторить") }
                    } }
                } }
                if (state.page > 0) item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${state.items.size} из ${state.total}", style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = model::refresh, enabled = !state.loading) { Text("Обновить") }
                    }
                }
            }
            if (visible.isEmpty() && (savedOnly || (!state.loading && state.page > 0))) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Bookmarks, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(if (savedOnly) "Сохрани интересный проект" else "Ничего не найдено", style = MaterialTheme.typography.titleMedium)
                    Text(if (savedOnly) "Нажми на закладку в карточке." else "Попробуй изменить запрос.")
                }
            }
            items(visible, key = { it.id }) { repo ->
                OutlinedCard(onClick = { selected = repo }, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(repo.fullName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { model.toggle(repo) }) {
                                Icon(if (repo.id in favoriteIds) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                    if (repo.id in favoriteIds) "Убрать из избранного" else "Сохранить проект", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text(repo.description ?: "Описание не указано", maxLines = 3, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("★ ${repo.stars}   ·   ${repo.language ?: "Язык не указан"}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (!savedOnly && state.loading) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            if (!savedOnly && state.more && state.error == null) item {
                OutlinedButton(onClick = model::next, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Загрузить ещё") }
            }
            if (!savedOnly && !state.more && state.items.isNotEmpty() && state.total > 1000) item {
                Text("GitHub позволяет просматривать первые 1 000 результатов. Уточните запрос.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    selected?.let { repo ->
        val uriHandler = LocalUriHandler.current
        var openError by remember(repo.id) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(repo.fullName) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(repo.description ?: "Описание не указано")
                Text("Язык: ${repo.language ?: "—"}\nЗвёзды: ${repo.stars}\nФорки: ${repo.forks}")
                if (openError) Text("Не удалось открыть браузер.", color = MaterialTheme.colorScheme.error)
            } }, confirmButton = { TextButton(onClick = {
                try { if (repo.url.startsWith("https://github.com/")) uriHandler.openUri(repo.url) else openError = true }
                catch (_: Exception) { openError = true }
            }) { Text("Открыть GitHub") } }, dismissButton = { TextButton(onClick = { selected = null }) { Text("Закрыть") } })
    }
}
