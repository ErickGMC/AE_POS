package com.minimarket.aepos.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimarket.aepos.data.repository.ProductRepository
import com.minimarket.aepos.data.repository.SaleRepository
import com.minimarket.aepos.domain.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SalesUiState(
    val searchQuery: String = "",
    val selectedCategory: String = "Todos",
    val categories: List<String> = listOf("Todos"),
    val products: List<Product> = emptyList(),
    val cart: List<CartItem> = emptyList(),
    val selectedPaymentMethod: PaymentMethod = PaymentMethod.EFECTIVO,
    val cashReceived: String = "",
    val customerName: String = "",
    val customerDoc: String = "",
    val isCheckingOut: Boolean = false,
    val isScannerOpen: Boolean = false,
    val isPagosMixtosOpen: Boolean = false,
    val isProcessing: Boolean = false,
    val lastSuccessComprobante: String? = null,
    val errorMessage: String? = null
) {
    val total: Double
        get() {
            val raw = cart.sumOf { it.subtotal }
            return Math.round(raw * 100.0) / 100.0
        }

    val totalItemsCount: Double
        get() = cart.sumOf { it.cantidad }

    val changeAmount: Double
        get() {
            val received = cashReceived.toDoubleOrNull() ?: 0.0
            val change = received - total
            return if (change > 0) Math.round(change * 100.0) / 100.0 else 0.0
        }
}

class SalesViewModel(
    private val productRepository: ProductRepository,
    private val saleRepository: SaleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalesUiState())
    val uiState: StateFlow<SalesUiState> = _uiState.asStateFlow()

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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeProducts() {
        viewModelScope.launch {
            combine(
                _uiState.map { it.searchQuery }.distinctUntilChanged(),
                _uiState.map { it.selectedCategory }.distinctUntilChanged()
            ) { query, category ->
                Pair(query, category)
            }.flatMapLatest { (query, category) ->
                if (query.isNotBlank()) {
                    productRepository.searchProductsFlow(query)
                } else {
                    productRepository.getProductsByCategoryFlow(category)
                }
            }.collect { productList ->
                _uiState.update { it.copy(products = productList) }
            }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }
    }

    fun onCategorySelected(category: String) {
        _uiState.update { it.copy(selectedCategory = category, searchQuery = "") }
    }

    fun openScanner() {
        _uiState.update { it.copy(isScannerOpen = true) }
    }

    fun closeScanner() {
        _uiState.update { it.copy(isScannerOpen = false) }
    }

    fun openPagosMixtos() {
        _uiState.update { it.copy(isPagosMixtosOpen = true) }
    }

    fun closePagosMixtos() {
        _uiState.update { it.copy(isPagosMixtosOpen = false) }
    }

    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            val product = productRepository.getByBarcode(barcode)
            if (product != null) {
                addToCart(product)
            } else {
                _uiState.update {
                    it.copy(errorMessage = "Código '$barcode' no registrado en catálogo")
                }
            }
        }
    }

    fun addToCart(product: Product) {
        _uiState.update { state ->
            val currentCart = state.cart.toMutableList()
            val existingIndex = currentCart.indexOfFirst { it.product.id == product.id }

            if (existingIndex >= 0) {
                val current = currentCart[existingIndex]
                if (current.cantidad + 1.0 > product.stock) {
                    return@update state.copy(errorMessage = "Stock insuficiente para ${product.nombre}")
                }
                currentCart[existingIndex] = current.copy(cantidad = current.cantidad + 1.0)
            } else {
                if (product.stock < 1.0) {
                    return@update state.copy(errorMessage = "Producto agotado: ${product.nombre}")
                }
                currentCart.add(CartItem(product = product, cantidad = 1.0))
            }
            state.copy(cart = currentCart, errorMessage = null)
        }
    }

    fun decrementCartItem(productId: String) {
        _uiState.update { state ->
            val currentCart = state.cart.toMutableList()
            val index = currentCart.indexOfFirst { it.product.id == productId }
            if (index >= 0) {
                val current = currentCart[index]
                if (current.cantidad > 1.0) {
                    currentCart[index] = current.copy(cantidad = current.cantidad - 1.0)
                } else {
                    currentCart.removeAt(index)
                }
            }
            state.copy(cart = currentCart)
        }
    }

    fun removeFromCart(productId: String) {
        _uiState.update { state ->
            state.copy(cart = state.cart.filterNot { it.product.id == productId })
        }
    }

    fun clearCart() {
        _uiState.update { it.copy(cart = emptyList(), isCheckingOut = false, cashReceived = "") }
    }

    fun openCheckout() {
        if (_uiState.value.cart.isEmpty()) return
        _uiState.update { it.copy(isCheckingOut = true, cashReceived = "%.2f".format(it.total)) }
    }

    fun closeCheckout() {
        _uiState.update { it.copy(isCheckingOut = false) }
    }

    fun setPaymentMethod(method: PaymentMethod) {
        _uiState.update { it.copy(selectedPaymentMethod = method) }
    }

    fun setCashReceived(amountStr: String) {
        _uiState.update { it.copy(cashReceived = amountStr) }
    }

    fun setCustomerInfo(name: String, doc: String) {
        _uiState.update { it.copy(customerName = name, customerDoc = doc) }
    }

    fun processSale() {
        val state = _uiState.value
        if (state.cart.isEmpty()) return
        if (state.isProcessing) return

        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }

        viewModelScope.launch {
            val result = saleRepository.processSale(
                items = state.cart,
                total = state.total,
                paymentMethod = state.selectedPaymentMethod,
                customerName = state.customerName,
                customerDoc = state.customerDoc
            )

            result.fold(
                onSuccess = { comprobante ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            isCheckingOut = false,
                            cart = emptyList(),
                            cashReceived = "",
                            customerName = "",
                            customerDoc = "",
                            lastSuccessComprobante = comprobante
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = error.message ?: "Error al procesar la venta"
                        )
                    }
                }
            )
        }
    }

    fun processPagoMixtoSale(pagos: List<PagoParcial>) {
        val state = _uiState.value
        if (state.cart.isEmpty() || state.isProcessing) return

        val montoEfectivo = pagos.filter { it.metodo.equals("Efectivo", ignoreCase = true) }.sumOf { it.monto }

        _uiState.update { it.copy(isProcessing = true, isPagosMixtosOpen = false) }

        viewModelScope.launch {
            val result = saleRepository.processSale(
                items = state.cart,
                total = state.total,
                paymentMethod = PaymentMethod.MIXTO,
                customerName = state.customerName,
                customerDoc = state.customerDoc,
                montoEfectivo = montoEfectivo
            )

            result.fold(
                onSuccess = { comprobante ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            isCheckingOut = false,
                            cart = emptyList(),
                            cashReceived = "",
                            lastSuccessComprobante = comprobante
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = error.message ?: "Error al procesar la venta"
                        )
                    }
                }
            )
        }
    }

    fun dismissSuccessDialog() {
        _uiState.update { it.copy(lastSuccessComprobante = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
