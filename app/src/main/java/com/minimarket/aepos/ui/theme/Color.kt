package com.minimarket.aepos.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Paleta Corporativa Principal Emerald & Teal
val Emerald900 = Color(0xFF064E3B)
val Emerald700 = Color(0xFF047857)
val Emerald600 = Color(0xFF059669)
val Emerald500 = Color(0xFF10B981)
val Emerald400 = Color(0xFF34D399)
val Emerald300 = Color(0xFF6EE7B7)
val Emerald100 = Color(0xFFD1FAE5)

val Teal500 = Color(0xFF14B8A6)
val Teal600 = Color(0xFF0D9488)
val Teal400 = Color(0xFF2DD4BF)

// Dark Mode Slate Profundo (Aesthetics Mobile)
val Slate950 = Color(0xFF020617) // Fondo principal ultra-dark
val Slate900 = Color(0xFF0F172A) // Superficie de tarjetas y barras
val Slate850 = Color(0xFF161F33) // Superficie intermedia
val Slate800 = Color(0xFF1E293B) // Elementos interactivos / inputs
val Slate750 = Color(0xFF243044) // Bordes y separadores intermedios
val Slate700 = Color(0xFF334155) // Bordes sutiles
val Slate600 = Color(0xFF475569) // Textos secundarios atenuados
val Slate500 = Color(0xFF64748B) // Iconos desactivados
val Slate400 = Color(0xFF94A3B8) // Textos secundarios claros
val Slate300 = Color(0xFFCBD5E1) // Textos principales suaves
val Slate200 = Color(0xFFE2E8F0) // Bordes claros
val Slate100 = Color(0xFFF1F5F9) // Textos destacados / fondos claros
val Slate50 = Color(0xFFF8FAFC)

// Estados, Alertas y Categorías
val Red500 = Color(0xFFEF4444)
val Red400 = Color(0xFFF87171)
val Red900 = Color(0xFF450A0A)

val Amber500 = Color(0xFFF59E0B)
val Amber400 = Color(0xFFFBBF24)
val Amber900 = Color(0xFF451A03)

val Blue500 = Color(0xFF3B82F6)
val Blue400 = Color(0xFF60A5FA)
val Blue900 = Color(0xFF1E3A8A)

val Purple500 = Color(0xFFA855F7)
val Purple400 = Color(0xFFC084FC)

// Gradientes UI
val EmeraldTealGradient = Brush.horizontalGradient(
    listOf(Emerald500, Teal500)
)

val DarkSurfaceGradient = Brush.verticalGradient(
    listOf(Slate900, Slate950)
)

val AccentGlowGradient = Brush.radialGradient(
    listOf(Emerald500.copy(alpha = 0.25f), Color.Transparent)
)


