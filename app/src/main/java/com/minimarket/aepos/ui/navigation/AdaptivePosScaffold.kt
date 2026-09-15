package com.minimarket.aepos.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimarket.aepos.data.remote.FirestoreSyncService
import com.minimarket.aepos.data.remote.SyncStateStatus
import com.minimarket.aepos.domain.model.User
import com.minimarket.aepos.ui.cash.CashScreen
import com.minimarket.aepos.ui.cash.CashViewModel
import com.minimarket.aepos.ui.inventory.InventoryScreen
import com.minimarket.aepos.ui.inventory.InventoryViewModel
import com.minimarket.aepos.ui.reports.ReportsScreen
import com.minimarket.aepos.ui.reports.ReportsViewModel
import com.minimarket.aepos.ui.sales.PhoneSalesScreen
import com.minimarket.aepos.ui.sales.SalesViewModel
import com.minimarket.aepos.ui.sales.TabletSalesScreen
import com.minimarket.aepos.ui.shopping.ShoppingListScreen
import com.minimarket.aepos.ui.shopping.ShoppingListViewModel
import com.minimarket.aepos.ui.users.UserSwitchDialog
import com.minimarket.aepos.ui.users.UsersScreen
import com.minimarket.aepos.ui.users.UsersViewModel
import kotlinx.coroutines.launch

enum class PosDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    SALES("sales", "Vender", Icons.Default.PointOfSale),
    CASH("cash", "Caja", Icons.Default.AccountBalanceWallet),
    PRODUCTS("products", "Inventario", Icons.Default.Inventory2),
    SALES_HISTORY("sales_history", "Ventas PC", Icons.AutoMirrored.Filled.ReceiptLong),
    SHOPPING("shopping", "Compras", Icons.AutoMirrored.Filled.FactCheck),
    USERS("users", "Personal", Icons.Default.Group)
}

