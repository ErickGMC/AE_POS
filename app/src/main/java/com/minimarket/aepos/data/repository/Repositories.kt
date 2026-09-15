package com.minimarket.aepos.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.minimarket.aepos.data.local.AppDatabase
import com.minimarket.aepos.data.local.entity.*
import com.minimarket.aepos.data.remote.FirestoreSyncService
import com.minimarket.aepos.domain.model.*
import com.minimarket.aepos.utils.ImageOptimizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class StockFilterOption {
    ALL,
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK
}

class ProductRepository(
    private val db: AppDatabase,
    private val syncService: FirestoreSyncService,
    private val scope: CoroutineScope
) {
    private val productDao = db.productDao()

    fun getAllProductsFlow(): Flow<List<Product>> {
        return productDao.getAllProductsFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    fun searchProductsFlow(
        query: String,
        category: String = "Todos",
        stockFilter: StockFilterOption = StockFilterOption.ALL
    ): Flow<List<Product>> {
        val baseFlow = when {
            query.isNotBlank() -> productDao.searchProductsFlow(query.trim())
            category != "Todos" && category.isNotBlank() -> productDao.getProductsByCategoryFlow(category)
            else -> productDao.getAllActiveProductsFlow()
        }

        return baseFlow.map { list ->
            val mapped = list.map { it.toDomain() }
            if (stockFilter == StockFilterOption.ALL && (query.isNotBlank() || category == "Todos" || category.isBlank())) {
                mapped
            } else {
                mapped.filter { prod ->
                    val matchesCat = category == "Todos" || category.isBlank() || prod.categoria.equals(category, ignoreCase = true)
                    val matchesStock = when (stockFilter) {
                        StockFilterOption.ALL -> true
                        StockFilterOption.IN_STOCK -> prod.stock > 10.0
                        StockFilterOption.LOW_STOCK -> prod.stock in 0.01..10.0
                        StockFilterOption.OUT_OF_STOCK -> prod.stock <= 0.0
                    }
                    matchesCat && matchesStock
                }
            }
        }
    }

    fun getCategoriesFlow(): Flow<List<String>> {
        return productDao.getAllCategoriesFlow().map { categories ->
            listOf("Todos") + categories
        }
    }

    fun getProductsByCategoryFlow(category: String): Flow<List<Product>> {
        return searchProductsFlow(query = "", category = category)
    }

    suspend fun getByBarcode(barcode: String): Product? {
        return productDao.getByBarcode(barcode.trim())?.toDomain()
    }

    suspend fun getById(id: String): Product? {
        return productDao.getById(id)?.toDomain()
    }

    /**
     * Guarda o edita un producto con soporte completo para imágenes WebP optimizadas (600x600 px).
     */
    suspend fun saveProduct(
        context: Context?,
        product: Product,
        webpBytes: ByteArray? = null
    ) {
        var finalProduct = product

        if (webpBytes != null) {
            // 1. Guardar copia local en caché para renderizado instantáneo offline
            val localPath = if (context != null) {
                try {
                    ImageOptimizer.saveWebpLocally(context, webpBytes, product.id)
                } catch (_: Exception) { null }
            } else null

            // 2. Subir imagen WebP a Firebase Storage
            val uploadResult = syncService.uploadProductImageWebp(webpBytes, product.categoria)
            val remoteUrl = uploadResult.getOrNull()

            finalProduct = product.copy(
                imagenUrl = remoteUrl ?: product.imagenUrl,
                imagenLocal = localPath ?: product.imagenLocal
            )
        }

        // 3. Guardar en SQLite Room local (cero latencia)
        productDao.insertOrUpdate(finalProduct.toEntity())

        // 4. Sincronizar en Firestore Cloud
        scope.launch(Dispatchers.IO) {
            syncService.uploadProduct(finalProduct)
        }
    }

    suspend fun deleteProduct(id: String) {
        productDao.deleteById(id)
        scope.launch(Dispatchers.IO) {
            syncService.deleteProduct(id)
        }
    }

    private fun ProductEntity.toDomain() = Product(
        id = id,
        codigoBarras = codigoBarras,
        nombre = nombre,
        descripcion = descripcion,
        categoria = categoria,
        precio = precio,
        costo = costo,
        stock = stock,
        unidadMedida = unidadMedida,
        imagenUrl = imagenUrl,
        imagenLocal = imagenLocal,
        disponible = disponible == 1,
        destacado = destacado == 1,
        esPrincipalWeb = esPrincipalWeb == 1,
        productoPadreId = productoPadreId,
        etiquetaVariante = etiquetaVariante,
        mostrarPrecioWeb = mostrarPrecioWeb == 1
    )

    private fun Product.toEntity() = ProductEntity(
        id = id,
        codigoBarras = codigoBarras,
        nombre = nombre,
        descripcion = descripcion,
        categoria = categoria,
        precio = precio,
        costo = costo,
        stock = stock,
        unidadMedida = unidadMedida,
        imagenUrl = imagenUrl,
        imagenLocal = imagenLocal,
        disponible = if (disponible) 1 else 0,
        destacado = if (destacado) 1 else 0,
        esPrincipalWeb = if (esPrincipalWeb) 1 else 0,
        productoPadreId = productoPadreId,
        etiquetaVariante = etiquetaVariante,
        mostrarPrecioWeb = if (mostrarPrecioWeb) 1 else 0
    )
}

class CashRepository(
    private val db: AppDatabase,
    private val syncService: FirestoreSyncService,
    private val scope: CoroutineScope
) {
    private val cashDao = db.cashDao()

    val activeShiftFlow: Flow<CashShiftEntity?> = cashDao.getActiveShiftFlow()
    val allShiftsFlow: Flow<List<CashShiftEntity>> = cashDao.getAllShiftsFlow()

    fun getMovementsFlow(shiftId: String): Flow<List<CashMovementEntity>> {
        return cashDao.getMovementsForShiftFlow(shiftId)
    }

    suspend fun openShift(montoInicial: Double, cajero: String): Result<Unit> {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val shift = CashShiftEntity(
                id = UUID.randomUUID().toString(),
                fechaApertura = dateFormat.format(Date()),
                montoInicial = montoInicial,
                cajero = cajero.ifBlank { "Cajero" },
                estado = "abierta",
                sincronizado = 0
            )
            cashDao.insertOrUpdateShift(shift)
            scope.launch(Dispatchers.IO) {
                val res = syncService.uploadShift(shift)
                if (res.isSuccess) {
                    cashDao.markShiftAsSynced(shift.id)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun closeShift(shiftId: String, montoFinalReal: Double, observaciones: String?): Result<Unit> {
        return try {
            val current = cashDao.getActiveShift() ?: return Result.failure(Exception("No hay caja abierta"))
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val diferencia = Math.round((montoFinalReal - current.montoEsperado) * 100.0) / 100.0

            val closedShift = current.copy(
                fechaCierre = dateFormat.format(Date()),
                montoFinalReal = montoFinalReal,
                diferencia = diferencia,
                estado = "cerrada",
                observaciones = observaciones,
                sincronizado = 0
            )
            cashDao.insertOrUpdateShift(closedShift)
            scope.launch(Dispatchers.IO) {
                val res = syncService.uploadShift(closedShift)
                if (res.isSuccess) {
                    cashDao.markShiftAsSynced(closedShift.id)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerMovement(shiftId: String, tipo: String, monto: Double, motivo: String): Result<Unit> {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val movement = CashMovementEntity(
                id = UUID.randomUUID().toString(),
                turnoId = shiftId,
                tipo = tipo,
                monto = monto,
                motivo = motivo,
                fecha = dateFormat.format(Date()),
                sincronizado = 0
            )
            cashDao.insertMovement(movement)
            if (tipo == "ingreso") {
                cashDao.addIncome(shiftId, monto)
            } else {
                cashDao.addExpense(shiftId, monto)
            }
            scope.launch(Dispatchers.IO) {
                val res = syncService.uploadMovement(movement)
                if (res.isSuccess) {
                    cashDao.markMovementAsSynced(movement.id)
                }
                val updatedShift = cashDao.getActiveShift()
                if (updatedShift != null && updatedShift.id == shiftId) {
                    syncService.uploadShift(updatedShift)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class SaleRepository(
    private val db: AppDatabase,
    private val syncService: FirestoreSyncService,
    private val scope: CoroutineScope
) {
    private val saleDao = db.saleDao()
    private val correlativeDao = db.correlativeDao()
    private val productDao = db.productDao()

    val allSalesFlow: Flow<List<Sale>> = saleDao.getAllSalesFlow().map { entities ->
        entities.map { entity ->
            entity.toDomainWithProducts(emptyList())
        }
    }

    fun getSalesByDateFlow(datePrefix: String): Flow<List<Sale>> {
        return saleDao.getSalesByDateFlow(datePrefix).map { entities ->
            entities.map { entity ->
                entity.toDomainWithProducts(emptyList())
            }
        }
    }

    suspend fun getSaleDetailsWithProducts(saleId: String): List<SaleDetail> {
        val details = saleDao.getDetailsWithProductForSale(saleId)
        return details.map {
            SaleDetail(
                id = it.id,
                ventaId = it.venta_id,
                productoId = it.producto_id,
                productoNombre = it.producto_nombre ?: "Producto",
                cantidad = it.cantidad,
                precioUnitario = it.precio_unitario,
                subtotal = it.subtotal
            )
        }
    }

    /**
     * Procesa una venta de forma 100% ATÓMICA en SQLite y la sincroniza a Firestore.
     */
    suspend fun processSale(
        items: List<CartItem>,
        total: Double,
        paymentMethod: PaymentMethod,
        customerName: String? = null,
        customerDoc: String? = null,
        montoEfectivo: Double? = null
    ): Result<String> {
        if (items.isEmpty()) return Result.failure(Exception("El carrito está vacío"))

        return try {
            val (comprobante, saleEntity, detailEntities) = db.withTransaction {
                val serie = "M001"
                var correlative = correlativeDao.getCorrelative(serie)
                if (correlative == null) {
                    correlative = CorrelativeEntity(serie = serie, siguiente_numero = 1)
                    correlativeDao.insertOrUpdate(correlative)
                }

                val currentNumber = correlative.siguiente_numero
                val comprobanteFormateado = String.format("%s-%08d", serie, currentNumber)

                val saleId = UUID.randomUUID().toString()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val currentDate = dateFormat.format(Date())

                // 1. Cabecera de Venta
                val sEntity = SaleEntity(
                    id = saleId,
                    fecha = currentDate,
                    total = Math.round(total * 100.0) / 100.0,
                    metodoPago = paymentMethod.label,
                    estado = "completada",
                    clienteNombre = customerName?.takeIf { it.isNotBlank() },
                    clienteDocumento = customerDoc?.takeIf { it.isNotBlank() },
                    serie = serie,
                    correlativoNumero = currentNumber,
                    comprobanteFormateado = comprobanteFormateado,
                    anulado = 0,
                    sincronizado = 0
                )
                saleDao.insertSale(sEntity)

                // 2. Detalles y Descuento de Stock
                val dEntities = items.map { item ->
                    val detailId = UUID.randomUUID().toString()
                    productDao.reduceStock(item.product.id, item.cantidad)
                    SaleDetailEntity(
                        id = detailId,
                        venta_id = saleId,
                        producto_id = item.product.id,
                        cantidad = item.cantidad,
                        precio_unitario = item.precioUnitario,
                        subtotal = item.subtotal
                    )
                }
                saleDao.insertSaleDetails(dEntities)

                // 3. Incrementar correlativo
                correlativeDao.increment(serie)

                // 4. Actualizar turno de caja activo si existe
                val activeShift = db.cashDao().getActiveShift()
                if (activeShift != null) {
                    if (montoEfectivo != null) {
                        val ef = Math.round(montoEfectivo * 100.0) / 100.0
                        val dig = Math.max(0.0, Math.round((sEntity.total - ef) * 100.0) / 100.0)
                        if (ef > 0) db.cashDao().addCashSale(activeShift.id, ef)
                        if (dig > 0) db.cashDao().addDigitalSale(activeShift.id, dig)
                    } else if (paymentMethod == PaymentMethod.EFECTIVO) {
                        db.cashDao().addCashSale(activeShift.id, sEntity.total)
                    } else {
                        db.cashDao().addDigitalSale(activeShift.id, sEntity.total)
                    }
                }

                Triple(comprobanteFormateado, sEntity, dEntities)
            }

            // 5. Subir a Firestore en background
            scope.launch(Dispatchers.IO) {
                val uploadResult = syncService.uploadSale(saleEntity, detailEntities)
                if (uploadResult.isSuccess) {
                    saleDao.markSaleAsSynced(saleEntity.id)
                }
                val updatedShift = db.cashDao().getActiveShift()
                if (updatedShift != null) {
                    syncService.uploadShift(updatedShift)
                }
            }

            Result.success(comprobante)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelSale(saleId: String): Result<Unit> {
        return try {
            val (details, sale) = db.withTransaction {
                val s = saleDao.getSaleById(saleId)
                val dList = saleDao.getDetailsForSale(saleId)
                for (d in dList) {
                    productDao.increaseStock(d.producto_id, d.cantidad)
                }
                saleDao.cancelSale(saleId)

                // Revertir del turno de caja activo si existe
                val activeShift = db.cashDao().getActiveShift()
                if (activeShift != null && s != null) {
                    if (s.metodoPago.equals(PaymentMethod.EFECTIVO.label, ignoreCase = true)) {
                        db.cashDao().subtractCashSale(activeShift.id, s.total)
                    } else {
                        db.cashDao().subtractDigitalSale(activeShift.id, s.total)
                    }
                }

                Pair(dList, s)
            }
            scope.launch(Dispatchers.IO) {
                syncService.cancelSale(saleId, details)
                val updatedShift = db.cashDao().getActiveShift()
                if (updatedShift != null) {
                    syncService.uploadShift(updatedShift)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun SaleEntity.toDomainWithProducts(details: List<com.minimarket.aepos.data.local.dao.SaleDetailWithProduct>) = Sale(
        id = id,
        fecha = fecha,
        total = total,
        metodoPago = PaymentMethod.entries.find { it.label.equals(metodoPago, ignoreCase = true) } ?: PaymentMethod.EFECTIVO,
        estado = estado,
        clienteNombre = clienteNombre,
        clienteDocumento = clienteDocumento,
        serie = serie,
        numeroComprobante = comprobanteFormateado,
        anulado = anulado == 1,
        items = details.map {
            SaleDetail(
                id = it.id,
                ventaId = it.venta_id,
                productoId = it.producto_id,
                productoNombre = it.producto_nombre ?: "",
                cantidad = it.cantidad,
                precioUnitario = it.precio_unitario,
                subtotal = it.subtotal
            )
        }
    )
}
