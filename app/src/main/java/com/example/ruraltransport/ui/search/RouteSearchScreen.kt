package com.example.ruraltransport.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ruraltransport.data.model.LiveDriverPosition
import com.example.ruraltransport.data.model.RouteDirection
import com.example.ruraltransport.data.model.RouteInfo
import com.example.ruraltransport.data.model.TransportStop
import com.example.ruraltransport.ui.home.PassengerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSearchScreen(
    searchViewModel: RouteSearchViewModel = viewModel(),
    passengerViewModel: PassengerViewModel,
    onBack: () -> Unit,
    onNavigateToLiveRoute: (route: RouteInfo, direction: RouteDirection, pickup: TransportStop, dest: TransportStop, selectedDriver: LiveDriverPosition?) -> Unit
) {
    val uiState by searchViewModel.uiState.collectAsState()
    val waitingState by passengerViewModel.waitingState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Search by source and destination",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Find autos & routes between any stops",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Search Form Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Select Corridor Stops",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Source Selector
                        SearchStopSelector(
                            label = "From (Source Stop or Route)",
                            selectedStop = uiState.sourceStop,
                            searchableItems = uiState.searchableItems,
                            icon = Icons.Default.LocationOn,
                            onItemSelected = { searchViewModel.selectSearchableItem(it, isSource = true) }
                        )

                        // Swap Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            IconButton(
                                onClick = { searchViewModel.swapStops() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(
                                    Icons.Default.SwapVert,
                                    contentDescription = "Swap Stops",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Destination Selector
                        SearchStopSelector(
                            label = "To (Destination Stop)",
                            selectedStop = uiState.destinationStop,
                            searchableItems = uiState.searchableItems,
                            icon = Icons.Default.LocationOn,
                            onItemSelected = { searchViewModel.selectSearchableItem(it, isSource = false) }
                        )

                        if (uiState.error != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = uiState.error ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Button(
                            onClick = { searchViewModel.searchRoutes() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !uiState.isSearching
                        ) {
                            if (uiState.isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Searching Corridors...")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Search Autos", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // OUTCOMES BRANCHING
            when (val outcome = uiState.outcome) {
                is SearchOutcome.Idle -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Choose your origin and destination stops above to see operating autos or nearby alternatives.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is SearchOutcome.ResultsFound -> {
                    // Direct Matches
                    if (outcome.directMatches.isNotEmpty()) {
                        item {
                            Text(
                                text = "Direct Corridor Routes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        outcome.directMatches.forEach { directMatch ->
                            item {
                                DirectMatchCard(
                                    match = directMatch,
                                    onNavigateToLiveRoute = onNavigateToLiveRoute,
                                    waitingState = waitingState,
                                    onStartWaiting = { pickup, dest, routeId ->
                                        passengerViewModel.startWaiting(pickup, dest, routeId)
                                    },
                                    onStopWaiting = { passengerViewModel.stopWaiting() }
                                )
                            }
                        }
                    }

                    // Nearby Fallbacks
                    if (outcome.nearbyFallbacks.isNotEmpty()) {
                        item {
                            Text(
                                text = "Nearby Operating Stops",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        items(outcome.nearbyFallbacks) { fallback ->
                            NearbyMatchCard(
                                fallback = fallback,
                                onNavigateToLiveRoute = onNavigateToLiveRoute
                            )
                        }
                    }
                }

                is SearchOutcome.NoResults -> {
                    item {
                        NoResultsView()
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun DirectMatchCard(
    match: DirectRouteMatch,
    onNavigateToLiveRoute: (route: RouteInfo, direction: RouteDirection, pickup: TransportStop, dest: TransportStop, selectedDriver: LiveDriverPosition?) -> Unit,
    waitingState: com.example.ruraltransport.ui.home.WaitingUiState,
    onStartWaiting: (String, String, String) -> Unit,
    onStopWaiting: () -> Unit
) {
    val result = match.matchResult
    val drivers = match.matchingDrivers

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = result.route.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${result.sourceStop.name} → ${result.destStop.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = result.direction.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            HorizontalDivider(alpha = 0.1f)

            if (drivers.isNotEmpty()) {
                Text(
                    text = "${drivers.size} auto(s) operating on this corridor",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2E7D32)
                )

                Button(
                    onClick = { onNavigateToLiveRoute(result.route, result.direction, result.sourceStop, result.destStop, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Live Corridor Map")
                }
            } else {
                Text(
                    text = "No autos currently active in this direction.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                
                val isWaitingHere = waitingState.isWaiting && waitingState.routeId == result.route.id && 
                        waitingState.pickupStop.equals(result.sourceStop.name, ignoreCase = true)

                if (!isWaitingHere) {
                    OutlinedButton(
                        onClick = { onStartWaiting(result.sourceStop.name, result.destStop.name, result.route.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (waitingState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("I'm Waiting Here")
                        }
                    }
                } else {
                    Button(
                        onClick = onStopWaiting,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cancel Wait")
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyMatchCard(
    fallback: NearbyRouteStopMatch,
    onNavigateToLiveRoute: (route: RouteInfo, direction: RouteDirection, pickup: TransportStop, dest: TransportStop, selectedDriver: LiveDriverPosition?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable {
             onNavigateToLiveRoute(fallback.route, RouteDirection.FORWARD, fallback.nearbyStop, fallback.route.stops.last(), fallback.operatingDrivers.firstOrNull())
        },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = fallback.nearbyStop.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text = "Corridor: ${fallback.route.name}", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "${fallback.operatingDrivers.size} active auto(s) • ~${"%.1f".format(fallback.distanceKm)} km away",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun NoResultsView() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "No Routes Found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "We couldn't find any direct or nearby corridors for this journey. Try selecting different stops or check back later.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SearchStopSelector(
    label: String,
    selectedStop: TransportStop?,
    searchableItems: List<SearchableItem>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onItemSelected: (SearchableItem) -> Unit
) {
    var searchQuery by remember(selectedStop) { mutableStateOf(selectedStop?.name ?: "") }
    var isExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val filteredItems = remember(searchQuery, searchableItems) {
        if (searchQuery.isBlank()) {
            searchableItems.filterIsInstance<SearchableItem.Stop>()
        } else {
            searchableItems.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))

        Box {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    isExpanded = true
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Type stop or route name...") },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            isExpanded = true
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Clear")
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            if (isExpanded && filteredItems.isNotEmpty()) {
                DropdownMenu(
                    expanded = isExpanded,
                    onDismissRequest = { isExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f),
                    properties = PopupProperties(focusable = false)
                ) {
                    filteredItems.take(15).forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (item is SearchableItem.Route) Icons.Default.DirectionsCar else Icons.Default.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (item is SearchableItem.Route) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.displayName,
                                        fontWeight = if (item is SearchableItem.Stop && item.stop.id == selectedStop?.id) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (item is SearchableItem.Route) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "(Route)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            },
                            onClick = {
                                searchQuery = item.displayName
                                onItemSelected(item)
                                isExpanded = false
                                focusManager.clearFocus()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HorizontalDivider(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outlineVariant, alpha: Float = 1f) {
    Spacer(modifier = modifier.fillMaxWidth().height(1.dp).background(color.copy(alpha = alpha)))
}
