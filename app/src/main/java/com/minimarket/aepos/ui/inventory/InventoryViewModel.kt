package com.minimarket.aepos.ui.inventory

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimarket.aepos.data.repository.ProductRepository
import com.minimarket.aepos.data.repository.StockFilterOption
import com.minimarket.aepos.domain.model.Product
import com.minimarket.aepos.utils.ImageOptimizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class InventoryUiState(
    val searchQuery: String = "",
    val selectedCategory: String = "Todos",
    val selectedStockFilter: StockFilterOption = StockFilterOption.ALL,
    val categories: List<String> = listOf("Todos"),
    val products: List<Product> = emptyList(),
    val isAddEditOpen: Boolean = false,
    val isScannerOpen: Boolean = false,
    val scannedBarcodeForForm: String? = null,
    val editingProduct: Product? = null,
    val selectedImageUri: Uri? = null,
    val selectedImageWebpBytes: ByteArray? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class InventoryViewModel(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
        observeProducts()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            productRepository.getCategoriesFlow().collect { cats ->
                _uiState.update { it.copy(categories = cats) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun observeProducts() {
        viewModelScope.launch {
            combine(
                _uiState.map { it.searchQuery }.distinctUntilChanged().debounce { q ->
                    if (q.isBlank()) 0L else 250L
                },
                _uiState.map { it.selectedCategory }.distinctUntilChanged(),
                _uiState.map { it.selectedStockFilter }.distinctUntilChanged()
            ) { query, category, stockFilter ->
                Triple(query, category, stockFilter)
            }.flatMapLatest { (query, category, stockFilter) ->
                productRepository.searchProductsFlow(query, category, stockFilter)
            }.collect { list ->
                _uiState.update { it.copy(products = list) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onCategorySelected(category: String) {
        _uiState.update { it.copy(selectedCategory = category, searchQuery = "") }
    }

    fun onStockFilterSelected(filter: StockFilterOption) {
        _uiState.update { it.copy(selectedStockFilter = filter) }
    }

    fun openScanner() {
        _uiState.update { it.copy(isScannerOpen = true) }
    }

    fun closeScanner() {
        _uiState.update { it.copy(isScannerOpen = false) }
    }

    fun onBarcodeScanned(barcode: String) {
        if (_uiState.value.isAddEditOpen) {
            _uiState.update { it.copy(scannedBarcodeForForm = barcode, isScannerOpen = false) }
        } else {
            _uiState.update { it.copy(searchQuery = barcode, isScannerOpen = false) }
        }
    }

    fun openAddProduct() {
        _uiState.update {
            it.copy(
                isAddEditOpen = true,
                editingProduct = null,
                scannedBarcodeForForm = null,
                selectedImageUri = null,
                selectedImageWebpBytes = null
            )
        }
    }

    fun openEditProduct(product: Product) {
        _uiState.update {
            it.copy(
                isAddEditOpen = true,
                editingProduct = product,
                scannedBarcodeForForm = null,
                selectedImageUri = null,
                selectedImageWebpBytes = null
            )
        }
    }

    fun closeAddEdit() {
        _uiState.update {
            it.copy(
                isAddEditOpen = false,
                editingProduct = null,
                scannedBarcodeForForm = null,
                selectedImageUri = null,
                selectedImageWebpBytes = null,
                isSaving = false
            )
        }
    }

    /**
     * Procesa la imagen seleccionada y la convierte a formato WebP (600x600 px @ 80% calidad).
     */
    fun onImageSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = ImageOptimizer.optimizeToWebp(
                context = context,
                inputUri = uri,
                targetWidth = 600,
                targetHeight = 600,
                quality = 80
            )
            result.fold(
                onSuccess = { webpBytes ->
                    _uiState.update {
                        it.copy(
                            selectedImageUri = uri,
                            selectedImageWebpBytes = webpBytes
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(errorMessage = "Error al procesar imagen: ${error.message}")
                    }
                }
            )
        }
    }

    fun saveProduct(
        context: Context,
        codigoBarras: String?,
        nombre: String,
        descripcion: String?,
        categoria: String,
        precio: Double,
        costo: Double?,
        stock: Double,
        unidadMedida: String,
        disponibleWeb: Boolean,
        destacado: Boolean
    ) {
        val state = _uiState.value
        if (state.isSaving) return

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val current = state.editingProduct
            val productToSave = Product(
                id = current?.id ?: UUID.randomUUID().toString(),
                codigoBarras = codigoBarras?.takeIf { it.isNotBlank() },
                nombre = nombre.trim(),
                descripcion = descripcion?.takeIf { it.isNotBlank() },
                categoria = categoria.trim().ifBlank { "General" },
                precio = precio,
                costo = costo,
                stock = stock,
                unidadMedida = unidadMedida.trim().ifBlank { "UND" },
                imagenUrl = current?.imagenUrl,
                imagenLocal = current?.imagenLocal,
                disponible = disponibleWeb,
                destacado = destacado
            )

            productRepository.saveProduct(
                context = context,
                product = productToSave,
                webpBytes = state.selectedImageWebpBytes
            )

            closeAddEdit()
        }
    }

    fun deleteProduct(id: String) {
        viewModelScope.launch {
            productRepository.deleteProduct(id)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
