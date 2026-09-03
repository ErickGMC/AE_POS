package com.minimarket.aepos.data.local.dao

import androidx.room.*
import com.minimarket.aepos.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM usuarios WHERE activo = 1 ORDER BY username ASC")
    fun getAllActiveUsersFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM usuarios ORDER BY username ASC")
    fun getAllUsersFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM usuarios WHERE pin = :pin AND activo = 1 LIMIT 1")
    suspend fun getUserByPin(pin: String): UserEntity?

    @Query("SELECT * FROM usuarios WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<UserEntity>)

    @Query("DELETE FROM usuarios WHERE id = :id")
    suspend fun deleteUser(id: String)

    @Query("DELETE FROM usuarios")
    suspend fun deleteAllUsers()

    @Transaction
    suspend fun syncUsers(users: List<UserEntity>) {
        deleteAllUsers()
        if (users.isNotEmpty()) {
            insertAll(users)
        }
    }
}
