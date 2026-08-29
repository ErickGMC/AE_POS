package com.minimarket.aepos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.minimarket.aepos.data.local.dao.*
import com.minimarket.aepos.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ProductEntity::class,
        SaleEntity::class,
        SaleDetailEntity::class,
        CorrelativeEntity::class,
        UserEntity::class,
        CashShiftEntity::class,
        CashMovementEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun correlativeDao(): CorrelativeDao
    abstract fun cashDao(): CashDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ae_pos_database"
                )
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }

            suspend fun populateInitialData(db: AppDatabase) {
                // Correlativo Inicial M001
                db.correlativeDao().insertOrUpdate(
                    CorrelativeEntity(serie = "M001", siguiente_numero = 1)
                )

                // Usuarios Iniciales por Defecto
                val defaultUsers = listOf(
                    UserEntity(
                        id = "user_admin_001",
                        username = "admin",
                        nombreCompleto = "Erick Martínez (Admin)",
                        email = "erickmartinezc@gmail.com",
                        pin = "1234",
                        role = "admin",
                        permisos = "all",
                        activo = 1
                    ),
                    UserEntity(
                        id = "user_cajero_002",
                        username = "cajero",
                        nombreCompleto = "Flor (Caja Principal)",
                        email = "flor@minimarket.com",
                        pin = "0000",
                        role = "colaborador",
                        permisos = "inventory:read",
                        activo = 1
                    )
                )
                db.userDao().insertAll(defaultUsers)

                // Turno de Caja Inicial Abierto por defecto
                db.cashDao().insertOrUpdateShift(
                    CashShiftEntity(
                        id = "shift_init_001",
                        fechaApertura = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                        montoInicial = 100.0,
                        cajero = "Erick Martínez (Admin)",
                        estado = "abierta"
                    )
                )

                // Productos de muestra iniciales
                val sampleProducts = listOf(
                    ProductEntity(
                        id = "prod_001",
                        codigoBarras = "7750123456789",
                        nombre = "Arroz Costeño Extra 1kg",
                        descripcion = "Arroz seleccionado grano largo",
                        categoria = "Abarrotes",
                        precio = 4.80,
                        costo = 3.90,
                        stock = 50.0,
                        unidadMedida = "KG",
                        disponible = 1,
                        destacado = 1
                    ),
                    ProductEntity(
                        id = "prod_002",
                        codigoBarras = "7750987654321",
                        nombre = "Aceite Primor Premium 900ml",
                        descripcion = "Aceite vegetal 100% puro",
                        categoria = "Abarrotes",
                        precio = 8.50,
                        costo = 7.10,
                        stock = 30.0,
                        unidadMedida = "UND",
                        disponible = 1,
                        destacado = 1
                    ),
                    ProductEntity(
                        id = "prod_003",
                        codigoBarras = "7751112223334",
                        nombre = "Leche Gloria Azul 400g",
                        descripcion = "Leche evaporada entera",
                        categoria = "Lácteos",
                        precio = 4.20,
                        costo = 3.50,
                        stock = 45.0,
                        unidadMedida = "UND",
                        disponible = 1,
                        destacado = 1
                    ),
                    ProductEntity(
                        id = "prod_004",
                        codigoBarras = "7754445556667",
                        nombre = "Inca Kola 500ml",
                        descripcion = "Gaseosa sabor original bien helada",
                        categoria = "Bebidas",
                        precio = 3.00,
                        costo = 2.10,
                        stock = 60.0,
                        unidadMedida = "UND",
                        disponible = 1,
                        destacado = 1
                    ),
                    ProductEntity(
                        id = "prod_005",
                        codigoBarras = "7757778889990",
                        nombre = "Coca Cola 500ml",
                        descripcion = "Gaseosa refrescante",
                        categoria = "Bebidas",
                        precio = 3.00,
                        costo = 2.10,
                        stock = 60.0,
                        unidadMedida = "UND",
                        disponible = 1,
                        destacado = 1
                    ),
                    ProductEntity(
                        id = "prod_006",
                        codigoBarras = "7750001112223",
                        nombre = "Galleta Soda San Jorge Pack",
                        descripcion = "Galletas crocantes x 6 paquetes",
                        categoria = "Golosinas",
                        precio = 3.50,
                        costo = 2.60,
                        stock = 25.0,
                        unidadMedida = "PQTE",
                        disponible = 1,
                        destacado = 0
                    ),
                    ProductEntity(
                        id = "prod_007",
                        codigoBarras = "7753334445556",
                        nombre = "Detergente Bolívar 1kg",
                        descripcion = "Limpieza profunda con aroma floral",
                        categoria = "Limpieza",
                        precio = 9.80,
                        costo = 8.00,
                        stock = 20.0,
                        unidadMedida = "UND",
                        disponible = 1,
                        destacado = 0
                    ),
                    ProductEntity(
                        id = "prod_008",
                        codigoBarras = "7756667778889",
                        nombre = "Papel Higiénico Suave 4 Rollos",
                        descripcion = "Doble hoja suave y rendidor",
                        categoria = "Limpieza",
                        precio = 6.00,
                        costo = 4.80,
                        stock = 35.0,
                        unidadMedida = "PQTE",
                        disponible = 1,
                        destacado = 0
                    )
                )
                db.productDao().insertAll(sampleProducts)
            }
        }
    }
}
