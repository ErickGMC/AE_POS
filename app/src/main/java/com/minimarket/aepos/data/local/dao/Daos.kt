package com.minimarket.aepos.data.local.dao

import androidx.room.*
import com.minimarket.aepos.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("""
        SELECT p.id, p.codigoBarras, p.nombre, p.descripcion, p.categoria, p.precio, p.costo, p.stock, p.unidadMedida,
               COALESCE(p.imagenUrl, padre.imagenUrl) AS imagenUrl,
               COALESCE(p.thumbnailUrl, padre.thumbnailUrl) AS thumbnailUrl,
               p.imagenLocal, p.thumbnailLocal,
               p.disponible, p.destacado, p.etiquetas, p.esPrincipalWeb, p.productoPadreId, p.etiquetaVariante, p.mostrarPrecioWeb
        FROM productos p
        LEFT JOIN productos padre ON p.productoPadreId = padre.id
        WHERE p.disponible = 1 AND (p.esPrincipalWeb = 0 OR p.esPrincipalWeb IS NULL)
        ORDER BY p.nombre ASC
    """)
    fun getAllActiveProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM productos ORDER BY nombre ASC")
    fun getAllProductsFlow(): Flow<List<ProductEntity>>

    @Query("""
        SELECT p.id, p.codigoBarras, p.nombre, p.descripcion, p.categoria, p.precio, p.costo, p.stock, p.unidadMedida,
               COALESCE(p.imagenUrl, padre.imagenUrl) AS imagenUrl,
               COALESCE(p.thumbnailUrl, padre.thumbnailUrl) AS thumbnailUrl,
               p.imagenLocal, p.thumbnailLocal,
               p.disponible, p.destacado, p.etiquetas, p.esPrincipalWeb, p.productoPadreId, p.etiquetaVariante, p.mostrarPrecioWeb
        FROM productos p
        LEFT JOIN productos padre ON p.productoPadreId = padre.id
        WHERE (p.nombre LIKE '%' || :query || '%' OR p.codigoBarras LIKE '%' || :query || '%')
          AND p.disponible = 1 AND (p.esPrincipalWeb = 0 OR p.esPrincipalWeb IS NULL)
        ORDER BY p.nombre ASC
    """)
    fun searchProductsFlow(query: String): Flow<List<ProductEntity>>

    @Query("""
        SELECT p.id, p.codigoBarras, p.nombre, p.descripcion, p.categoria, p.precio, p.costo, p.stock, p.unidadMedida,
               COALESCE(p.imagenUrl, padre.imagenUrl) AS imagenUrl,
               COALESCE(p.thumbnailUrl, padre.thumbnailUrl) AS thumbnailUrl,
               p.imagenLocal, p.thumbnailLocal,
               p.disponible, p.destacado, p.etiquetas, p.esPrincipalWeb, p.productoPadreId, p.etiquetaVariante, p.mostrarPrecioWeb
        FROM productos p
        LEFT JOIN productos padre ON p.productoPadreId = padre.id
        WHERE p.categoria = :category AND p.disponible = 1 AND (p.esPrincipalWeb = 0 OR p.esPrincipalWeb IS NULL)
        ORDER BY p.nombre ASC
    """)
    fun getProductsByCategoryFlow(category: String): Flow<List<ProductEntity>>

    @Query("SELECT DISTINCT categoria FROM productos WHERE disponible = 1 AND (esPrincipalWeb = 0 OR esPrincipalWeb IS NULL) ORDER BY categoria ASC")
    fun getAllCategoriesFlow(): Flow<List<String>>

    @Query("""
        SELECT p.id, p.codigoBarras, p.nombre, p.descripcion, p.categoria, p.precio, p.costo, p.stock, p.unidadMedida,
               COALESCE(p.imagenUrl, padre.imagenUrl) AS imagenUrl,
               COALESCE(p.thumbnailUrl, padre.thumbnailUrl) AS thumbnailUrl,
               p.imagenLocal, p.thumbnailLocal,
               p.disponible, p.destacado, p.etiquetas, p.esPrincipalWeb, p.productoPadreId, p.etiquetaVariante, p.mostrarPrecioWeb
        FROM productos p
        LEFT JOIN productos padre ON p.productoPadreId = padre.id
        WHERE p.codigoBarras = :barcode AND (p.esPrincipalWeb = 0 OR p.esPrincipalWeb IS NULL)
        LIMIT 1
    """)
    suspend fun getByBarcode(barcode: String): ProductEntity?

    @Query("""
        SELECT p.id, p.codigoBarras, p.nombre, p.descripcion, p.categoria, p.precio, p.costo, p.stock, p.unidadMedida,
               COALESCE(p.imagenUrl, padre.imagenUrl) AS imagenUrl,
               COALESCE(p.thumbnailUrl, padre.thumbnailUrl) AS thumbnailUrl,
               p.imagenLocal, p.thumbnailLocal,
               p.disponible, p.destacado, p.etiquetas, p.esPrincipalWeb, p.productoPadreId, p.etiquetaVariante, p.mostrarPrecioWeb
        FROM productos p
        LEFT JOIN productos padre ON p.productoPadreId = padre.id
        WHERE p.id = :id
        LIMIT 1
    """)
    suspend fun getById(id: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Query("UPDATE productos SET stock = stock - :quantity WHERE id = :productId")
    suspend fun reduceStock(productId: String, quantity: Double)

    @Query("UPDATE productos SET stock = stock + :quantity WHERE id = :productId")
    suspend fun increaseStock(productId: String, quantity: Double)

    @Query("DELETE FROM productos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE productos SET disponible = 0 WHERE id NOT IN (:activeIds)")
    suspend fun deactivateMissingProducts(activeIds: List<String>)

    @Transaction
    suspend fun syncCatalog(products: List<ProductEntity>) {
        if (products.isNotEmpty()) {
            insertAll(products)
            val activeIds = products.map { it.id }
            deactivateMissingProducts(activeIds)
        }
    }
}

