package com.minimarket.aepos.data.repository

import com.minimarket.aepos.data.local.AppDatabase
import com.minimarket.aepos.data.local.entity.UserEntity
import com.minimarket.aepos.data.remote.FirestoreSyncService
import com.minimarket.aepos.domain.model.User
import com.minimarket.aepos.domain.model.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

class UserRepository(
    private val db: AppDatabase,
    private val syncService: FirestoreSyncService,
    private val scope: CoroutineScope
) {
    private val userDao = db.userDao()

    val allUsersFlow: Flow<List<User>> = userDao.getAllUsersFlow().map { list ->
        list.map { it.toDomain() }
    }

    val activeUsersFlow: Flow<List<User>> = userDao.getAllActiveUsersFlow().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun loginByPin(pin: String): User? {
        return userDao.getUserByPin(pin.trim())?.toDomain()
    }

    suspend fun getUserById(id: String): User? {
        return userDao.getUserById(id)?.toDomain()
    }

    suspend fun saveUser(user: User) {
        userDao.insertOrUpdate(user.toEntity())
        scope.launch(Dispatchers.IO) {
            syncService.uploadUser(user)
        }
    }

    suspend fun deleteUser(id: String) {
        userDao.deleteUser(id)
        scope.launch(Dispatchers.IO) {
            syncService.deleteUser(id)
        }
    }

    private fun UserEntity.toDomain() = User(
        id = id,
        username = username,
        nombreCompleto = nombreCompleto,
        email = email,
        pin = pin,
        role = if (role.lowercase() == "admin") UserRole.ADMIN else UserRole.COLABORADOR,
        permisos = if (role.lowercase() == "admin" || permisos == "all") {
            listOf("all")
        } else if (permisos.isBlank()) {
            listOf(User.PERM_INVENTORY_READ, "inventario:consultar")
        } else {
            permisos.split(",").map { it.trim() }.filter { it.isNotBlank() }
        },
        activo = activo == 1
    )

    private fun User.toEntity() = UserEntity(
        id = id.ifBlank { UUID.randomUUID().toString() },
        username = username,
        nombreCompleto = nombreCompleto,
        email = email,
        pin = pin,
        role = if (role == UserRole.ADMIN) "admin" else "colaborador",
        permisos = if (role == UserRole.ADMIN || permisos.contains("all")) "all" else if (permisos.isEmpty()) User.PERM_INVENTORY_READ else permisos.joinToString(","),
        activo = if (activo) 1 else 0
    )
}
