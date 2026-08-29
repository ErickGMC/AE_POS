package com.minimarket.aepos.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.minimarket.aepos.data.local.AppDatabase
import com.minimarket.aepos.data.local.entity.ProductEntity
import com.minimarket.aepos.data.local.entity.SaleDetailEntity
import com.minimarket.aepos.data.local.entity.SaleEntity
import com.minimarket.aepos.data.local.entity.UserEntity
import com.minimarket.aepos.domain.model.Product
import com.minimarket.aepos.domain.model.User
import com.minimarket.aepos.domain.model.UserRole
import com.minimarket.aepos.utils.ImageOptimizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

enum class SyncStateStatus {
    SYNCED,
    SYNCING,
    OFFLINE,
    ERROR
}

data class SyncStatus(
    val status: SyncStateStatus = SyncStateStatus.SYNCED,
    val lastSyncTime: String = "Ahora",
    val pendingUploadsCount: Int = 0,
    val message: String? = null
)

class FirestoreSyncService(
    private val db: AppDatabase,
    private val scope: CoroutineScope
) {
    private val firestore by lazy { FirebaseConfig.firestore }
    private var productsListenerRegistration: ListenerRegistration? = null
    private var usersListenerRegistration: ListenerRegistration? = null
    private var salesListenerRegistration: ListenerRegistration? = null

    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    init {
        startRealtimeProductsListener()
        startRealtimeUsersListener()
        startRealtimeSalesListener()
    }

    /**
     * Escucha en tiempo real los cambios de la colección 'productos' en Firestore
     * y los refleja de inmediato en la base de datos local SQLite (Room).
     */
    fun startRealtimeProductsListener() {
        try {
            productsListenerRegistration?.remove()
            productsListenerRegistration = firestore.collection("productos")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        _syncStatus.value = _syncStatus.value.copy(
                            status = SyncStateStatus.OFFLINE,
                            message = error.message
                        )
                        return@addSnapshotListener
                    }

                    if (snapshot != null && !snapshot.isEmpty) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val products = snapshot.documents.mapNotNull { doc ->
                                    val id = doc.id
                                    val data = doc.data ?: return@mapNotNull null
                                    ProductEntity(
                                        id = id,
                                        codigoBarras = data["codigoBarras"] as? String,
                                        nombre = (data["nombre"] as? String) ?: "Producto sin nombre",
                                        descripcion = data["descripcion"] as? String,
                                        categoria = (data["categoria"] as? String) ?: "General",
                                        precio = (data["precio"] as? Number)?.toDouble() ?: 0.0,
                                        costo = (data["costo"] as? Number)?.toDouble(),
                                        stock = (data["stock"] as? Number)?.toDouble() ?: 0.0,
                                        unidadMedida = (data["unidadMedida"] as? String) ?: "UND",
                                        imagenUrl = data["imagenUrl"] as? String,
                                        thumbnailUrl = data["thumbnailUrl"] as? String,
                                        imagenLocal = data["imagenLocal"] as? String,
                                        thumbnailLocal = data["thumbnailLocal"] as? String,
                                        disponible = if ((data["disponible"] as? Boolean) == false) 0 else 1,
                                        destacado = if ((data["destacado"] as? Boolean) == true) 1 else 0,
                                        esPrincipalWeb = if ((data["esPrincipalWeb"] as? Boolean) == true) 1 else 0,
                                        productoPadreId = data["productoPadreId"] as? String,
                                        etiquetaVariante = data["etiquetaVariante"] as? String,
                                        mostrarPrecioWeb = if ((data["mostrarPrecioWeb"] as? Boolean) == true) 1 else 0
                                    )
                                }
                                db.productDao().insertAll(products)

                                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                _syncStatus.value = SyncStatus(
                                    status = SyncStateStatus.SYNCED,
                                    lastSyncTime = timeFormat.format(Date()),
                                    message = "Sincronizado con la nube (${products.size} productos)"
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus(
                status = SyncStateStatus.OFFLINE,
                message = "Modo Offline activo"
            )
        }
    }

    /**
     * Escucha en tiempo real los cambios de la colección 'usuarios' en Firestore.
     */
    fun startRealtimeUsersListener() {
        try {
            usersListenerRegistration?.remove()
            usersListenerRegistration = firestore.collection("usuarios")
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null && !snapshot.isEmpty) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val users = snapshot.documents.mapNotNull { doc ->
                                    val data = doc.data ?: return@mapNotNull null
                                    val usernameVal = (data["username"] as? String) ?: "usuario"
                                    val roleVal = (data["role"] as? String)?.lowercase() ?: if (usernameVal.equals("admin", ignoreCase = true)) "admin" else "colaborador"
                                    val rawPerms = data["permisos"] as? List<*>
                                    val permsStr = if (roleVal == "admin") {
                                        "all"
                                    } else {
                                        rawPerms?.mapNotNull { it as? String }?.takeIf { it.isNotEmpty() }?.joinToString(",") ?: User.PERM_INVENTORY_READ
                                    }

                                    UserEntity(
                                        id = doc.id,
                                        username = usernameVal,
                                        nombreCompleto = (data["nombreCompleto"] as? String) ?: (data["username"] as? String) ?: "Usuario",
                                        email = data["email"] as? String,
                                        pin = (data["pin"] as? String) ?: "1234",
                                        role = roleVal,
                                        permisos = permsStr,
                                        activo = if ((data["activo"] as? Boolean) == false) 0 else 1
                                    )
                                }
                                db.userDao().insertAll(users)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
        } catch (_: Exception) {}
    }

    /**
     * Escucha en tiempo real las ventas registradas en la nube (desde Desktop PC o móvil).
     */
    fun startRealtimeSalesListener() {
        try {
            salesListenerRegistration?.remove()
            salesListenerRegistration = firestore.collection("ventas")
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null && !snapshot.isEmpty) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val sales = snapshot.documents.mapNotNull { doc ->
                                    val data = doc.data ?: return@mapNotNull null
                                    val fechaVal = when (val f = data["fecha"]) {
                                        is Timestamp -> SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(f.toDate())
                                        is String -> f
                                        else -> (data["fechaString"] as? String) ?: SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                                    }
                                    val serieVal = (data["serie"] as? String) ?: "B001"
                                    val numVal = (data["correlativoNumero"] as? Number)?.toInt() ?: 1
                                    val ticketVal = (data["numeroTicket"] as? String) ?: "$serieVal-${numVal.toString().padStart(8, '0')}"
                                    val anuladoVal = (data["anulado"] as? Boolean) == true

                                    SaleEntity(
                                        id = doc.id,
                                        fecha = fechaVal,
                                        total = (data["total"] as? Number)?.toDouble() ?: 0.0,
                                        metodoPago = (data["metodoPago"] as? String) ?: "Efectivo",
                                        estado = (data["estado"] as? String) ?: "completada",
                                        clienteNombre = data["clienteNombre"] as? String,
                                        clienteDocumento = data["clienteDocumento"] as? String,
                                        serie = serieVal,
                                        correlativoNumero = numVal,
                                        comprobanteFormateado = ticketVal,
                                        anulado = if (anuladoVal) 1 else 0
                                    )
                                }
                                db.saleDao().insertAllSales(sales)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
        } catch (_: Exception) {}
    }

    /**
     * Sube un usuario a Firestore.
     */
    suspend fun uploadUser(user: User): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val map = hashMapOf(
                "id" to user.id,
                "username" to user.username,
                "nombreCompleto" to user.nombreCompleto,
                "email" to (user.email ?: ""),
                "pin" to user.pin,
                "role" to if (user.role == UserRole.ADMIN) "admin" else "colaborador",
                "permisos" to user.permisos,
                "activo" to user.activo,
                "actualizado_el" to Timestamp.now()
            )
            firestore.collection("usuarios").document(user.id)
                .set(map, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteUser(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("usuarios").document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sube un producto creado o modificado en AE_POS directamente a Firestore.
     */
    suspend fun uploadProduct(product: Product): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _syncStatus.value = _syncStatus.value.copy(status = SyncStateStatus.SYNCING)

            val map = hashMapOf(
                "id" to product.id,
                "codigoBarras" to (product.codigoBarras ?: ""),
                "nombre" to product.nombre,
                "descripcion" to (product.descripcion ?: ""),
                "categoria" to product.categoria,
                "precio" to product.precio,
                "costo" to (product.costo ?: 0.0),
                "stock" to product.stock,
                "unidadMedida" to product.unidadMedida,
                "imagenUrl" to (product.imagenUrl ?: ""),
                "disponible" to product.disponible,
                "destacado" to product.destacado,
                "esPrincipalWeb" to product.esPrincipalWeb,
                "productoPadreId" to (product.productoPadreId ?: ""),
                "etiquetaVariante" to (product.etiquetaVariante ?: ""),
                "mostrarPrecioWeb" to product.mostrarPrecioWeb,
                "actualizado_el" to Timestamp.now()
            )

            firestore.collection("productos").document(product.id)
                .set(map, SetOptions.merge())
                .await()

            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            _syncStatus.value = SyncStatus(
                status = SyncStateStatus.SYNCED,
                lastSyncTime = timeFormat.format(Date())
            )
            Result.success(Unit)
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus(
                status = SyncStateStatus.OFFLINE,
                message = "Guardado localmente (se sincronizará al conectar)"
            )
            Result.failure(e)
        }
    }

    /**
     * Elimina un producto en Firestore.
     */
    suspend fun deleteProduct(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("productos").document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sube una imagen optimizada a WebP a Firebase Storage.
     */
    suspend fun uploadProductImageWebp(webpBytes: ByteArray, categoria: String): Result<String> {
        return ImageOptimizer.uploadProductImageToStorage(webpBytes, categoria)
    }

    /**
     * Sube una venta completada a Firestore:
     * 1. Crea el documento en 'ventas'
     * 2. Guarda los items en la subcolección 'ventas/{id}/detalle'
     * 3. Descuenta el stock en Firestore para reflejarse en la PC y en la web
     */
    suspend fun uploadSale(
        saleEntity: SaleEntity,
        details: List<SaleDetailEntity>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _syncStatus.value = _syncStatus.value.copy(status = SyncStateStatus.SYNCING)

            val batch = firestore.batch()

            // 1. Cabecera de Venta
            val saleDocRef = firestore.collection("ventas").document(saleEntity.id)
            val saleMap = hashMapOf(
                "id" to saleEntity.id,
                "fecha" to Timestamp.now(),
                "fechaString" to saleEntity.fecha,
                "total" to saleEntity.total,
                "metodoPago" to saleEntity.metodoPago,
                "estado" to saleEntity.estado,
                "clienteNombre" to (saleEntity.clienteNombre ?: ""),
                "clienteDocumento" to (saleEntity.clienteDocumento ?: ""),
                "serie" to saleEntity.serie,
                "correlativoNumero" to saleEntity.correlativoNumero,
                "numeroTicket" to saleEntity.comprobanteFormateado,
                "anulado" to (saleEntity.anulado == 1),
                "origen" to "AE_POS_ANDROID"
            )
            batch.set(saleDocRef, saleMap)

            // 2. Descontar Stock en Firestore para cada producto
            for (item in details) {
                val detailDocRef = saleDocRef.collection("detalle").document(item.id)
                val detailMap = hashMapOf(
                    "id" to item.id,
                    "venta_id" to saleEntity.id,
                    "producto_id" to item.producto_id,
                    "cantidad" to item.cantidad,
                    "precio_unitario" to item.precio_unitario,
                    "subtotal" to item.subtotal
                )
                batch.set(detailDocRef, detailMap)

                // Decremento de stock atómico en Firestore
                val prodRef = firestore.collection("productos").document(item.producto_id)
                batch.update(prodRef, "stock", com.google.firebase.firestore.FieldValue.increment(-item.cantidad))
            }

            // 3. Actualizar correlativo M001 en la nube
            val correlativeRef = firestore.collection("correlativos").document(saleEntity.serie)
            batch.set(
                correlativeRef,
                hashMapOf(
                    "serie" to saleEntity.serie,
                    "siguiente_numero" to (saleEntity.correlativoNumero + 1),
                    "ultimo_ticket" to saleEntity.comprobanteFormateado,
                    "actualizado_el" to Timestamp.now()
                ),
                SetOptions.merge()
            )

            batch.commit().await()

            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            _syncStatus.value = SyncStatus(
                status = SyncStateStatus.SYNCED,
                lastSyncTime = timeFormat.format(Date()),
                message = "Venta sincronizada a la nube"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus(
                status = SyncStateStatus.OFFLINE,
                message = "Venta registrada localmente. Pendiente de subida."
            )
            Result.failure(e)
        }
    }

    /**
     * Descarga manual y forzada de todo el catálogo, ventas y usuarios desde Firestore.
     */
    suspend fun syncAllNow(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            _syncStatus.value = _syncStatus.value.copy(status = SyncStateStatus.SYNCING)

            val snapshot = firestore.collection("productos").get().await()
            val products = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                ProductEntity(
                    id = doc.id,
                    codigoBarras = data["codigoBarras"] as? String,
                    nombre = (data["nombre"] as? String) ?: "Producto sin nombre",
                    descripcion = data["descripcion"] as? String,
                    categoria = (data["categoria"] as? String) ?: "General",
                    precio = (data["precio"] as? Number)?.toDouble() ?: 0.0,
                    costo = (data["costo"] as? Number)?.toDouble(),
                    stock = (data["stock"] as? Number)?.toDouble() ?: 0.0,
                    unidadMedida = (data["unidadMedida"] as? String) ?: "UND",
                    imagenUrl = data["imagenUrl"] as? String,
                    thumbnailUrl = data["thumbnailUrl"] as? String,
                    imagenLocal = data["imagenLocal"] as? String,
                    thumbnailLocal = data["thumbnailLocal"] as? String,
                    disponible = if ((data["disponible"] as? Boolean) == false) 0 else 1,
                    destacado = if ((data["destacado"] as? Boolean) == true) 1 else 0,
                    esPrincipalWeb = if ((data["esPrincipalWeb"] as? Boolean) == true) 1 else 0,
                    productoPadreId = data["productoPadreId"] as? String,
                    etiquetaVariante = data["etiquetaVariante"] as? String,
                    mostrarPrecioWeb = if ((data["mostrarPrecioWeb"] as? Boolean) == true) 1 else 0
                )
            }

            if (products.isNotEmpty()) {
                db.productDao().insertAll(products)
            }

            // Descargar usuarios desde la nube
            try {
                val userSnap = firestore.collection("usuarios").get().await()
                val users = userSnap.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val usernameVal = (data["username"] as? String) ?: "usuario"
                    val roleVal = (data["role"] as? String)?.lowercase() ?: if (usernameVal.equals("admin", ignoreCase = true)) "admin" else "colaborador"
                    val rawPerms = data["permisos"] as? List<*>
                    val permsStr = if (roleVal == "admin") {
                        "all"
                    } else {
                        rawPerms?.mapNotNull { it as? String }?.takeIf { it.isNotEmpty() }?.joinToString(",") ?: User.PERM_INVENTORY_READ
                    }

                    UserEntity(
                        id = doc.id,
                        username = usernameVal,
                        nombreCompleto = (data["nombreCompleto"] as? String) ?: (data["username"] as? String) ?: "Usuario",
                        email = data["email"] as? String,
                        pin = (data["pin"] as? String) ?: "1234",
                        role = roleVal,
                        permisos = permsStr,
                        activo = if ((data["activo"] as? Boolean) == false) 0 else 1
                    )
                }
                if (users.isNotEmpty()) {
                    db.userDao().insertAll(users)
                }
            } catch (_: Exception) {}

            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            _syncStatus.value = SyncStatus(
                status = SyncStateStatus.SYNCED,
                lastSyncTime = timeFormat.format(Date()),
                message = "${products.size} productos actualizados"
            )
            Result.success(products.size)
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus(
                status = SyncStateStatus.ERROR,
                message = "Fallo de sincronización: ${e.message}"
            )
            Result.failure(e)
        }
    }
}
