package com.minimarket.aepos.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.SetOptions
import com.minimarket.aepos.data.local.AppDatabase
import com.minimarket.aepos.data.local.entity.UserEntity
import com.minimarket.aepos.data.remote.FirebaseConfig
import com.minimarket.aepos.domain.model.User
import com.minimarket.aepos.domain.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AuthRepository(
    private val db: AppDatabase
) {
    private val auth: FirebaseAuth by lazy { FirebaseConfig.auth }
    private val firestore by lazy { FirebaseConfig.firestore }
    private val userDao = db.userDao()

    /**
     * Construye un correo válido de Firebase Auth si el usuario ingresó solo el username.
     * Idéntico a buildAuthEmail en tienda-pos.
     */
    private fun buildAuthEmail(identifier: String): String {
        val trimmed = identifier.trim()
        if (trimmed.contains("@")) return trimmed
        return "$trimmed@${FirebaseConfig.PROJECT_ID}.com"
    }

    /**
     * Autenticación contra Firebase Authentication + Recuperación de perfil en Firestore.
     */
    suspend fun loginWithFirebase(
        identifier: String,
        password: String
    ): Result<User> = withContext(Dispatchers.IO) {
        try {
            val loginEmail = buildAuthEmail(identifier)

            // 1. Autenticación con Firebase Auth
            val authResult = auth.signInWithEmailAndPassword(loginEmail, password).await()
            val firebaseUser = authResult.user ?: return@withContext Result.failure(Exception("Error al autenticar usuario"))
            val uid = firebaseUser.uid
            val email = firebaseUser.email ?: loginEmail

            // 2. Recuperar perfil desde Firestore / SQLite
            val identifierClean = identifier.trim()
            val isExplicitAdmin = identifierClean.equals("admin", ignoreCase = true) || identifierClean.startsWith("admin@", ignoreCase = true)
            var roleStr = if (isExplicitAdmin) "admin" else "colaborador"
            var nombreCompleto = identifierClean
            var username = identifierClean.substringBefore("@")
            var pin = "1234"
            var permisosList: List<String> = if (isExplicitAdmin) listOf("all") else listOf(User.PERM_INVENTORY_READ, "inventario:consultar")
            var activo = true

            try {
                // Intentar por UID del doc
                var doc = firestore.collection("usuarios").document(uid).get().await()
                
                // Si no existe con ese UID, buscar por username o email en Firestore
                if (!doc.exists()) {
                    val queryUser = firestore.collection("usuarios")
                        .whereEqualTo("username", username)
                        .limit(1).get().await()
                    if (!queryUser.isEmpty) {
                        doc = queryUser.documents.first()
                    } else {
                        val queryEmail = firestore.collection("usuarios")
                            .whereEqualTo("email", loginEmail)
                            .limit(1).get().await()
                        if (!queryEmail.isEmpty) {
                            doc = queryEmail.documents.first()
                        }
                    }
                }

                if (doc.exists()) {
                    val data = doc.data
                    if (data != null) {
                        activo = (data["activo"] as? Boolean) ?: true
                        if (!activo) {
                            auth.signOut()
                            return@withContext Result.failure(Exception("Tu cuenta ha sido desactivada por el administrador."))
                        }
                        val fetchedRole = (data["role"] as? String)?.lowercase() ?: if (username.equals("admin", ignoreCase = true)) "admin" else "colaborador"
                        roleStr = fetchedRole
                        nombreCompleto = (data["nombreCompleto"] as? String) ?: (data["username"] as? String) ?: identifierClean
                        username = (data["username"] as? String) ?: username
                        pin = (data["pin"] as? String) ?: pin
                        val rawPerms = data["permisos"] as? List<*>
                        permisosList = if (roleStr == "admin") {
                            listOf("all")
                        } else {
                            rawPerms?.mapNotNull { it as? String }?.takeIf { it.isNotEmpty() } ?: listOf(User.PERM_INVENTORY_READ, "inventario:consultar")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val role = if (roleStr == "admin") UserRole.ADMIN else UserRole.COLABORADOR
            val user = User(
                id = uid,
                username = username,
                nombreCompleto = nombreCompleto,
                email = email,
                pin = pin,
                role = role,
                permisos = permisosList,
                activo = activo
            )

            // 3. Persistir sesión de usuario en Room SQLite local
            userDao.insertOrUpdate(
                UserEntity(
                    id = uid,
                    username = username,
                    nombreCompleto = nombreCompleto,
                    email = email,
                    pin = pin,
                    role = roleStr,
                    permisos = if (role == UserRole.ADMIN) "all" else permisosList.joinToString(","),
                    activo = if (activo) 1 else 0
                )
            )

            Result.success(user)
        } catch (e: Exception) {
            val friendlyMessage = when (e) {
                is FirebaseAuthInvalidUserException -> "Usuario o correo electrónico no registrado en Firebase."
                is FirebaseAuthInvalidCredentialsException -> "Contraseña o credenciales incorrectas."
                is FirebaseAuthException -> {
                    when (e.errorCode) {
                        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "Contraseña incorrecta."
                        "ERROR_USER_NOT_FOUND" -> "El usuario no existe en Firebase Authentication."
                        "ERROR_USER_DISABLED" -> "Esta cuenta ha sido deshabilitada."
                        "ERROR_TOO_MANY_REQUESTS" -> "Demasiados intentos fallidos. Espera unos momentos."
                        else -> e.message ?: "Error de autenticación"
                    }
                }
                else -> e.message ?: "No se pudo conectar con el servidor de autenticación."
            }
            Result.failure(Exception(friendlyMessage))
        }
    }

    /**
     * Verifica si ya existe una sesión activa en Firebase Auth al iniciar la app.
     */
    suspend fun getPersistedSession(): User? = withContext(Dispatchers.IO) {
        val currentFirebaseUser = auth.currentUser ?: return@withContext null
        val uid = currentFirebaseUser.uid

        // Intentar recuperar de Room SQLite local
        val local = userDao.getUserById(uid)
        if (local != null && local.activo == 1) {
            val isAdm = local.role.equals("admin", ignoreCase = true)
            return@withContext User(
                id = local.id,
                username = local.username,
                nombreCompleto = local.nombreCompleto,
                email = local.email,
                pin = local.pin,
                role = if (isAdm) UserRole.ADMIN else UserRole.COLABORADOR,
                permisos = if (isAdm || local.permisos == "all") {
                    listOf("all")
                } else if (local.permisos.isBlank()) {
                    listOf(User.PERM_INVENTORY_READ, "inventario:consultar")
                } else {
                    local.permisos.split(",").map { it.trim() }
                },
                activo = true
            )
        }

        // Si no está en Room, consultar Firestore
        try {
            val doc = firestore.collection("usuarios").document(uid).get().await()
            if (doc.exists()) {
                val data = doc.data ?: return@withContext null
                val userNm = (data["username"] as? String) ?: currentFirebaseUser.email?.substringBefore("@") ?: "Usuario"
                val roleStr = (data["role"] as? String)?.lowercase() ?: if (userNm.equals("admin", ignoreCase = true)) "admin" else "colaborador"
                val rawPerms = data["permisos"] as? List<*>
                val perms = if (roleStr == "admin") {
                    listOf("all")
                } else {
                    rawPerms?.mapNotNull { it as? String }?.takeIf { it.isNotEmpty() } ?: listOf(User.PERM_INVENTORY_READ, "inventario:consultar")
                }

                val user = User(
                    id = uid,
                    username = userNm,
                    nombreCompleto = (data["nombreCompleto"] as? String) ?: userNm,
                    email = currentFirebaseUser.email,
                    pin = (data["pin"] as? String) ?: "1234",
                    role = if (roleStr == "admin") UserRole.ADMIN else UserRole.COLABORADOR,
                    permisos = perms,
                    activo = (data["activo"] as? Boolean) ?: true
                )
                return@withContext user
            }
        } catch (_: Exception) {}

        null
    }

    fun logout() {
        try {
            auth.signOut()
        } catch (_: Exception) {}
    }
}
