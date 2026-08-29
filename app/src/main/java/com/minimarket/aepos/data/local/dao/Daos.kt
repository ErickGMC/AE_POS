package com.minimarket.aepos.data.local.dao

import androidx.room.*
import com.minimarket.aepos.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM productos WHERE disponible = 1 ORDER BY nombre ASC")
    fun getAllActiveProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM productos ORDER BY nombre ASC")
    fun getAllProductsFlow(): Flow<List<ProductEntity>>

    @Query("""
        SELECT * FROM productos 
        WHERE (nombre LIKE '%' || :query || '%' OR codigoBarras LIKE '%' || :query || '%')
        AND disponible = 1
        ORDER BY nombre ASC
    """)
    fun searchProductsFlow(query: String): Flow<List<ProductEntity>>

    @Query("""
        SELECT * FROM productos 
        WHERE categoria = :category AND disponible = 1
        ORDER BY nombre ASC
    """)
    fun getProductsByCategoryFlow(category: String): Flow<List<ProductEntity>>

    @Query("SELECT DISTINCT categoria FROM productos WHERE disponible = 1 ORDER BY categoria ASC")
    fun getAllCategoriesFlow(): Flow<List<String>>

    @Query("SELECT * FROM productos WHERE codigoBarras = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM productos WHERE id = :id LIMIT 1")
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
}

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

    @Query("UPDATE ventas SET anulado = 1 WHERE id = :saleId")
    suspend fun cancelSale(saleId: String)
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