data class SaleDetailWithProduct(
    val id: String,
    val venta_id: String,
    val producto_id: String,
    val producto_nombre: String? = null,
    val cantidad: Double,
    val precio_unitario: Double,
    val subtotal: Double
)

@Dao
interface SaleDao {
    @Query("SELECT * FROM ventas ORDER BY fecha DESC")
    fun getAllSalesFlow(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM ventas WHERE fecha LIKE :datePrefix || '%' AND anulado = 0 ORDER BY fecha DESC")
    fun getSalesByDateFlow(datePrefix: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM ventas WHERE id = :saleId LIMIT 1")
    suspend fun getSaleById(saleId: String): SaleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSales(sales: List<SaleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleDetails(details: List<SaleDetailEntity>)

    @Query("SELECT * FROM ventas_detalle WHERE venta_id = :saleId")
    suspend fun getDetailsForSale(saleId: String): List<SaleDetailEntity>

    @Query("""
        SELECT d.id, d.venta_id, d.producto_id, p.nombre AS producto_nombre, d.cantidad, d.precio_unitario, d.subtotal 
        FROM ventas_detalle d 
        LEFT JOIN productos p ON d.producto_id = p.id 
        WHERE d.venta_id = :saleId
    """)
    suspend fun getDetailsWithProductForSale(saleId: String): List<SaleDetailWithProduct>

    @Query("UPDATE ventas SET anulado = 1 WHERE id = :saleId")
    suspend fun cancelSale(saleId: String)

    @Query("SELECT * FROM ventas WHERE sincronizado = 0 ORDER BY fecha ASC")
    suspend fun getPendingSyncSales(): List<SaleEntity>

    @Query("UPDATE ventas SET sincronizado = 1 WHERE id = :saleId")
    suspend fun markSaleAsSynced(saleId: String)

    @Query("SELECT COUNT(*) FROM ventas WHERE sincronizado = 0")
    fun getPendingSalesCountFlow(): Flow<Int>
}

@Dao
interface CorrelativeDao {
    @Query("SELECT * FROM correlativos WHERE serie = :serie LIMIT 1")
    suspend fun getCorrelative(serie: String): CorrelativeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(correlative: CorrelativeEntity)

    @Query("UPDATE correlativos SET siguiente_numero = siguiente_numero + 1 WHERE serie = :serie")
    suspend fun increment(serie: String)
}
