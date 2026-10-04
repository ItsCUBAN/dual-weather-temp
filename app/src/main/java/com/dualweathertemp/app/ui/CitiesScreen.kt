package com.dualweathertemp.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dualweathertemp.app.R
import com.dualweathertemp.app.data.Places
import com.dualweathertemp.app.data.SavedPlace
import com.dualweathertemp.app.sky.dayPhase
import com.dualweathertemp.app.ui.sections.GlassCard
import com.dualweathertemp.app.ui.sections.ScreenTopBar
import com.dualweathertemp.app.ui.sections.SectionTitle
import com.dualweathertemp.app.ui.sections.secondaryContentColor

/** Search US cities, save up to [Places.MAX_SAVED] of them, and jump to any saved page. */
@Composable
fun CitiesScreen(
    state: WeatherUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onAdd: (SavedPlace) -> Unit,
    onRemove: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val search = state.search
    val savedIds = state.savedPlaces.map { it.id }.toSet()
    val isFull = state.savedPlaces.size >= Places.MAX_SAVED

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTopBar(title = stringResource(R.string.saved_cities), onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { SearchField(search.query, onQueryChange) }
            item { SearchResults(search, savedIds, isFull, onAdd) }

            item {
                Spacer(Modifier.padding(top = 4.dp))
                SectionTitle(stringResource(R.string.your_cities))
            }
            item {
                CityRow(
                    title = state.title(Places.CURRENT_ID) ?: stringResource(R.string.my_location),
                    subtitle = stringResource(R.string.my_location),
                    place = state.place(Places.CURRENT_ID),
                    isCurrentLocation = true,
                    onClick = { onOpen(Places.CURRENT_ID) },
                    onRemove = null,
                )
            }
            items(state.savedPlaces, key = { it.id }) { place ->
                CityRow(
                    title = place.shortLabel,
                    subtitle = null,
                    place = state.place(place.id),
                    isCurrentLocation = false,
                    onClick = { onOpen(place.id) },
                    onRemove = { onRemove(place.id) },
                )
            }
            item {
                Text(
                    text = if (isFull) {
                        stringResource(R.string.cities_full, Places.MAX_SAVED)
                    } else {
                        stringResource(R.string.cities_hint, Places.MAX_SAVED)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryContentColor(),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val palette = LocalPalette.current
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = palette.content,
            unfocusedTextColor = palette.content,
            cursorColor = palette.content,
            focusedBorderColor = palette.content,
            unfocusedBorderColor = palette.content.copy(alpha = 0.5f),
            focusedLeadingIconColor = palette.content,
            unfocusedLeadingIconColor = palette.content,
            focusedTrailingIconColor = palette.content,
            unfocusedTrailingIconColor = palette.content,
            focusedPlaceholderColor = palette.content.copy(alpha = 0.7f),
            unfocusedPlaceholderColor = palette.content.copy(alpha = 0.7f),
            focusedContainerColor = palette.card,
            unfocusedContainerColor = palette.card,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SearchResults(search: SearchState, savedIds: Set<String>, isFull: Boolean, onAdd: (SavedPlace) -> Unit) {
    val query = search.query.trim()
    if (query.isEmpty()) return

    val message = when {
        query.length < Places.MIN_SEARCH_LENGTH -> stringResource(R.string.search_min_chars, Places.MIN_SEARCH_LENGTH)
        search.error != null -> stringResource(WeatherText.errorRes(search.error))
        !search.isSearching && search.results.isEmpty() -> stringResource(R.string.search_no_results)
        else -> null
    }
    GlassCard {
        when {
            message != null -> Text(message, style = MaterialTheme.typography.bodyMedium)
            search.isSearching && search.results.isEmpty() ->
                CircularProgressIndicator(modifier = Modifier.size(20.dp).align(Alignment.CenterHorizontally), strokeWidth = 2.dp)
            else -> search.results.forEachIndexed { index, place ->
                if (index > 0) HorizontalDivider(color = secondaryContentColor().copy(alpha = 0.3f))
                val saved = place.id in savedIds
                val addLabel = stringResource(R.string.add_city, place.longLabel)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !saved && !isFull, onClickLabel = addLabel) { onAdd(place) }
                        .padding(vertical = 10.dp),
                ) {
                    Text(place.name, fontWeight = FontWeight.Medium)
                    Text(", ${place.state}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    when {
                        saved -> Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.already_saved))
                        !isFull -> Icon(Icons.Outlined.Add, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun CityRow(
    title: String,
    subtitle: String?,
    place: PlaceState,
    isCurrentLocation: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val order = LocalUnitOrder.current
    val report = place.report

    GlassCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrentLocation) {
                        Icon(Icons.Outlined.NearMe, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val detail = listOfNotNull(
                    subtitle,
                    report?.let {
                        stringResource(WeatherText.conditionRes(it.current.condition, it.dayPhase(System.currentTimeMillis())))
                    },
                ).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = secondaryContentColor())
                }
            }
            Text(
                text = report?.let { WeatherText.dualTemp(it.current.celsius, order).replace(" / ", " | ") } ?: "--",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.remove_city, title))
                }
            }
        }
    }
}
