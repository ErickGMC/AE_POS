package com.minimarket.aepos

import com.minimarket.aepos.domain.model.User
import com.minimarket.aepos.domain.model.UserRole
import org.junit.Assert.*
import org.junit.Test

class UserManagementTests {

    @Test
    fun testGranularPermissions() {
        val user = User(
            id = "u1",
            username = "seller",
            nombreCompleto = "Vendedor Test",
            role = UserRole.COLABORADOR,
            permisos = listOf(User.PERM_SALES_WRITE, User.PERM_SALES_READ, User.PERM_INVENTORY_READ)
        )

        assertTrue(user.hasPermission(User.PERM_SALES_WRITE))
        assertTrue(user.hasPermission(User.PERM_SALES_READ))
        assertTrue(user.hasPermission(User.PERM_INVENTORY_READ))
        
        assertFalse(user.hasPermission(User.PERM_INVENTORY_WRITE))
        assertFalse(user.hasPermission(User.PERM_REPORTS_VIEW))
        assertFalse(user.hasPermission(User.PERM_USERS_MANAGE))
    }

    @Test
    fun testAdminBypassAll() {
        val admin = User(
            id = "a1",
            username = "admin",
            nombreCompleto = "Admin",
            role = UserRole.ADMIN,
            permisos = listOf("all")
        )

        assertTrue(admin.hasPermission(User.PERM_USERS_MANAGE))
        assertTrue(admin.hasPermission(User.PERM_REPORTS_VIEW))
        assertTrue(admin.hasPermission("any:garbage:permission"))
    }

    @Test
    fun testUserStatusLogic() {
        val activeUser = User(id = "1", username = "u1", nombreCompleto = "N1", activo = true)
        val inactiveUser = User(id = "2", username = "u2", nombreCompleto = "N2", activo = false)

        assertTrue(activeUser.activo)
        assertFalse(inactiveUser.activo)
    }

    @Test
    fun testPinLengthValidationSim() {
        val validPin = "1234"
        val validPin6 = "123456"
        val invalidPinShort = "123"
        val invalidPinLong = "1234567"

        assertTrue(validPin.length == 4 || validPin.length == 6)
        assertTrue(validPin6.length == 4 || validPin6.length == 6)
        assertFalse(invalidPinShort.length == 4 || invalidPinShort.length == 6)
        assertFalse(invalidPinLong.length == 4 || invalidPinLong.length == 6)
    }

    @Test
    fun testDefaultCollaboratorIsReadOnly() {
        val defaultUser = User(
            id = "u_default",
            username = "consultor",
            nombreCompleto = "Consultor de Tienda",
            role = UserRole.COLABORADOR
        )

        // Por defecto solo puede consultar inventario
        assertTrue(defaultUser.hasPermission(User.PERM_INVENTORY_READ))
        assertTrue(defaultUser.hasPermission("inventario:consultar"))
        assertFalse(defaultUser.hasPermission(User.PERM_INVENTORY_WRITE))
        assertFalse(defaultUser.hasPermission(User.PERM_REPORTS_VIEW))
        assertFalse(defaultUser.hasPermission(User.PERM_USERS_MANAGE))
        assertFalse(defaultUser.hasPermission(User.PERM_SHOPPING_MANAGE))
        assertFalse(defaultUser.isAdmin)
    }

    @Test
    fun testBidirectionalTokenParity() {
        val desktopSyncedUser = User(
            id = "u_synced",
            username = "cajero_desktop",
            nombreCompleto = "Cajero Tienda Desktop",
            role = UserRole.COLABORADOR,
            permisos = listOf("inventario:consultar", "compras:gestionar")
        )

        // Verificamos equivalencia transparente entre tokens Desktop (español) y Android (inglés)
        assertTrue(desktopSyncedUser.hasPermission(User.PERM_INVENTORY_READ))
        assertTrue(desktopSyncedUser.hasPermission("inventario:consultar"))
        assertTrue(desktopSyncedUser.hasPermission(User.PERM_SHOPPING_MANAGE))
        assertTrue(desktopSyncedUser.hasPermission("compras:gestionar"))

        // Permisos no concedidos
        assertFalse(desktopSyncedUser.hasPermission(User.PERM_INVENTORY_WRITE))
        assertFalse(desktopSyncedUser.hasPermission("inventario:modificar"))
        assertFalse(desktopSyncedUser.hasPermission(User.PERM_REPORTS_VIEW))
        assertFalse(desktopSyncedUser.hasPermission("ventas:historial"))
        assertFalse(desktopSyncedUser.hasPermission(User.PERM_USERS_MANAGE))
        assertFalse(desktopSyncedUser.isAdmin)
    }

    @Test
    fun testCustomDelegatedPermissions() {
        val inventoryEditor = User(
            id = "u_editor",
            username = "editor_stock",
            nombreCompleto = "Asistente de Inventario",
            role = UserRole.COLABORADOR,
            permisos = listOf(User.PERM_INVENTORY_READ, User.PERM_INVENTORY_WRITE)
        )

        assertTrue(inventoryEditor.hasPermission(User.PERM_INVENTORY_READ))
        assertTrue(inventoryEditor.hasPermission(User.PERM_INVENTORY_WRITE))
        assertFalse(inventoryEditor.hasPermission(User.PERM_REPORTS_VIEW))
        assertFalse(inventoryEditor.hasPermission(User.PERM_SHOPPING_MANAGE))
        assertFalse(inventoryEditor.hasPermission(User.PERM_USERS_MANAGE))
        assertFalse(inventoryEditor.isAdmin)
    }
}
