package com.minimarket.aepos.domain.model

data class Product(
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
    val imagenLocal: String? = null,
    val disponible: Boolean = true,
    val destacado: Boolean = false,
    val esPrincipalWeb: Boolean = false,
    val productoPadreId: String? = null,
    val etiquetaVariante: String? = null,
    val mostrarPrecioWeb: Boolean = false
) {
    val isWeightUnit: Boolean
        get() = unidadMedida.trim().lowercase() in listOf("kg", "kilo", "kilos", "kilogramo", "kilogramos", "g", "gr", "gramo", "gramos", "granel", "peso")
}

data class CartItem(
    val product: Product,
    val cantidad: Double = 1.0,
    val precioUnitario: Double = product.precio
) {
    val subtotal: Double
        get() = Math.round(cantidad * precioUnitario * 100.0) / 100.0
}

enum class PaymentMethod(val label: String) {
    EFECTIVO("Efectivo"),
    YAPE("Yape"),
    PLIN("Plin"),
    TARJETA("Tarjeta"),
    TRANSFERENCIA("Transferencia"),
    MIXTO("Mixto")
}

data class Sale(
    val id: String,
    val fecha: String,
    val total: Double,
    val metodoPago: PaymentMethod,
    val estado: String = "completada",
    val clienteNombre: String? = null,
    val clienteDocumento: String? = null,
    val serie: String = "M001",
    val numeroComprobante: String,
    val anulado: Boolean = false,
    val items: List<SaleDetail> = emptyList()
)

data class SaleDetail(
    val id: String,
    val ventaId: String,
    val productoId: String,
    val productoNombre: String,
    val cantidad: Double,
    val precioUnitario: Double,
    val subtotal: Double
)

enum class UserRole(val label: String) {
    ADMIN("Administrador"),
    COLABORADOR("Colaborador / Consultor")
}

data class User(
    val id: String,
    val username: String,
    val nombreCompleto: String,
    val email: String? = null,
    val pin: String = "1234",
    val role: UserRole = UserRole.COLABORADOR,
    val permisos: List<String> = listOf(PERM_INVENTORY_READ),
    val activo: Boolean = true
) {
    val isAdmin: Boolean
        get() = role == UserRole.ADMIN

    fun hasPermission(permission: String): Boolean {
        if (isAdmin || permisos.contains("all") || permisos.contains("*")) return true
        
        val equivalentKeys = when (permission) {
            PERM_INVENTORY_READ, "inventario:consultar" -> listOf(PERM_INVENTORY_READ, "inventario:consultar", "inventario:leer", "inventory:read")
            PERM_INVENTORY_WRITE, "inventario:modificar" -> listOf(PERM_INVENTORY_WRITE, "inventario:modificar", "inventario:editar", "inventory:write")
            PERM_REPORTS_VIEW, "ventas:historial" -> listOf(PERM_REPORTS_VIEW, "ventas:historial", "reportes:ver", "ventas:leer", "reports:view")
            PERM_SHOPPING_MANAGE, "compras:gestionar" -> listOf(PERM_SHOPPING_MANAGE, "compras:gestionar", "compras:modificar", "shopping:manage")
            PERM_USERS_MANAGE, "usuarios:gestionar" -> listOf(PERM_USERS_MANAGE, "usuarios:gestionar", "usuarios:modificar", "users:manage")
            PERM_SALES_READ, "ventas:leer" -> listOf(PERM_SALES_READ, "ventas:leer", "ventas:historial", "sales:read")
            PERM_SALES_WRITE, "ventas:cobrar" -> listOf(PERM_SALES_WRITE, "ventas:cobrar", "ventas:escribir", "sales:write")
            else -> listOf(permission)
        }
        return permisos.any { p -> equivalentKeys.contains(p) }
    }

    companion object {
        const val PERM_INVENTORY_READ = "inventory:read"
        const val PERM_INVENTORY_WRITE = "inventory:write"
        const val PERM_REPORTS_VIEW = "reports:view"
        const val PERM_SHOPPING_MANAGE = "shopping:manage"
        const val PERM_USERS_MANAGE = "users:manage"
        const val PERM_SALES_READ = "sales:read"
        const val PERM_SALES_WRITE = "sales:write"
    }
}
