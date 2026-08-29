package com.minimarket.aepos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.minimarket.aepos.ui.auth.AuthViewModel
import com.minimarket.aepos.ui.auth.LoginScreen
import com.minimarket.aepos.ui.inventory.InventoryViewModel
import com.minimarket.aepos.ui.navigation.AdaptivePosScaffold
import com.minimarket.aepos.ui.reports.ReportsViewModel
import com.minimarket.aepos.ui.shopping.ShoppingListViewModel
import com.minimarket.aepos.ui.theme.AEPOSTheme
import com.minimarket.aepos.ui.theme.Emerald500
import com.minimarket.aepos.ui.theme.Slate950
import com.minimarket.aepos.ui.users.UsersViewModel

class MainActivity : ComponentActivity() {

    private val app by lazy { application as AEPosApplication }

    private val authViewModel: AuthViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel(app.authRepository) as T
            }
        }
    }

    private val inventoryViewModel: InventoryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return InventoryViewModel(app.productRepository) as T
            }
        }
    }

    private val shoppingViewModel: ShoppingListViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ShoppingListViewModel(app.productRepository) as T
            }
        }
    }

    private val reportsViewModel: ReportsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReportsViewModel(app.saleRepository) as T
            }
        }
    }

    private val usersViewModel: UsersViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return UsersViewModel(app.userRepository) as T
            }
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val authState by authViewModel.uiState.collectAsState()

            AEPOSTheme {
                if (authState.isLoading && !authState.isAuthenticated) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Slate950),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Emerald500,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                } else if (!authState.isAuthenticated || authState.currentUser == null) {
                    LoginScreen(viewModel = authViewModel)
                } else {
                    AdaptivePosScaffold(
                        currentUser = authState.currentUser!!,
                        windowWidthSizeClass = windowSizeClass.widthSizeClass,
                        inventoryViewModel = inventoryViewModel,
                        shoppingViewModel = shoppingViewModel,
                        reportsViewModel = reportsViewModel,
                        usersViewModel = usersViewModel,
                        syncService = app.syncService,
                        onLogout = { authViewModel.logout() }
                    )
                }
            }
        }
    }
}

