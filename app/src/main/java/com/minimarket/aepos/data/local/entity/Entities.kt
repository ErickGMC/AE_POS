package com.minimarket.aepos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "productos",
    indices = [
        Index(value = ["codigoBarras"], unique = false),
        Index(value = ["nombre"])
    ]
)
data class ProductEntity(
    @PrimaryKey
    val id: String,
    val codigoBarras: String?,
    val nombre: String,
    val descripcion: String? = null,
    val categoria: String,
    val precio: Double,
    val costo: Double? = null,
    val stock: Double,
    val unidadMedida: String = "UND",
    val imagenUrl: String? = null,
    val thumbnailUrl: String? = null,
    val imagenLocal: String? = null,
    val thumbnailLocal: String? = null,
    val disponible: Int = 1,
    val destacado: Int = 0,
    val etiquetas: String? = null,
    val esPrincipalWeb: Int = 0,
    val productoPadreId: String? = null,
    val etiquetaVariante: String? = null,
    val mostrarPrecioWeb: Int = 0
)

@Entity(
    tableName = "ventas",
    indices = [
        Index(value = ["fecha"]),
        Index(value = ["sincronizado"])
    ]
)
data class SaleEntity(
    @PrimaryKey
    val id: String,
    val fecha: String,
    val total: Double,
    val metodoPago: String,
    val estado: String = "completada",
    val clienteNombre: String? = null,
    val clienteDocumento: String? = null,
    val serie: String = "M001",
    val correlativoNumero: Int = 1,
    val comprobanteFormateado: String = "M001-00000001",
    val anulado: Int = 0,
    val sincronizado: Int = 1
)

@Entity(
    tableName = "ventas_detalle",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["venta_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["producto_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["venta_id"]),
        Index(value = ["producto_id"])
    ]
)
data class SaleDetailEntity(
    @PrimaryKey
    val id: String,
    val venta_id: String,
    val producto_id: String,
    val cantidad: Double,
    val precio_unitario: Double,
    val subtotal: Double
)

@Entity(tableName = "correlativos")
data class CorrelativeEntity(
    @PrimaryKey
    val serie: String,
    val siguiente_numero: Int
)

@Entity(tableName = "usuarios")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val username: String,
    val nombreCompleto: String = "Usuario",
    val email: String? = null,
    val pin: String = "1234",
    val role: String = "admin", // "admin" | "colaborador"
    val permisos: String = "all", // "all" o lista separada por comas
    val activo: Int = 1
)
