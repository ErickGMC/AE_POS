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
        CashRepository(database, applicationScope)
    }

    val userRepository by lazy {
        UserRepository(database, syncService, applicationScope)
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseConfig.init(this)
        syncService.startRealtimeProductsListener()
        syncService.startRealtimeUsersListener()
    }
}
