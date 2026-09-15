package com.minimarket.aepos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Categorías canónicas estándar alineadas con Tienda-web y Sistema-POS-para-TIenda
 */
val POS_STANDARD_CATEGORIES = listOf(
    "Abarrotes",
    "Bebidas",
    "Golosinas",
    "Lácteos",
    "Carnes",
    "Verduras",
    "Frutas",
    "Panadería",
    "Aseo y limpieza",
    "Ferreteria y electricidad",
    "Bazar",
    "Medicina",
    "Libreria",
    "Ocasión y Otros"
)

data class UnitItem(val code: String, val label: String)

/**
 * Unidades estándar para venta y balanza en AE_POS
 */
val POS_STANDARD_UNITS = listOf(
    UnitItem("UND", "UND — Unidad"),
    UnitItem("KG", "KG — Kilogramo (Balanza)"),
    UnitItem("GR", "GR — Gramo (Peso)"),
    UnitItem("LT", "LT — Litro"),
    UnitItem("PAQ", "PAQ — Paquete"),
    UnitItem("CAJA", "CAJA — Caja"),
    UnitItem("BOL", "BOL — Bolsa"),
    UnitItem("DOC", "DOC — Docena"),
    UnitItem("BOT", "BOT — Botella"),
    UnitItem("LATA", "LATA — Lata")
)

/**
 * Selector desplegable de categoría reutilizable Material 3
 */
@Composable
fun CategoryDropdownSelector(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    availableCategories: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var isCustomMode by remember { mutableStateOf(false) }
    var customCategoryText by remember { mutableStateOf("") }

    val allCategories = remember(availableCategories) {
        val dbCats = availableCategories.filter { it != "Todos" && it.isNotBlank() }
        (POS_STANDARD_CATEGORIES + dbCats).distinct().sorted()
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Box(modifier = modifier) {
        if (isCustomMode) {
            OutlinedTextField(
                value = customCategoryText,
                onValueChange = {
                    customCategoryText = it
                    onCategorySelected(it)
                },
                label = { Text("Nueva Categoría *") },
                trailingIcon = {
                    IconButton(onClick = {
                        isCustomMode = false
                        if (customCategoryText.isNotBlank()) {
                            onCategorySelected(customCategoryText)
                        } else if (allCategories.isNotEmpty()) {
                            onCategorySelected(allCategories.first())
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Volver a la lista",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                shape = MaterialTheme.shapes.small,
                colors = textFieldColors,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = selectedCategory.ifBlank { "Abarrotes" },
                onValueChange = {},
                readOnly = true,
                label = { Text("Categoría *") },
                trailingIcon = {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = "Desplegar categorías",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                shape = MaterialTheme.shapes.small,
                colors = textFieldColors,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .widthIn(min = 200.dp, max = 280.dp)
                    .heightIn(max = 320.dp)
            ) {
                // Opción para ingresar una nueva categoría
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "➕ Nueva categoría...",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        isCustomMode = true
                        customCategoryText = ""
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                allCategories.forEach { cat ->
                    val isSelected = cat.equals(selectedCategory, ignoreCase = true)
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Seleccionado",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onCategorySelected(cat)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Selector desplegable de unidad de medida reutilizable Material 3
 */
@Composable
fun UnitDropdownSelector(
    selectedUnit: String,
    onUnitSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var isCustomMode by remember {
        mutableStateOf(POS_STANDARD_UNITS.none { it.code.equals(selectedUnit.trim(), ignoreCase = true) } && selectedUnit.isNotBlank() && selectedUnit != "UND")
    }
    var customUnitText by remember { mutableStateOf(if (isCustomMode) selectedUnit else "") }

    val currentUnitLabel = remember(selectedUnit) {
        val found = POS_STANDARD_UNITS.find { it.code.equals(selectedUnit.trim(), ignoreCase = true) }
        found?.label ?: selectedUnit.ifBlank { "UND — Unidad" }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Box(modifier = modifier) {
        if (isCustomMode) {
            OutlinedTextField(
                value = customUnitText,
                onValueChange = {
                    customUnitText = it
                    onUnitSelected(it)
                },
                label = { Text("Unidad Medida *") },
                trailingIcon = {
                    IconButton(onClick = {
                        isCustomMode = false
                        onUnitSelected(customUnitText.ifBlank { "UND" })
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Volver a la lista",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                shape = MaterialTheme.shapes.small,
                colors = textFieldColors,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = currentUnitLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Unidad *") },
                trailingIcon = {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = "Desplegar unidades",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                shape = MaterialTheme.shapes.small,
                colors = textFieldColors,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .widthIn(min = 200.dp, max = 280.dp)
                    .heightIn(max = 320.dp)
            ) {
                POS_STANDARD_UNITS.forEach { unitItem ->
                    val isSelected = unitItem.code.equals(selectedUnit.trim(), ignoreCase = true)
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = unitItem.label,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Seleccionado",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onUnitSelected(unitItem.code)
                            expanded = false
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Otro (Personalizado...)",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        isCustomMode = true
                        customUnitText = ""
                    }
                )
            }
        }
    }
}
