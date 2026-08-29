package com.minimarket.aepos

import com.minimarket.aepos.data.repository.StockFilterOption
import com.minimarket.aepos.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class PosUnitTests {

    @Test
    fun testUserPermissions_AdminHasAllPermissions() {
        val adminUser = User(
            id = "admin-1",
            username = "admin",
            nombreCompleto = "Admin User",
            role = UserRole.ADMIN,
            permisos = listOf("all")
        )

        assertTrue(adminUser.isAdmin)
        assertTrue(adminUser.hasPermission("ventas:anular"))
        assertTrue(adminUser.hasPermission("inventario:editar"))
        assertTrue(adminUser.hasPermission("usuarios:gestionar"))
    }

    @Test
    fun testUserPermissions_ColaboradorHasSpecificPermissions() {
        val cashierUser = User(
            id = "cashier-1",
            username = "cajero",
            nombreCompleto = "Cajero User",
            role = UserRole.COLABORADOR,
            permisos = listOf("ventas:cobrar", "inventario:consultar")
        )

        assertFalse(cashierUser.isAdmin)
        assertTrue(cashierUser.hasPermission("ventas:cobrar"))
        assertTrue(cashierUser.hasPermission("inventario:consultar"))
        assertFalse(cashierUser.hasPermission("ventas:anular"))
        assertFalse(cashierUser.hasPermission("usuarios:gestionar"))
    }

    @Test
    fun testStockFilterLogic() {
        val products = listOf(
            Product(id = "1", codigoBarras = "111", nombre = "Arroz", categoria = "Abarrotes", precio = 4.50, stock = 25.0),
            Product(id = "2", codigoBarras = "222", nombre = "Aceite", categoria = "Abarrotes", precio = 8.00, stock = 5.0),
            Product(id = "3", codigoBarras = "333", nombre = "Leche", categoria = "Lácteos", precio = 4.20, stock = 0.0)
        )

        val inStock = products.filter { it.stock > 10.0 }
        val lowStock = products.filter { it.stock in 0.01..10.0 }
        val outOfStock = products.filter { it.stock <= 0.0 }

        assertEquals(1, inStock.size)
        assertEquals("Arroz", inStock[0].nombre)

        assertEquals(1, lowStock.size)
        assertEquals("Aceite", lowStock[0].nombre)

        assertEquals(1, outOfStock.size)
        assertEquals("Leche", outOfStock[0].nombre)
    }

    @Test
    fun testCartItemSubtotalRounding() {
        val product = Product(
            id = "p-1",
            codigoBarras = "123",
            nombre = "Atún",
            categoria = "Abarrotes",
            precio = 4.85,
            stock = 20.0
        )

        val cartItem = CartItem(product = product, cantidad = 3.0)
        assertEquals(14.55, cartItem.subtotal, 0.001)
    }
}
