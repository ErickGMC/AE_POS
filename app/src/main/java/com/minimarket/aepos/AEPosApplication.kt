package com.minimarket.aepos

import android.app.Application
import com.minimarket.aepos.data.local.AppDatabase
import com.minimarket.aepos.data.remote.FirebaseConfig
import com.minimarket.aepos.data.remote.FirestoreSyncService
import com.minimarket.aepos.data.repository.AuthRepository
import com.minimarket.aepos.data.repository.CashRepository
import com.minimarket.aepos.data.repository.ProductRepository
import com.minimarket.aepos.data.repository.SaleRepository
import com.minimarket.aepos.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AEPosApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    
    val syncService by lazy { 
        FirestoreSyncService(database, applicationScope)
    }

    val authRepository by lazy {
        AuthRepository(database)
    }

    val productRepository by lazy { 
        ProductRepository(database, syncService, applicationScope) 
    }

    val saleRepository by lazy { 
        SaleRepository(database, syncService, applicationScope) 
    }

    val cashRepository by lazy {
        CashRepository(database, syncService, applicationScope)
    }

    val userRepository by lazy {
        UserRepository(database, syncService, applicationScope)
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseConfig.init(this)
        syncService.startRealtimeProductsListener()
        syncService.startRealtimeUsersListener()
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    applicationScope.launch(Dispatchers.IO) {
                        syncService.syncPendingOutbox()
                    }
                }
            })
        } catch (_: Exception) {}
    }
}
