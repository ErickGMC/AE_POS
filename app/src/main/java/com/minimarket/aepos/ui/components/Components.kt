package com.minimarket.aepos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.minimarket.aepos.domain.model.CartItem
import com.minimarket.aepos.domain.model.Product
import com.minimarket.aepos.ui.theme.*

@Composable
fun ProductCard(
    product: Product,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Slate900
        ),
        border = BorderStroke(1.dp, Slate800),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Imagen del Producto con Overlay de Categoria y Stock
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(Slate850)
            ) {
                val imageSource = product.imagenLocal ?: product.imagenUrl
                if (!imageSource.isNullOrBlank()) {
                    AsyncImage(
                        model = imageSource,
                        contentDescription = product.nombre,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = Slate600,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Sombra degradada inferior en la imagen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(35.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Slate900.copy(alpha = 0.8f))
                            )
                        )
                )

                // Categoría badge (Top Left)
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    color = Slate950.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = product.categoria,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Slate300,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                // Stock indicator badge (Top Right)
                val (stockColor, stockBg) = when {
                    product.stock <= 0.0 -> Pair(Red400, Red900.copy(alpha = 0.85f))
                    product.stock <= 10.0 -> Pair(Amber400, Amber900.copy(alpha = 0.85f))
                    else -> Pair(Emerald300, Emerald700.copy(alpha = 0.85f))
                }

                Surface(
                    shape = RoundedCornerShape(bottomStart = 8.dp),
                    color = stockBg,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "${product.stock.toInt()} ${product.unidadMedida}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        color = stockColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                // Nombre del producto
                Text(
                    text = product.nombre,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.heightIn(min = 34.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Precio y Botón Agregar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PRECIO",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = Slate400,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "S/ %.2f".format(product.precio),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            ),
                            color = Emerald400
                        )
                    }

                    FilledIconButton(
                        onClick = onClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Emerald500,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(34.dp)
                            .shadow(4.dp, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Agregar",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryFilterRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
    ) {
        items(categories) { cat ->
            val isSelected = cat == selectedCategory
            Surface(
                onClick = { onCategorySelected(cat) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) Emerald500 else Slate850,
                border = if (isSelected) null else BorderStroke(1.dp, Slate700.copy(alpha = 0.5f))
            ) {
                Text(
                    text = cat,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isSelected) Color.White else Slate300,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
fun CartItemRow(
    item: CartItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = BorderStroke(1.dp, Slate800)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product.nombre,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "S/ %.2f c/u".format(item.precioUnitario),
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Controles de Cantidad Móviles (+ / -)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .background(Slate800, RoundedCornerShape(20.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = onDecrement,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = if (item.cantidad <= 1) Icons.Default.Delete else Icons.Default.Remove,
                        contentDescription = "Disminuir",
                        tint = if (item.cantidad <= 1) Red400 else Slate300,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Text(
                    text = if (item.cantidad % 1.0 == 0.0) "${item.cantidad.toInt()}" else "%.2f".format(item.cantidad),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                IconButton(
                    onClick = onIncrement,
                    modifier = Modifier
                        .size(26.dp)
                        .background(Emerald500, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Aumentar",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Subtotal
            Text(
                text = "S/ %.2f".format(item.subtotal),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = Emerald400
                ),
                modifier = Modifier.widthIn(min = 60.dp)
            )
        }
    }
}

@Composable
fun SearchBarField(
    query: String,
    onQueryChange: (String) -> Unit,
    onScanBarcodeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = "Buscar producto o escanear...",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate500
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Buscar",
                tint = Emerald400,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpiar",
                            tint = Slate400,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onScanBarcodeClick,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(36.dp)
                        .background(Emerald500.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Escanear",
                        tint = Emerald400,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Emerald500,
            unfocusedBorderColor = Slate700,
            focusedContainerColor = Slate850,
            unfocusedContainerColor = Slate850,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