@Composable
fun AdaptivePosScaffold(
    currentUser: User,
    windowWidthSizeClass: WindowWidthSizeClass,
    salesViewModel: SalesViewModel,
    cashViewModel: CashViewModel,
    inventoryViewModel: InventoryViewModel,
    shoppingViewModel: ShoppingListViewModel,
    reportsViewModel: ReportsViewModel,
    usersViewModel: UsersViewModel,
    syncService: FirestoreSyncService,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val usersState by usersViewModel.uiState.collectAsState()
    val activeUser = usersState.currentUser.takeIf { it != null && it.id.isNotBlank() && it.activo } ?: currentUser

    LaunchedEffect(currentUser) {
        usersViewModel.setCurrentUser(currentUser)
    }

    // Filtrar destinos basados en permisos
    val visibleDestinations = remember(activeUser) {
        PosDestination.entries.filter { dest ->
            when (dest) {
                PosDestination.SALES -> activeUser.hasPermission(User.PERM_SALES_WRITE) || activeUser.isAdmin
                PosDestination.CASH -> activeUser.hasPermission(User.PERM_SALES_WRITE) || activeUser.isAdmin
                PosDestination.PRODUCTS -> activeUser.hasPermission(User.PERM_INVENTORY_READ) || activeUser.isAdmin
                PosDestination.SALES_HISTORY -> activeUser.hasPermission(User.PERM_REPORTS_VIEW) || activeUser.isAdmin
                PosDestination.SHOPPING -> activeUser.hasPermission(User.PERM_SHOPPING_MANAGE) || activeUser.isAdmin
                PosDestination.USERS -> activeUser.isAdmin || activeUser.hasPermission(User.PERM_USERS_MANAGE)
            }
        }
    }

    var currentDestination by remember(visibleDestinations) { 
        mutableStateOf(visibleDestinations.firstOrNull() ?: PosDestination.SALES) 
    }

    LaunchedEffect(visibleDestinations) {
        if (currentDestination !in visibleDestinations) {
            currentDestination = visibleDestinations.firstOrNull() ?: PosDestination.SALES
        }
    }
    
    val syncStatus by syncService.syncStatus.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val isTablet = windowWidthSizeClass != WindowWidthSizeClass.Compact

    if (isTablet) {
        // LAYOUT TABLET (Navigation Rail Lateral + Contenido) Material 3
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                header = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "AE POS",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Perfil de Usuario Rápido
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable { usersViewModel.openUserSwitch() }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (activeUser.isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (activeUser.isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activeUser.username,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxHeight()
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                visibleDestinations.forEach { destination ->
                    val isSelected = currentDestination == destination
                    NavigationRailItem(
                        selected = isSelected,
                        onClick = { currentDestination = destination },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = destination.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Sync Status Badge
                Box(modifier = Modifier.padding(8.dp)) {
                    SyncIndicatorBadge(
                        status = syncStatus.status,
                        onClick = {
                            coroutineScope.launch { syncService.syncAllNow() }
                        }
                    )
                }

                IconButton(
                    onClick = onLogout,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Cerrar Sesión",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                when (currentDestination) {
                    PosDestination.SALES -> TabletSalesScreen(viewModel = salesViewModel)
                    PosDestination.CASH -> CashScreen(viewModel = cashViewModel, currentUser = activeUser)
                    PosDestination.PRODUCTS -> InventoryScreen(viewModel = inventoryViewModel, currentUser = activeUser)
                    PosDestination.SALES_HISTORY -> ReportsScreen(viewModel = reportsViewModel, currentUser = activeUser)
                    PosDestination.SHOPPING -> ShoppingListScreen(viewModel = shoppingViewModel)
                    PosDestination.USERS -> UsersScreen(viewModel = usersViewModel, currentUser = activeUser)
                }
            }
        }
    } else {
        // LAYOUT SMARTPHONE (Navegación Inferior + Top Header Sync & User Profile) Material 3
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Badge de la tienda y usuario
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { usersViewModel.openUserSwitch() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (activeUser.isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (activeUser.isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "AE POS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = activeUser.username,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Sync Cloud y Logout
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SyncIndicatorBadge(
                                status = syncStatus.status,
                                onClick = {
                                    coroutineScope.launch { syncService.syncAllNow() }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = onLogout,
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Cerrar Sesión",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (visibleDestinations.size > 1) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 6.dp
                    ) {
                        visibleDestinations.forEach { destination ->
                            val isSelected = currentDestination == destination
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { currentDestination = destination },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.title,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = destination.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentDestination) {
                    PosDestination.SALES -> PhoneSalesScreen(viewModel = salesViewModel)
                    PosDestination.CASH -> CashScreen(viewModel = cashViewModel, currentUser = activeUser)
                    PosDestination.PRODUCTS -> InventoryScreen(viewModel = inventoryViewModel, currentUser = activeUser)
                    PosDestination.SALES_HISTORY -> ReportsScreen(viewModel = reportsViewModel, currentUser = activeUser)
                    PosDestination.SHOPPING -> ShoppingListScreen(viewModel = shoppingViewModel)
                    PosDestination.USERS -> UsersScreen(viewModel = usersViewModel, currentUser = activeUser)
                }
            }
        }
    }

    if (usersState.isUserSwitchOpen) {
        UserSwitchDialog(
            errorMessage = usersState.errorMessage,
            onDismiss = { usersViewModel.closeUserSwitch() },
            onPinSubmitted = { pin -> usersViewModel.switchUserWithPin(pin) }
        )
    }
}

@Composable
fun SyncIndicatorBadge(
    status: SyncStateStatus,
    onClick: () -> Unit
) {
    val (color, label, icon) = when (status) {
        SyncStateStatus.SYNCED -> Triple(MaterialTheme.colorScheme.primary, "Cloud OK", Icons.Default.CloudDone)
        SyncStateStatus.SYNCING -> Triple(MaterialTheme.colorScheme.tertiary, "Sync...", Icons.Default.CloudSync)
        SyncStateStatus.OFFLINE -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, "Offline", Icons.Default.CloudOff)
        SyncStateStatus.ERROR -> Triple(MaterialTheme.colorScheme.error, "Reintentar", Icons.Default.SyncProblem)
    }

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
