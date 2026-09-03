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

    @Test
    fun testSaleDetail_ProductNamePreserved() {
        val detail = SaleDetail(
            id = "d-1",
            ventaId = "v-1",
            productoId = "p-123",
            productoNombre = "Leche Gloria Entera 1L",
            cantidad = 2.0,
            precioUnitario = 4.20,
            subtotal = 8.40
        )

        assertEquals("Leche Gloria Entera 1L", detail.productoNombre)
        assertEquals(8.40, detail.subtotal, 0.001)
    }

    @Test
    fun testCashShift_DifferenceCalculations() {
        val expected = 350.50

        // Caso 1: Caja Cuadrada exacta
        val realExact = 350.50
        val diffExact = Math.round((realExact - expected) * 100.0) / 100.0
        assertEquals(0.0, diffExact, 0.001)

        // Caso 2: Sobrante de 10 soles
        val realSobrante = 360.50
        val diffSobrante = Math.round((realSobrante - expected) * 100.0) / 100.0
        assertEquals(10.0, diffSobrante, 0.001)

        // Caso 3: Faltante de 5 soles
        val realFaltante = 345.50
        val diffFaltante = Math.round((realFaltante - expected) * 100.0) / 100.0
        assertEquals(-5.0, diffFaltante, 0.001)
    }

    @Test
    fun testPosDestinationAccessByRole() {
        val cashier = User(
            id = "u-cashier",
            username = "cajero1",
            nombreCompleto = "Cajero Turno",
            role = UserRole.COLABORADOR,
            permisos = listOf("ventas:cobrar", "inventario:consultar")
        )

        assertTrue(cashier.hasPermission(User.PERM_SALES_WRITE))
        assertTrue(cashier.hasPermission(User.PERM_INVENTORY_READ))
        assertFalse(cashier.hasPermission(User.PERM_REPORTS_VIEW))
        assertFalse(cashier.hasPermission(User.PERM_SHOPPING_MANAGE))
        assertFalse(cashier.hasPermission(User.PERM_USERS_MANAGE))

        val admin = User(
            id = "u-admin",
            username = "admin",
            nombreCompleto = "Admin",
            role = UserRole.ADMIN,
            permisos = listOf("all")
        )

        assertTrue(admin.hasPermission(User.PERM_SALES_WRITE))
        assertTrue(admin.hasPermission(User.PERM_INVENTORY_READ))
        assertTrue(admin.hasPermission(User.PERM_REPORTS_VIEW))
        assertTrue(admin.hasPermission(User.PERM_SHOPPING_MANAGE))
        assertTrue(admin.hasPermission(User.PERM_USERS_MANAGE))
    }
}
