# AE_POS - Sistema Punto de Venta Móvil & Tablet (Nativo Android)

Sistema nativo de Punto de Venta (POS) y control de inventario desarrollado para **Minimarket Flor**, con soporte adaptativo para Smartphones y Tablets Android.

---

## 🚀 Stack Tecnológico

- **Lenguaje**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose con Material Design 3 y Window Size Classes (Phone / Tablet)
- **Persistencia Local**: Room Database (SQLite) con arquitectura Offline-First
- **Sincronización Cloud**: Firebase Firestore en tiempo real & Firebase Storage (WebP)
- **Autenticación**: Firebase Auth + Switch rápido por PIN de colaborador
- **Escaneo de Código de Barras**: CameraX + Google ML Kit Barcode Scanning
- **Carga de Imágenes**: Coil Compose

---

## 📱 Módulos y Funcionalidades

1. **Ventas POS (`sales/`)**:
   - Búsqueda en tiempo real por texto y escaneo de código de barras con feedback háptico.
   - Carrito táctil docked con cálculo automático de totales y redondeo financiero a 2 decimales.
   - Checkout con soporte para múltiples métodos de pago (Efectivo, Yape, Plin, Tarjetas, Pagos Mixtos) y cálculo dinámico de vuelto con denominaciones peruanas (S/10, S/20, S/50, S/100).
   - Generación de comprobantes serie `M001-XXXXXXXX` con decremento atómico de stock.

2. **Gestión de Inventario (`inventory/`)**:
   - Catálogo completo con filtros por categoría y estado de stock (En Stock, Stock Bajo, Agotados).
   - Creación y edición de productos con optimización automática de fotos a WebP (600x600 px).
   - Soporte para familias y variantes web (`esPrincipalWeb`, `productoPadreId`, `etiquetaVariante`).

3. **Control de Caja y Turnos (`cash/`)**:
   - Apertura y cierre de turnos con arqueo ciego y cálculo dinámico de diferencias.
   - Registro de movimientos de efectivo (Ingresos y Egresos con motivo).
   - Timeline detallado de transacciones del turno.

4. **Historial de Ventas y Reportes (`reports/`)**:
   - Resumen financiero de ventas del día por método de pago.
   - Detalle de comprobantes con opción de compartir por WhatsApp y anulación con reposición automática de inventario.

5. **Lista de Reabastecimiento (`shopping/`)**:
   - Generación de listas de compras agrupadas por categorías.
   - Exportación de pedidos estructurados a WhatsApp para proveedores.

6. **Gestión de Colaboradores y Seguridad (`users/` & `auth/`)**:
   - Control de acceso basado en roles (Administrador y Cajero/Colaborador).
   - Cambio rápido de sesión mediante PIN sin cerrar la app.

---

## 🛠️ Compilación y Ejecución

```bash
# Compilar APK Debug
./gradlew assembleDebug

# Ejecutar pruebas unitarias
./gradlew testDebugUnitTest
```
