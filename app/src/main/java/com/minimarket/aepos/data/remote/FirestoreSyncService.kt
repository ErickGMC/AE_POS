package com.minimarket.aepos.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.minimarket.aepos.data.local.AppDatabase
import com.minimarket.aepos.data.local.entity.*
import com.minimarket.aepos.domain.model.Product
import com.minimarket.aepos.domain.model.User
import com.minimarket.aepos.domain.model.UserRole
import com.minimarket.aepos.utils.ImageOptimizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private var cashListenerRegistration: ListenerRegistration? = null
    private var movementsListenerRegistration: ListenerRegistration? = null

    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    init {
        startRealtimeProductsListener()
        startRealtimeUsersListener()
        startRealtimeSalesListener()
        startRealtimeCashListener()
        observePendingCount()
        scope.launch(Dispatchers.IO) {
            syncPendingOutbox()
        }
    }

    private fun observePendingCount() {
        scope.launch(Dispatchers.IO) {
            db.saleDao().getPendingSalesCountFlow().collect { count ->
                _syncStatus.update { it.copy(pendingUploadsCount = count) }
            }
        }
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

                    if (snapshot != null) {
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
                                db.productDao().syncCatalog(products)

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
                    if (error == null && snapshot != null) {
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
                                if (users.isNotEmpty()) {
                                    db.userDao().syncUsers(users)
                                }
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
            val todayPrefix = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            salesListenerRegistration = firestore.collection("ventas")
                .whereGreaterThanOrEqualTo("fechaString", todayPrefix)
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
                                        anulado = if (anuladoVal) 1 else 0,
                                        sincronizado = 1
                                    )
                                }
                                db.saleDao().insertAllSales(sales)

                                // Descargar o registrar detalles de la venta
                                for (doc in snapshot.documents) {
                                    val sId = doc.id
                                    val existing = db.saleDao().getDetailsForSale(sId)
                                    if (existing.isEmpty()) {
                                        val detList = mutableListOf<SaleDetailEntity>()
                                        val docData = doc.data
                                        val rootItems = (docData?.get("items") ?: docData?.get("detalles")) as? List<*>
                                        
                                        if (rootItems != null && rootItems.isNotEmpty()) {
                                            for (raw in rootItems) {
                                                if (raw is Map<*, *>) {
                                                    detList.add(
                                                        SaleDetailEntity(
                                                            id = (raw["id"] as? String) ?: UUID.randomUUID().toString(),
                                                            venta_id = sId,
                                                            producto_id = (raw["producto_id"] as? String) ?: (raw["productoId"] as? String) ?: "",
                                                            cantidad = (raw["cantidad"] as? Number)?.toDouble() ?: 1.0,
                                                            precio_unitario = (raw["precio_unitario"] as? Number)?.toDouble() ?: (raw["precioUnitario"] as? Number)?.toDouble() ?: 0.0,
                                                            subtotal = (raw["subtotal"] as? Number)?.toDouble() ?: 0.0
                                                        )
                                                    )
                                                }
                                            }
                                        } else {
                                            // Fallback para ventas legadas con subcolección
                                            try {
                                                val detSnap = firestore.collection("ventas").document(sId).collection("detalle").get().await()
                                                for (detDoc in detSnap.documents) {
                                                    val detData = detDoc.data ?: continue
                                                    if (detDoc.id == "items" && detData["items"] is List<*>) {
                                                        @Suppress("UNCHECKED_CAST")
                                                        val items = detData["items"] as List<Map<String, Any>>
                                                        for (itm in items) {
                                                            detList.add(
                                                                SaleDetailEntity(
                                                                    id = (itm["id"] as? String) ?: UUID.randomUUID().toString(),
                                                                    venta_id = sId,
                                                                    producto_id = (itm["producto_id"] as? String) ?: (itm["productoId"] as? String) ?: "",
                                                                    cantidad = (itm["cantidad"] as? Number)?.toDouble() ?: 1.0,
                                                                    precio_unitario = (itm["precio_unitario"] as? Number)?.toDouble() ?: (itm["precioUnitario"] as? Number)?.toDouble() ?: 0.0,
                                                                    subtotal = (itm["subtotal"] as? Number)?.toDouble() ?: 0.0
                                                                )
                                                            )
                                                        }
                                                    } else if (detData.containsKey("producto_id")) {
                                                        detList.add(
                                                            SaleDetailEntity(
                                                                id = (detData["id"] as? String) ?: detDoc.id,
                                                                venta_id = sId,
                                                                producto_id = (detData["producto_id"] as? String) ?: "",
                                                                cantidad = (detData["cantidad"] as? Number)?.toDouble() ?: 1.0,
                                                                precio_unitario = (detData["precio_unitario"] as? Number)?.toDouble() ?: 0.0,
                                                                subtotal = (detData["subtotal"] as? Number)?.toDouble() ?: 0.0
                                                            )
                                                        )
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }

                                        if (detList.isNotEmpty()) {
                                            db.saleDao().insertSaleDetails(detList)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
        } catch (_: Exception) {}
    }

    /**
     * Escucha en tiempo real los turnos y movimientos de caja en Firestore.
     */
    fun startRealtimeCashListener() {
        try {
            cashListenerRegistration?.remove()
            cashListenerRegistration = firestore.collection("caja_turnos")
                .orderBy("fechaApertura", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(15)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null && !snapshot.isEmpty) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                for (doc in snapshot.documents) {
                                    val data = doc.data ?: continue
                                    val shift = CashShiftEntity(
                                        id = doc.id,
                                        fechaApertura = (data["fechaApertura"] as? String) ?: SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                                        fechaCierre = data["fechaCierre"] as? String,
                                        montoInicial = (data["montoInicial"] as? Number)?.toDouble() ?: 0.0,
                                        totalVentasEfectivo = (data["totalVentasEfectivo"] as? Number)?.toDouble() ?: 0.0,
                                        totalVentasDigital = (data["totalVentasDigital"] as? Number)?.toDouble() ?: 0.0,
                                        totalIngresos = (data["totalIngresos"] as? Number)?.toDouble() ?: 0.0,
                                        totalEgresos = (data["totalEgresos"] as? Number)?.toDouble() ?: 0.0,
                                        montoFinalReal = (data["montoFinalReal"] as? Number)?.toDouble(),
                                        diferencia = (data["diferencia"] as? Number)?.toDouble(),
                                        estado = (data["estado"] as? String) ?: "abierta",
                                        cajero = (data["cajero"] as? String) ?: "Cajero Principal",
                                        observaciones = data["observaciones"] as? String,
                                        sincronizado = 1
                                    )
                                    db.cashDao().insertOrUpdateShift(shift)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }

            movementsListenerRegistration?.remove()
            val todayPrefix = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            movementsListenerRegistration = firestore.collection("caja_movimientos")
                .whereGreaterThanOrEqualTo("fecha", todayPrefix)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null && !snapshot.isEmpty) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                for (doc in snapshot.documents) {
                                    val data = doc.data ?: continue
                                    val mov = CashMovementEntity(
                                        id = doc.id,
                                        turnoId = (data["turnoId"] as? String) ?: "",
                                        tipo = (data["tipo"] as? String) ?: "ingreso",
                                        monto = (data["monto"] as? Number)?.toDouble() ?: 0.0,
                                        motivo = (data["motivo"] as? String) ?: "Movimiento",
                                        fecha = (data["fecha"] as? String) ?: SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                                        sincronizado = 1
                                    )
                                    db.cashDao().insertMovement(mov)
                                }
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

            val itemsList = details.map { item ->
                hashMapOf(
                    "id" to item.id,
                    "venta_id" to saleEntity.id,
                    "producto_id" to item.producto_id,
                    "cantidad" to item.cantidad,
                    "precio_unitario" to item.precio_unitario,
                    "subtotal" to item.subtotal
                )
            }

            // 1. Cabecera y detalle de Venta unificados en 1 solo documento (1 sola escritura)
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
                "origen" to "AE_POS_ANDROID",
                "items" to itemsList
            )
            batch.set(saleDocRef, saleMap)

            // 2. Descontar Stock atómico en Firestore
            for (item in details) {
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
     * Sincroniza la anulación de una venta a Firestore y restituye el stock en la nube.
     */
    suspend fun cancelSale(saleId: String, details: List<SaleDetailEntity>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val batch = firestore.batch()
            val saleDocRef = firestore.collection("ventas").document(saleId)
            batch.update(
                saleDocRef,
                mapOf(
                    "anulado" to true,
                    "estado" to "anulada",
                    "actualizado_el" to Timestamp.now()
                )
            )

            // Restituir stock en la nube
            for (item in details) {
                if (item.producto_id.isNotBlank()) {
                    val prodRef = firestore.collection("productos").document(item.producto_id)
                    batch.update(prodRef, "stock", com.google.firebase.firestore.FieldValue.increment(item.cantidad))
                }
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Sube una apertura o cierre de turno de caja a Firestore.
     */
    suspend fun uploadShift(shift: CashShiftEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val map = hashMapOf(
                "id" to shift.id,
                "fechaApertura" to shift.fechaApertura,
                "fechaCierre" to (shift.fechaCierre ?: ""),
                "montoInicial" to shift.montoInicial,
                "totalVentasEfectivo" to shift.totalVentasEfectivo,
                "totalVentasDigital" to shift.totalVentasDigital,
                "totalIngresos" to shift.totalIngresos,
                "totalEgresos" to shift.totalEgresos,
                "montoEsperado" to shift.montoEsperado,
                "montoFinalReal" to (shift.montoFinalReal ?: 0.0),
                "diferencia" to (shift.diferencia ?: 0.0),
                "cajero" to shift.cajero,
                "estado" to shift.estado,
                "observaciones" to (shift.observaciones ?: ""),
                "actualizado_el" to Timestamp.now()
            )
            firestore.collection("caja_turnos").document(shift.id)
                .set(map, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sube un movimiento de caja (ingreso o egreso) a Firestore.
     */
    suspend fun uploadMovement(movement: CashMovementEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val map = hashMapOf(
                "id" to movement.id,
                "turnoId" to movement.turnoId,
                "tipo" to movement.tipo,
                "monto" to movement.monto,
                "motivo" to movement.motivo,
                "fecha" to movement.fecha,
                "creado_el" to Timestamp.now()
            )
            firestore.collection("caja_movimientos").document(movement.id)
                .set(map, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
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

            db.productDao().syncCatalog(products)

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
                    db.userDao().syncUsers(users)
                }
            } catch (_: Exception) {}

            // Descargar turnos y movimientos de caja
            try {
                val turnosSnap = firestore.collection("caja_turnos").get().await()
                for (doc in turnosSnap.documents) {
                    val data = doc.data ?: continue
                    val shift = CashShiftEntity(
                        id = doc.id,
                        fechaApertura = (data["fechaApertura"] as? String) ?: "",
                        fechaCierre = data["fechaCierre"] as? String,
                        montoInicial = (data["montoInicial"] as? Number)?.toDouble() ?: 0.0,
                        totalVentasEfectivo = (data["totalVentasEfectivo"] as? Number)?.toDouble() ?: 0.0,
                        totalVentasDigital = (data["totalVentasDigital"] as? Number)?.toDouble() ?: 0.0,
                        totalIngresos = (data["totalIngresos"] as? Number)?.toDouble() ?: 0.0,
                        totalEgresos = (data["totalEgresos"] as? Number)?.toDouble() ?: 0.0,
                        montoFinalReal = (data["montoFinalReal"] as? Number)?.toDouble(),
                        diferencia = (data["diferencia"] as? Number)?.toDouble(),
                        estado = (data["estado"] as? String) ?: "abierta",
                        cajero = (data["cajero"] as? String) ?: "Cajero Principal",
                        observaciones = data["observaciones"] as? String,
                        sincronizado = 1
                    )
                    db.cashDao().insertOrUpdateShift(shift)
                }

                val movsSnap = firestore.collection("caja_movimientos").get().await()
                for (doc in movsSnap.documents) {
                    val data = doc.data ?: continue
                    val mov = CashMovementEntity(
                        id = doc.id,
                        turnoId = (data["turnoId"] as? String) ?: "",
                        tipo = (data["tipo"] as? String) ?: "ingreso",
                        monto = (data["monto"] as? Number)?.toDouble() ?: 0.0,
                        motivo = (data["motivo"] as? String) ?: "Movimiento",
                        fecha = (data["fecha"] as? String) ?: "",
                        sincronizado = 1
                    )
                    db.cashDao().insertMovement(mov)
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

    /**
     * Sincroniza la cola local de salida (ventas, turnos y movimientos pendientes de subida).
     * Garantiza el funcionamiento 100% Offline-First sin pérdida de información.
     */
    suspend fun syncPendingOutbox(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var syncedCount = 0

            // 1. Sincronizar turnos pendientes
            val pendingShifts = db.cashDao().getPendingSyncShifts()
            for (shift in pendingShifts) {
                val res = uploadShift(shift)
                if (res.isSuccess) {
                    db.cashDao().markShiftAsSynced(shift.id)
                    syncedCount++
                }
            }

            // 2. Sincronizar movimientos de caja pendientes
            val pendingMovements = db.cashDao().getPendingSyncMovements()
            for (mov in pendingMovements) {
                val res = uploadMovement(mov)
                if (res.isSuccess) {
                    db.cashDao().markMovementAsSynced(mov.id)
                    syncedCount++
                }
            }

            // 3. Sincronizar ventas offline pendientes
            val pendingSales = db.saleDao().getPendingSyncSales()
            for (sale in pendingSales) {
                val details = db.saleDao().getDetailsForSale(sale.id)
                val res = uploadSale(sale, details)
                if (res.isSuccess) {
                    db.saleDao().markSaleAsSynced(sale.id)
                    syncedCount++
                }
            }

            if (syncedCount > 0) {
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                _syncStatus.update {
                    it.copy(
                        status = SyncStateStatus.SYNCED,
                        lastSyncTime = timeFormat.format(Date()),
                        message = "$syncedCount operaciones offline sincronizadas"
                    )
                }
            }

            Result.success(syncedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
