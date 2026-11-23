package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.CasheaSettlement
import com.example.utils.SecureEncryptedBackupManager
import com.example.utils.ImportBackupResult
import com.example.data.local.Customer
import com.example.data.local.DeliveryNote
import com.example.data.local.DeliveryNoteItem
import com.example.data.local.Expense
import com.example.data.local.HeldSale
import com.example.data.local.HeldSaleItem
import com.example.data.local.PosTerminalRecord
import com.example.data.local.Product
import com.example.data.local.Quote
import com.example.data.local.QuoteItem
import com.example.data.local.Sale
import com.example.data.local.SaleItem
import com.example.data.local.User
import com.example.data.local.Supplier
import com.example.data.local.PurchaseInvoice
import com.example.data.repository.AppRepository
import com.example.data.repository.GeminiRepository
import com.example.data.repository.CloudSyncRepository
import com.example.data.repository.FirestoreSyncRepository
import com.example.ui.assistant.*
import com.example.utils.GeminiLiveCallbacks
import com.example.utils.GeminiLiveManager
import com.example.utils.GeminiLiveStatus
import com.example.utils.SpeechToTextManager
import com.example.utils.ExtractedInvoiceItem
import com.example.utils.ParsedInvoiceOcrResult
import com.example.utils.ParsedPosTicketResult
import com.example.utils.PosTicketOcrParser
import com.example.utils.PosTicketOcrScanner
import com.example.utils.PurchaseInvoiceOcrParser
import com.example.utils.PurchaseInvoiceOcrScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Calendar
import java.util.Locale

class PosViewModel(
    private val repository: AppRepository,
    private val geminiRepository: GeminiRepository,
    private val cloudSyncRepository: CloudSyncRepository,
    private val firestoreSyncRepository: FirestoreSyncRepository
) : ViewModel() {

    val prefs = repository.prefs

    val allProducts = repository.allProducts.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allCustomers = repository.allCustomers.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allSales = repository.allSales.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allSaleItems = repository.allSaleItems.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allCreditSales = repository.allCreditSales.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allSuppliers = repository.allSuppliers.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allPurchaseInvoices = repository.allPurchaseInvoices.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allUsers = repository.allUsers
        .map { list -> list.filterNot { it.username.equals("Olivtech", ignoreCase = true) || it.username.equals("default", ignoreCase = true) } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allHeldSales = repository.allHeldSales.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allDeliveryNotes = repository.allDeliveryNotes.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allQuotes = repository.allQuotes.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allExpenses = repository.allExpenses.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allCasheaSettlements = repository.allCasheaSettlements.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allPosTerminalRecords = repository.allPosTerminalRecords.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _bcvRate = MutableStateFlow(prefs.lastBcvRate.toDouble())
    val bcvRate = _bcvRate.asStateFlow()

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart = _cart.asStateFlow()

    private val _quoteCart = MutableStateFlow<List<CartItem>>(emptyList())
    val quoteCart = _quoteCart.asStateFlow()

    private val _deliveryNoteCart = MutableStateFlow<List<CartItem>>(emptyList())
    val deliveryNoteCart = _deliveryNoteCart.asStateFlow()

    private val _deliveryNoteCurrency = MutableStateFlow("USD")
    val deliveryNoteCurrency = _deliveryNoteCurrency.asStateFlow()

    private val _posPrefillCustomer = MutableStateFlow<Pair<String, String>?>(null)
    val posPrefillCustomer = _posPrefillCustomer.asStateFlow()

    private val _voiceStatus = MutableStateFlow("")
    val voiceStatus = _voiceStatus.asStateFlow()

    // --- CONVERSATIONAL ASSISTANT & GEMINI AI ---
    val assistantEngine = ConversationalAssistantEngine(geminiRepository)

    private val _assistantMessages = MutableStateFlow<List<AssistantChatMessage>>(
        listOf(
            AssistantChatMessage(
                sender = AssistantSender.ASSISTANT,
                text = "¡Hola! Soy Oli, tu asistente comercial y consultor inteligente en tiempo real. Puedo ayudarte con todo lo relacionado al POS, inventario, facturación, presupuestos, notas de entrega, regulaciones SENIAT, así como también con consultas de conocimiento general, especificaciones técnicas (como compatibilidad de cargadores, tecnología) e información para que no tengas que salir de la aplicación. ¿Qué necesitas hoy?"
            )
        )
    )
    val assistantMessages = _assistantMessages.asStateFlow()

    private val _isAssistantThinking = MutableStateFlow(false)
    val isAssistantThinking = _isAssistantThinking.asStateFlow()

    private val _pendingAssistantAction = MutableStateFlow<PendingAssistantAction?>(null)
    val pendingAssistantAction = _pendingAssistantAction.asStateFlow()

    private val _geminiCustomApiKey = MutableStateFlow(prefs.geminiCustomApiKey)
    val geminiCustomApiKey = _geminiCustomApiKey.asStateFlow()

    private val _isGeminiLearningAiEnabled = MutableStateFlow(prefs.isGeminiLearningAiEnabled)
    val isGeminiLearningAiEnabled = _isGeminiLearningAiEnabled.asStateFlow()

    private val _geminiSelectedModel = MutableStateFlow(prefs.geminiSelectedModel)
    val geminiSelectedModel = _geminiSelectedModel.asStateFlow()

    // --- GEMINI LIVE & FLUID CONTINUOUS VOICE ---
    private val _isGeminiLiveActive = MutableStateFlow(false)
    val isGeminiLiveActive = _isGeminiLiveActive.asStateFlow()

    private val _geminiLiveStatus = MutableStateFlow(GeminiLiveStatus.DISCONNECTED)
    val geminiLiveStatus = _geminiLiveStatus.asStateFlow()

    private val _geminiLiveStatusText = MutableStateFlow("")
    val geminiLiveStatusText = _geminiLiveStatusText.asStateFlow()

    private val _isGeminiLiveMuted = MutableStateFlow(false)
    val isGeminiLiveMuted = _isGeminiLiveMuted.asStateFlow()

    private val _liveUserTranscript = MutableStateFlow("")
    val liveUserTranscript = _liveUserTranscript.asStateFlow()

    private val _liveModelTranscript = MutableStateFlow("")
    val liveModelTranscript = _liveModelTranscript.asStateFlow()

    private val _liveAudioLevel = MutableStateFlow(0f)
    val liveAudioLevel = _liveAudioLevel.asStateFlow()

    private val _assistantNavigationRequest = MutableStateFlow<String?>(null)
    val assistantNavigationRequest = _assistantNavigationRequest.asStateFlow()

    private var geminiLiveManager: GeminiLiveManager? = null
    private var continuousSttManager: SpeechToTextManager? = null

    private val _appTheme = MutableStateFlow(prefs.appTheme)
    val appTheme = _appTheme.asStateFlow()

    fun setAppTheme(theme: String) {
        prefs.appTheme = theme
        _appTheme.value = theme
    }

    private val _backupMode = MutableStateFlow<String?>(prefs.backupMode.ifBlank { null })
    val backupMode = _backupMode.asStateFlow()

    private val _googleAccount = MutableStateFlow<String>(prefs.googleAccount.ifBlank { "leornardcano@gmail.com" })
    val googleAccount = _googleAccount.asStateFlow()

    val isGoogleSignedIn: StateFlow<Boolean> = combine(_backupMode, _googleAccount) { mode, account ->
        mode == "GOOGLE_CLOUD" && account.isNotBlank()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, prefs.backupMode == "GOOGLE_CLOUD" && prefs.googleAccount.isNotBlank())

    private val _isGoogleAuthLoading = MutableStateFlow(false)
    val isGoogleAuthLoading = _isGoogleAuthLoading.asStateFlow()

    private val _googleAuthUserEmail = MutableStateFlow<String?>(firestoreSyncRepository.currentUserEmail)
    val googleAuthUserEmail = _googleAuthUserEmail.asStateFlow()

    val firestoreSyncError: StateFlow<String?> = firestoreSyncRepository.lastError

    val lastFirebaseBackupTimestamp: StateFlow<Long> = firestoreSyncRepository.lastEncryptedBackupTimestamp
    val lastFirebaseBackupSummary: StateFlow<String> = firestoreSyncRepository.lastEncryptedBackupSummary
    val remoteEncryptedBackupInfo: StateFlow<com.example.data.remote.FsEncryptedBackup?> = firestoreSyncRepository.remoteEncryptedBackupInfo
    val isUploadingFirebaseBackup: StateFlow<Boolean> = firestoreSyncRepository.isUploadingEncryptedBackup
    val isRestoringFirebaseBackup: StateFlow<Boolean> = firestoreSyncRepository.isRestoringEncryptedBackup

    fun signInWithGoogle(context: Context, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isGoogleAuthLoading.value = true
            try {
                val helper = com.example.utils.GoogleSignInHelper(context)
                val res = helper.signInWithGoogle()
                res.onSuccess { email ->
                    _googleAuthUserEmail.value = email
                    prefs.googleAccount = email
                    prefs.backupMode = "GOOGLE_CLOUD"
                    _backupMode.value = "GOOGLE_CLOUD"
                    _googleAccount.value = email
                    firestoreSyncRepository.startSync()
                    _isGoogleAuthLoading.value = false
                    onResult(true, "¡Sesión iniciada con Google ($email)! Base de datos sincronizada en tiempo real con la nube.")
                }.onFailure { err ->
                    _isGoogleAuthLoading.value = false
                    onResult(false, err.localizedMessage ?: "Error al iniciar sesión con Google.")
                }
            } catch (e: Exception) {
                _isGoogleAuthLoading.value = false
                onResult(false, e.localizedMessage ?: "Error desconocido en Google Sign-In.")
            }
        }
    }

    fun signOutGoogle(context: Context, onComplete: () -> Unit) {
        viewModelScope.launch {
            val helper = com.example.utils.GoogleSignInHelper(context)
            helper.signOut()
            firestoreSyncRepository.stopSync()
            _googleAuthUserEmail.value = null
            prefs.googleAccount = ""
            prefs.backupMode = ""
            _backupMode.value = null
            _googleAccount.value = ""
            onComplete()
        }
    }

    // --- COMPANY PROFILE (REGISTRO ÚNICO) ---
    private val _isCompanyRegistered = MutableStateFlow(prefs.isCompanyRegistered)
    val isCompanyRegistered = _isCompanyRegistered.asStateFlow()

    private val _companyGoogleAccount = MutableStateFlow(prefs.companyGoogleAccount)
    val companyGoogleAccount = _companyGoogleAccount.asStateFlow()

    private val _companyRegisteredAt = MutableStateFlow(prefs.companyRegisteredAt)
    val companyRegisteredAt = _companyRegisteredAt.asStateFlow()

    private val _storeName = MutableStateFlow(prefs.storeName)
    val storeName = _storeName.asStateFlow()

    private val _storeRif = MutableStateFlow(prefs.storeRif)
    val storeRif = _storeRif.asStateFlow()

    private val _storeAddress = MutableStateFlow(prefs.storeAddress)
    val storeAddress = _storeAddress.asStateFlow()

    private val _storePhone = MutableStateFlow(prefs.storePhone)
    val storePhone = _storePhone.asStateFlow()

    private val _storeLogoPath = MutableStateFlow(prefs.storeLogoPath)
    val storeLogoPath = _storeLogoPath.asStateFlow()

    private val _cashRegisterNumber = MutableStateFlow(prefs.cashRegisterNumber)
    val cashRegisterNumber = _cashRegisterNumber.asStateFlow()

    private val _isCashRegisterCommissionEnabled = MutableStateFlow(prefs.isCashRegisterCommissionEnabled)
    val isCashRegisterCommissionEnabled = _isCashRegisterCommissionEnabled.asStateFlow()

    private val _commissionEnabledRegisters = MutableStateFlow(prefs.commissionEnabledRegisters)
    val commissionEnabledRegisters = _commissionEnabledRegisters.asStateFlow()

    fun setCashRegisterNumber(number: String) {
        val cleanNumber = number.trim().ifBlank { "01" }
        prefs.cashRegisterNumber = cleanNumber
        _cashRegisterNumber.value = cleanNumber
    }

    fun setCashRegisterCommissionEnabled(enabled: Boolean) {
        prefs.isCashRegisterCommissionEnabled = enabled
        _isCashRegisterCommissionEnabled.value = enabled
    }

    fun setCommissionEnabledRegisters(registers: String) {
        prefs.commissionEnabledRegisters = registers.trim()
        _commissionEnabledRegisters.value = registers.trim()
    }

    fun setCashRegisterCommissionSettings(enabled: Boolean, registers: String) {
        setCashRegisterCommissionEnabled(enabled)
        setCommissionEnabledRegisters(registers)
    }

    fun isCommissionActiveForCurrentRegister(): Boolean {
        val allowedRegistersStr = _commissionEnabledRegisters.value.trim()
        val currentReg = _cashRegisterNumber.value.trim()
        if (allowedRegistersStr.isNotBlank()) {
            val list = allowedRegistersStr.split(",").map { it.trim().lowercase() }
            return list.contains(currentReg.lowercase())
        }
        return _isCashRegisterCommissionEnabled.value
    }

    // --- OPTIONAL MINIMUM FIXED PROFIT MARGIN ---
    private val _isMinProfitMarginEnabled = MutableStateFlow(prefs.isMinProfitMarginEnabled)
    val isMinProfitMarginEnabled = _isMinProfitMarginEnabled.asStateFlow()

    private val _minProfitMarginPercent = MutableStateFlow(prefs.minProfitMarginPercent.toDouble())
    val minProfitMarginPercent = _minProfitMarginPercent.asStateFlow()

    fun setMinProfitMarginSettings(enabled: Boolean, percent: Double) {
        val safePercent = percent.coerceAtLeast(0.0)
        prefs.isMinProfitMarginEnabled = enabled
        prefs.minProfitMarginPercent = safePercent.toFloat()
        _isMinProfitMarginEnabled.value = enabled
        _minProfitMarginPercent.value = safePercent
    }

    // --- LICENSE VALIDATION WITH GOOGLE ---
    private val _licenseGoogleAccount = MutableStateFlow(prefs.licenseGoogleAccount)
    val licenseGoogleAccount = _licenseGoogleAccount.asStateFlow()

    private val _licenseActivatedAt = MutableStateFlow(prefs.licenseActivatedAt)
    val licenseActivatedAt = _licenseActivatedAt.asStateFlow()

    // --- LICENSE MANAGEMENT & LIMITS ---
    private val _isLicensed = MutableStateFlow(prefs.isLicensed)
    val isLicensed = _isLicensed.asStateFlow()

    private val _licenseKey = MutableStateFlow(prefs.licenseKey)
    val licenseKey = _licenseKey.asStateFlow()

    private val _licenseAdminUser = MutableStateFlow(prefs.licenseAdminUser)
    val licenseAdminUser = _licenseAdminUser.asStateFlow()

    private val _isUnlimitedInventory = MutableStateFlow(prefs.isUnlimitedInventory)
    val isUnlimitedInventory = _isUnlimitedInventory.asStateFlow()

    private val _customProductLimit = MutableStateFlow(prefs.customProductLimit)
    val customProductLimit = _customProductLimit.asStateFlow()

    private val _isUnlimitedDailyInvoices = MutableStateFlow(prefs.isUnlimitedDailyInvoices)
    val isUnlimitedDailyInvoices = _isUnlimitedDailyInvoices.asStateFlow()

    private val _customDailyInvoiceLimit = MutableStateFlow(prefs.customDailyInvoiceLimit)
    val customDailyInvoiceLimit = _customDailyInvoiceLimit.asStateFlow()

    private val _isFullTimeLicense = MutableStateFlow(prefs.isFullTimeLicense)
    val isFullTimeLicense = _isFullTimeLicense.asStateFlow()

    private val _licenseExpiryTimestamp = MutableStateFlow(prefs.licenseExpiryTimestamp)
    val licenseExpiryTimestamp = _licenseExpiryTimestamp.asStateFlow()

    private val _licenseStatus = MutableStateFlow(prefs.licenseStatus)
    val licenseStatus = _licenseStatus.asStateFlow()

    val lowStockAlertProducts: StateFlow<List<Product>> = allProducts
        .map { products ->
            products
                .filter { it.isLowStockAlertTriggered(products) }
                .sortedWith(
                    compareBy<Product> { it.effectiveStockForAlert(products) > 1e-6 }
                        .thenBy { it.effectiveStockForAlert(products) }
                        .thenBy { it.name.lowercase(java.util.Locale.ROOT) }
                )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- SYSTEM NOTIFICATIONS (REAL-TIME ALERTS) ---
    val systemNotifications: StateFlow<List<SystemNotification>> = combine(
        allProducts,
        _isLicensed,
        _licenseExpiryTimestamp,
        _isFullTimeLicense
    ) { products, licensed, expiry, fullTime ->
        val list = mutableListOf<SystemNotification>()

        // 1. Configurable per-product Low Stock & Out-of-Stock check
        val triggeredProducts = products.filter { it.isLowStockAlertTriggered(products) }
        val outOfStock = triggeredProducts.filter { it.isOutOfStockAlertTriggered(products) }
        val lowStockProducts = triggeredProducts.filter { !it.isOutOfStockAlertTriggered(products) }

        if (lowStockProducts.isNotEmpty()) {
            val previewNames = lowStockProducts.take(3).joinToString(", ") { it.name }
            val extra = if (lowStockProducts.size > 3) " y ${lowStockProducts.size - 3} más" else ""
            list.add(SystemNotification(
                id = "low_stock",
                title = "Existencias en Stock Mínimo (${lowStockProducts.size})",
                message = "Alcanzaron su mínimo definido: $previewNames$extra.",
                type = NotificationType.LOW_STOCK
            ))
        }

        if (outOfStock.isNotEmpty()) {
            val previewNames = outOfStock.take(3).joinToString(", ") { it.name }
            val extra = if (outOfStock.size > 3) " y ${outOfStock.size - 3} más" else ""
            list.add(SystemNotification(
                id = "out_of_stock",
                title = "Productos Agotados (${outOfStock.size})",
                message = "Sin existencias en inventario: $previewNames$extra.",
                type = NotificationType.LOW_STOCK
            ))
        }

        // 2. License expiry check
        if (!fullTime) {
            if (licensed && expiry > 0) {
                val now = System.currentTimeMillis()
                val diff = expiry - now
                val days = diff / (24 * 60 * 60 * 1000)
                if (days in 0..7) {
                    list.add(SystemNotification(
                        id = "license_expiry",
                        title = "Licencia por Vencer",
                        message = if (days == 0L) "Tu licencia vence hoy." else "Tu licencia vence en $days días.",
                        type = NotificationType.LICENSE_EXPIRY
                    ))
                } else if (days < 0) {
                    list.add(SystemNotification(
                        id = "license_expired",
                        title = "Licencia Vencida",
                        message = "Tu licencia ha expirado. Favor renovar para continuar con todos los servicios.",
                        type = NotificationType.LICENSE_EXPIRY
                    ))
                }
            } else if (!licensed) {
                list.add(SystemNotification(
                    id = "no_license",
                    title = "Sin Licencia Activa",
                    message = "El sistema está operando en modo limitado. Activa una licencia para acceso total.",
                    type = NotificationType.LICENSE_EXPIRY
                ))
            }
        }

        // 3. System Health / Sync Alerts
        if (prefs.isCloudConnected && !cloudSyncRepository.isConfigured()) {
            list.add(SystemNotification(
                id = "cloud_sync_error",
                title = "Error de Sincronización",
                message = "La nube está habilitada pero las credenciales de Supabase no son válidas.",
                type = NotificationType.SYSTEM_ALERT
            ))
        }

        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- BLUETOOTH & USB THERMAL PRINTER (BLE, CLASSIC & USB OTG) ---
    private val _printerConnectionType = MutableStateFlow(prefs.printerConnectionType)
    val printerConnectionType = _printerConnectionType.asStateFlow()

    private val _printerPaperWidth = MutableStateFlow(prefs.printerPaperWidth)
    val printerPaperWidth = _printerPaperWidth.asStateFlow()

    private val _printerMacAddress = MutableStateFlow(prefs.printerMacAddress)
    val printerMacAddress = _printerMacAddress.asStateFlow()

    private val _printerName = MutableStateFlow(prefs.printerName)
    val printerName = _printerName.asStateFlow()

    private val _printerUsbVendorId = MutableStateFlow(prefs.printerUsbVendorId)
    val printerUsbVendorId = _printerUsbVendorId.asStateFlow()

    private val _printerUsbProductId = MutableStateFlow(prefs.printerUsbProductId)
    val printerUsbProductId = _printerUsbProductId.asStateFlow()

    private val _isFiscalPrinter = MutableStateFlow(prefs.isFiscalPrinter)
    val isFiscalPrinter = _isFiscalPrinter.asStateFlow()

    private val _fiscalPrinterBrand = MutableStateFlow(prefs.fiscalPrinterBrand)
    val fiscalPrinterBrand = _fiscalPrinterBrand.asStateFlow()

    fun setIsFiscalPrinter(enabled: Boolean) {
        prefs.isFiscalPrinter = enabled
        _isFiscalPrinter.value = enabled
    }

    fun setFiscalPrinterBrand(brand: String) {
        prefs.fiscalPrinterBrand = brand
        _fiscalPrinterBrand.value = brand
    }

    fun setPrinterConfig(
        connectionType: String,
        macOrId: String,
        name: String,
        usbVendorId: Int = -1,
        usbProductId: Int = -1,
        paperWidth: String = prefs.printerPaperWidth
    ) {
        prefs.printerConnectionType = connectionType
        prefs.printerMacAddress = macOrId.trim()
        prefs.printerName = name.trim()
        prefs.printerUsbVendorId = usbVendorId
        prefs.printerUsbProductId = usbProductId
        prefs.printerPaperWidth = paperWidth

        _printerConnectionType.value = connectionType
        _printerMacAddress.value = macOrId.trim()
        _printerName.value = name.trim()
        _printerUsbVendorId.value = usbVendorId
        _printerUsbProductId.value = usbProductId
        _printerPaperWidth.value = paperWidth
    }

    fun setPrinterPaperWidth(width: String) {
        prefs.printerPaperWidth = width
        _printerPaperWidth.value = width
    }

    fun unlinkPrinter() {
        prefs.printerConnectionType = "BLE_FUNPRINT"
        prefs.printerMacAddress = ""
        prefs.printerName = ""
        prefs.printerUsbVendorId = -1
        prefs.printerUsbProductId = -1
        prefs.printerUsbDeviceName = ""

        _printerConnectionType.value = "BLE_FUNPRINT"
        _printerMacAddress.value = ""
        _printerName.value = ""
        _printerUsbVendorId.value = -1
        _printerUsbProductId.value = -1
    }

    fun isPrinterConfigured(): Boolean {
        return _printerMacAddress.value.isNotBlank() || _printerUsbVendorId.value != -1
    }

    fun printSaleTicket(
        context: android.content.Context,
        sale: Sale,
        items: List<SaleItem>,
        onStatus: ((String) -> Unit)? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!isPrinterConfigured()) {
            onResult(false, "No hay ninguna impresora configurada")
            return
        }

        viewModelScope.launch {
            try {
                val widthLabel = if (prefs.printerPaperWidth.contains("80")) "80mm (576px)" else "58mm (384px)"
                onStatus?.invoke("Generando formato de factura $widthLabel...")
                val bitmap = com.example.ui.components.TicketUtils.generateTicketBitmap(context, prefs, sale, items)
                val printerManager = com.example.utils.ThermalPrinterManager(context)
                val result = printerManager.printUniversal(prefs, bitmap, onStatus)
                result.onSuccess {
                    onResult(true, "¡Factura impresa exitosamente!")
                }.onFailure { err ->
                    onResult(false, err.message ?: "Error al imprimir en el dispositivo térmico")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Error desconocido al procesar impresión")
            }
        }
    }

    fun printDeliveryNoteTicket(
        context: android.content.Context,
        note: DeliveryNote,
        items: List<DeliveryNoteItem>,
        onStatus: ((String) -> Unit)? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!isPrinterConfigured()) {
            onResult(false, "No hay ninguna impresora configurada")
            return
        }

        viewModelScope.launch {
            try {
                val widthLabel = if (prefs.printerPaperWidth.contains("80")) "80mm (576px)" else "58mm (384px)"
                onStatus?.invoke("Generando nota de entrega $widthLabel...")
                val bitmap = com.example.ui.components.TicketUtils.generateDeliveryNoteBitmap(context, prefs, note, items)
                val printerManager = com.example.utils.ThermalPrinterManager(context)
                val result = printerManager.printUniversal(prefs, bitmap, onStatus)
                result.onSuccess {
                    onResult(true, "¡Nota de entrega impresa exitosamente!")
                }.onFailure { err ->
                    onResult(false, err.message ?: "Error al imprimir en el dispositivo térmico")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Error al procesar impresión")
            }
        }
    }

    fun printQuoteTicket(
        context: android.content.Context,
        quote: Quote,
        items: List<QuoteItem>,
        onStatus: ((String) -> Unit)? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!isPrinterConfigured()) {
            onResult(false, "No hay ninguna impresora configurada")
            return
        }

        viewModelScope.launch {
            try {
                val widthLabel = if (prefs.printerPaperWidth.contains("80")) "80mm (576px)" else "58mm (384px)"
                onStatus?.invoke("Generando presupuesto $widthLabel...")
                val bitmap = com.example.ui.components.TicketUtils.generateQuoteTicketBitmap(context, prefs, quote, items)
                val printerManager = com.example.utils.ThermalPrinterManager(context)
                val result = printerManager.printUniversal(prefs, bitmap, onStatus)
                result.onSuccess {
                    onResult(true, "¡Presupuesto impreso exitosamente!")
                }.onFailure { err ->
                    onResult(false, err.message ?: "Error al imprimir en el dispositivo térmico")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Error al procesar impresión")
            }
        }
    }

    fun printTestTicket(
        context: android.content.Context,
        onStatus: ((String) -> Unit)? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!isPrinterConfigured()) {
            onResult(false, "No hay ninguna impresora configurada")
            return
        }

        viewModelScope.launch {
            try {
                val printerManager = com.example.utils.ThermalPrinterManager(context)
                val bitmap = printerManager.generateTestTicketBitmap(prefs)
                val result = printerManager.printUniversal(prefs, bitmap, onStatus)
                result.onSuccess {
                    onResult(true, "¡Prueba de impresión exitosa!")
                }.onFailure { err ->
                    onResult(false, err.message ?: "Error al enviar prueba a la impresora")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Error en la prueba de impresión")
            }
        }
    }

    fun printFiscalTestTicket(
        context: android.content.Context,
        onStatus: (String) -> Unit,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!isPrinterConfigured()) {
            onResult(false, "No hay ninguna impresora configurada")
            return
        }

        viewModelScope.launch {
            try {
                val printerManager = com.example.utils.ThermalPrinterManager(context)
                val fiscalPayload = com.example.utils.FiscalPrinterHelper.buildFiscalTestCommands(
                    brand = prefs.fiscalPrinterBrand
                )
                val result = printerManager.printFiscalCommands(prefs, fiscalPayload, onStatus)
                result.onSuccess {
                    onResult(true, "¡Protocolo fiscal de ${prefs.fiscalPrinterBrand} verificado con éxito!")
                }.onFailure { err ->
                    onResult(false, err.message ?: "Error en protocolo de impresora fiscal")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Error al enviar comandos a la impresora fiscal")
            }
        }
    }

    private val _cartDiscountUsd = MutableStateFlow(0.0)
    val cartDiscountUsd = _cartDiscountUsd.asStateFlow()

    fun setCartDiscountUsd(amount: Double) {
        _cartDiscountUsd.value = if (amount > 0.0) amount else 0.0
    }

    fun isMasterKey(code: String): Boolean {
        val clean = code.trim()
        return clean == "23390497Mano$"
    }

    fun getMaxProductsAllowed(): Int {
        return if (!_isLicensed.value) 10
        else if (_isUnlimitedInventory.value) Int.MAX_VALUE
        else _customProductLimit.value
    }

    fun getMaxDailyInvoicesAllowed(): Int {
        return if (!_isLicensed.value) 5
        else if (_isUnlimitedDailyInvoices.value) Int.MAX_VALUE
        else _customDailyInvoiceLimit.value
    }

    fun getTodaySalesCount(): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        return allSales.value.count { it.timestamp >= startOfDay }
    }

    fun canAddMoreProducts(): Boolean {
        val count = allProducts.value.size
        return count < getMaxProductsAllowed()
    }

    fun canProcessMoreInvoicesToday(): Boolean {
        val todayCount = getTodaySalesCount()
        return todayCount < getMaxDailyInvoicesAllowed()
    }

    fun activateLicense(
        key: String,
        adminUser: String = "admin",
        unlimitedInventory: Boolean = true,
        productLimit: Int = 50,
        unlimitedInvoices: Boolean = true,
        invoiceLimit: Int = 30,
        onResultMsg: ((String) -> Unit)? = null
    ): Boolean {
        if (key.isBlank()) return false

        val cleanKey = key.trim()
        val isMaster = isMasterKey(cleanKey)
        val now = System.currentTimeMillis()
        val googleAcc = _googleAccount.value

        if (isMaster) {
            // Master License = Lifetime / Full Access
            prefs.isLicensed = true
            prefs.licenseKey = cleanKey
            prefs.licenseAdminUser = adminUser
            prefs.isFullTimeLicense = true
            prefs.isUnlimitedInventory = true
            prefs.customProductLimit = 999999
            prefs.isUnlimitedDailyInvoices = true
            prefs.customDailyInvoiceLimit = 999999
            prefs.licenseActivatedAt = now
            prefs.licenseExpiryTimestamp = 0L // No expiry for master key
            prefs.licenseStatus = "ACTIVE (MASTER)"

            _isLicensed.value = true
            _licenseKey.value = cleanKey
            _licenseAdminUser.value = adminUser
            _isFullTimeLicense.value = true
            _isUnlimitedInventory.value = true
            _customProductLimit.value = 999999
            _isUnlimitedDailyInvoices.value = true
            _customDailyInvoiceLimit.value = 999999
            _licenseActivatedAt.value = now
            _licenseExpiryTimestamp.value = 0L
            _licenseStatus.value = "ACTIVE (MASTER)"

            onResultMsg?.invoke("¡Licencia Maestra activada correctamente!")
            return true
        } else {
            // Standard code activation (Custom or from server)
            val isFullTime = (unlimitedInventory && unlimitedInvoices)
            val finalUnlimitedInv = if (isFullTime) true else unlimitedInventory
            val finalProdLimit = if (isFullTime) 999999 else productLimit
            val finalUnlimitedInvoices = if (isFullTime) true else unlimitedInvoices
            val finalInvLimit = if (isFullTime) 999999 else invoiceLimit

            val thirtyDaysMillis = 30L * 24L * 60L * 60L * 1000L
            val expiryTime = now + thirtyDaysMillis

            prefs.isLicensed = true
            prefs.licenseKey = cleanKey
            prefs.licenseAdminUser = adminUser
            prefs.isFullTimeLicense = isFullTime
            prefs.isUnlimitedInventory = finalUnlimitedInv
            prefs.customProductLimit = finalProdLimit
            prefs.isUnlimitedDailyInvoices = finalUnlimitedInvoices
            prefs.customDailyInvoiceLimit = finalInvLimit
            prefs.licenseActivatedAt = now
            prefs.licenseExpiryTimestamp = expiryTime
            prefs.licenseStatus = "ACTIVE"

            _isLicensed.value = true
            _licenseKey.value = cleanKey
            _licenseAdminUser.value = adminUser
            _isFullTimeLicense.value = isFullTime
            _isUnlimitedInventory.value = finalUnlimitedInv
            _customProductLimit.value = finalProdLimit
            _isUnlimitedDailyInvoices.value = finalUnlimitedInvoices
            _customDailyInvoiceLimit.value = finalInvLimit
            _licenseActivatedAt.value = now
            _licenseExpiryTimestamp.value = expiryTime
            _licenseStatus.value = "ACTIVE"

            onResultMsg?.invoke("¡Licencia registrada y activada!")
            return true
        }
    }

    /**
     * Checks server authorization for the user's Google account and updates active license status.
     */
    fun checkLicenseStatusWithServer(onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val googleAcc = _googleAccount.value.ifBlank { prefs.googleAccount }

            // Local check first
            if (prefs.isLicensed && prefs.licenseExpiryTimestamp > 0L && now >= prefs.licenseExpiryTimestamp) {
                prefs.isLicensed = false
                prefs.licenseStatus = "EXPIRED"
                _isLicensed.value = false
                _licenseStatus.value = "EXPIRED"
            }

            val remoteRecord = cloudSyncRepository.checkRemoteAccountLicense(googleAcc)
            if (remoteRecord != null) {
                prefs.lastLicenseServerCheck = now
                val isExpiredOrLimited = remoteRecord.status.equals("EXPIRED", ignoreCase = true) ||
                                        remoteRecord.status.equals("LIMITED", ignoreCase = true) ||
                                        remoteRecord.daysRemaining <= 0 ||
                                        (remoteRecord.expiryTimestamp > 0L && now >= remoteRecord.expiryTimestamp)

                if (isExpiredOrLimited) {
                    prefs.isLicensed = false
                    prefs.licenseStatus = "EXPIRED"
                    _isLicensed.value = false
                    _licenseStatus.value = "EXPIRED"
                    onResult?.invoke(false, "El servidor indica que la cuenta $googleAcc no tiene licencia activa o fue expirada.")
                } else {
                    // Authorized on server!
                    prefs.isLicensed = true
                    prefs.licenseExpiryTimestamp = remoteRecord.expiryTimestamp
                    prefs.licenseStatus = "ACTIVE"
                    _isLicensed.value = true
                    _licenseExpiryTimestamp.value = remoteRecord.expiryTimestamp
                    _licenseStatus.value = "ACTIVE"

                    val daysLeft = if (remoteRecord.expiryTimestamp > now) {
                        ((remoteRecord.expiryTimestamp - now) / (1000 * 60 * 60 * 24)).toInt() + 1
                    } else remoteRecord.daysRemaining

                    onResult?.invoke(true, "Licencia autorizada en servidor para $googleAcc. Días restantes: $daysLeft")
                }
            } else {
                if (_isLicensed.value) {
                    onResult?.invoke(true, "Licencia activa en modo local.")
                } else {
                    onResult?.invoke(false, "El producto no está licenciado. Contacte a soporte para obtener un código de activación.")
                }
            }
        }
    }

    fun updateLicenseLimits(
        unlimitedInventory: Boolean,
        productLimit: Int,
        unlimitedInvoices: Boolean,
        invoiceLimit: Int
    ) {
        prefs.isUnlimitedInventory = unlimitedInventory
        prefs.customProductLimit = productLimit
        prefs.isUnlimitedDailyInvoices = unlimitedInvoices
        prefs.customDailyInvoiceLimit = invoiceLimit

        _isUnlimitedInventory.value = unlimitedInventory
        _customProductLimit.value = productLimit
        _isUnlimitedDailyInvoices.value = unlimitedInvoices
        _customDailyInvoiceLimit.value = invoiceLimit
    }

    fun deactivateLicense() {
        prefs.isLicensed = false
        prefs.licenseKey = ""
        prefs.licenseAdminUser = ""
        prefs.isFullTimeLicense = false
        prefs.isUnlimitedInventory = false
        prefs.customProductLimit = 10
        prefs.isUnlimitedDailyInvoices = false
        prefs.customDailyInvoiceLimit = 5
        prefs.licenseGoogleAccount = ""
        prefs.licenseActivatedAt = 0L

        _isLicensed.value = false
        _licenseKey.value = ""
        _licenseAdminUser.value = ""
        _isFullTimeLicense.value = false
        _isUnlimitedInventory.value = false
        _customProductLimit.value = 10
        _isUnlimitedDailyInvoices.value = false
        _customDailyInvoiceLimit.value = 5
        _licenseGoogleAccount.value = ""
        _licenseActivatedAt.value = 0L
    }

    // --- COMPANY UNIQUE REGISTRATION ---

    private fun isPlaceholderCompany(name: String, rif: String): Boolean {
        val n = name.trim()
        val r = rif.trim()
        val defName = n.isBlank() || n.equals("Mi Tienda", ignoreCase = true)
        val defRif = r.isBlank() || r.equals("J-00000000-0", ignoreCase = true) || r == "00000000"
        return defName && defRif
    }

    fun registerCompany(
        name: String,
        rif: String,
        phone: String,
        address: String
    ): Result<Unit> {
        val cleanName = name.trim()
        val cleanRif = rif.trim().uppercase()
        val cleanPhone = phone.trim()
        val cleanAddress = address.trim()

        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("El nombre de la empresa no puede estar vacío."))
        }
        if (cleanRif.isBlank()) {
            return Result.failure(IllegalArgumentException("El RIF no puede estar vacío."))
        }

        val googleAcc = if (isGoogleSignedIn.value) _googleAccount.value else (firestoreSyncRepository.currentUserEmail ?: "")
        val now = if (prefs.companyRegisteredAt > 0L) prefs.companyRegisteredAt else System.currentTimeMillis()

        prefs.storeName = cleanName
        prefs.storeRif = cleanRif
        prefs.storePhone = cleanPhone
        prefs.storeAddress = cleanAddress
        prefs.companyGoogleAccount = googleAcc
        prefs.companyRegisteredAt = now
        prefs.isCompanyRegistered = true

        _storeName.value = cleanName
        _storeRif.value = cleanRif
        _storePhone.value = cleanPhone
        _storeAddress.value = cleanAddress
        _companyGoogleAccount.value = googleAcc
        _companyRegisteredAt.value = now
        _isCompanyRegistered.value = true

        viewModelScope.launch {
            firestoreSyncRepository.syncCompanyProfile(cleanName, cleanRif, cleanPhone, cleanAddress)
        }

        // Notify server of device registration & upload full account JSON backup to cloud if linked
        if (googleAcc.isNotBlank()) {
            cloudSyncRepository.registerDeviceAndAccount(googleAcc, cleanName, cleanRif)
            cloudSyncRepository.uploadFullAccountBackup(googleAcc)
        }
        performPartialBackup()

        return Result.success(Unit)
    }

    fun updateCompanyProfile(
        newName: String,
        newRif: String,
        newPhone: String,
        newAddress: String
    ): Result<Unit> {
        val cleanName = newName.trim()
        val cleanRif = newRif.trim().uppercase()
        val cleanPhone = newPhone.trim()
        val cleanAddress = newAddress.trim()

        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("El nombre de la empresa no puede estar vacío."))
        }
        if (cleanRif.isBlank()) {
            return Result.failure(IllegalArgumentException("El RIF no puede estar vacío."))
        }

        prefs.storeName = cleanName
        prefs.storeRif = cleanRif
        prefs.storePhone = cleanPhone
        prefs.storeAddress = cleanAddress
        prefs.isCompanyRegistered = true

        _storeName.value = cleanName
        _storeRif.value = cleanRif
        _storePhone.value = cleanPhone
        _storeAddress.value = cleanAddress
        _isCompanyRegistered.value = true

        viewModelScope.launch {
            firestoreSyncRepository.syncCompanyProfile(cleanName, cleanRif, cleanPhone, cleanAddress)
        }

        if (prefs.googleAccount.isNotBlank()) {
            cloudSyncRepository.registerDeviceAndAccount(prefs.googleAccount, cleanName, cleanRif)
            cloudSyncRepository.uploadFullAccountBackup(prefs.googleAccount)
        }
        return Result.success(Unit)
    }

    fun updateCompanyContact(newPhone: String, newAddress: String) {
        val cleanPhone = newPhone.trim()
        val cleanAddress = newAddress.trim()
        prefs.storePhone = cleanPhone
        prefs.storeAddress = cleanAddress
        _storePhone.value = cleanPhone
        _storeAddress.value = cleanAddress
        viewModelScope.launch {
            firestoreSyncRepository.syncCompanyProfile(_storeName.value, _storeRif.value, cleanPhone, cleanAddress)
        }
        if (prefs.googleAccount.isNotBlank()) {
            cloudSyncRepository.uploadFullAccountBackup(prefs.googleAccount)
        }
    }

    fun updateStoreLogo(path: String) {
        prefs.storeLogoPath = path
        _storeLogoPath.value = path
        if (prefs.googleAccount.isNotBlank()) {
            cloudSyncRepository.uploadFullAccountBackup(prefs.googleAccount)
        }
    }

    private val _lastPartialBackupTimestamp = MutableStateFlow(prefs.lastPartialBackupTimestamp)
    val lastPartialBackupTimestamp = _lastPartialBackupTimestamp.asStateFlow()

    fun performPartialBackup() {
        cloudSyncRepository.performPartialBackup()
        if (prefs.googleAccount.isNotBlank()) {
            cloudSyncRepository.uploadFullAccountBackup(prefs.googleAccount)
        }
        _lastPartialBackupTimestamp.value = prefs.lastPartialBackupTimestamp
    }

    fun setBackupModeGoogle(
        email: String = "leornardcano@gmail.com",
        onRestoreCompleted: ((Boolean, String) -> Unit)? = null
    ) {
        prefs.backupMode = "GOOGLE_CLOUD"
        prefs.googleAccount = email
        _backupMode.value = "GOOGLE_CLOUD"
        _googleAccount.value = email

        viewModelScope.launch {
            // Check if account backup JSON already exists on cloud for this Google email
            val restoredBackup = cloudSyncRepository.checkAndRestoreRemoteAccountBackup(email)
            if (restoredBackup != null) {
                _storeName.value = prefs.storeName
                _storeRif.value = prefs.storeRif
                _storePhone.value = prefs.storePhone
                _storeAddress.value = prefs.storeAddress
                _companyRegisteredAt.value = prefs.companyRegisteredAt
                _companyGoogleAccount.value = prefs.companyGoogleAccount
                _isCompanyRegistered.value = prefs.isCompanyRegistered

                onRestoreCompleted?.invoke(
                    true,
                    "¡Datos de empresa e inventario restaurados desde la nube! Empresa: '${restoredBackup.storeName}', RIF: '${restoredBackup.storeRif}', ${restoredBackup.products.size} productos."
                )
            } else {
                cloudSyncRepository.registerDeviceAndAccount(email, prefs.storeName, prefs.storeRif)
                if (prefs.isCompanyRegistered) {
                    cloudSyncRepository.uploadFullAccountBackup(email)
                }
                performPartialBackup()
                onRestoreCompleted?.invoke(false, "Conectado a Google Cloud ($email). No se encontró respaldo previo.")
            }
        }
    }

    fun setBackupModeOffline() {
        prefs.backupMode = "OFFLINE_LOCAL"
        _backupMode.value = "OFFLINE_LOCAL"
    }

    fun resetBackupMode() {
        prefs.backupMode = ""
        _backupMode.value = null
    }

    private val _cloudUsername = MutableStateFlow(prefs.cloudUsername)
    val cloudUsername = _cloudUsername.asStateFlow()

    private val _cloudPassword = MutableStateFlow(prefs.cloudPassword)
    val cloudPassword = _cloudPassword.asStateFlow()

    private val _isCloudConnected = MutableStateFlow(prefs.isCloudConnected)
    val isCloudConnected = _isCloudConnected.asStateFlow()

    fun saveAndConnectCloudNetwork(user: String, pass: String, onResult: (Boolean, String) -> Unit) {
        val cleanUser = user.trim()
        val cleanPass = pass.trim()
        viewModelScope.launch {
            if (cleanUser.equals("Olivtech", ignoreCase = true) && cleanPass == "Sr4K1L!") {
                prefs.cloudUsername = cleanUser
                prefs.cloudPassword = cleanPass
                prefs.isCloudConnected = true
                prefs.isLicensed = true
                prefs.isUnlimitedInventory = true
                prefs.isUnlimitedDailyInvoices = true
                prefs.isFullTimeLicense = true
                prefs.licenseStatus = "Licencia Completa y Servicios Ilimitados (Olivtech)"
                _cloudUsername.value = cleanUser
                _cloudPassword.value = cleanPass
                _isCloudConnected.value = true
                _isLicensed.value = true
                _isUnlimitedInventory.value = true
                _isUnlimitedDailyInvoices.value = true
                _isFullTimeLicense.value = true
                _licenseStatus.value = "Licencia Completa y Servicios Ilimitados (Olivtech)"
                onResult(true, "Conectado exitosamente con credencial maestra 'Olivtech'. Servicios y licencia completa activados.")
                return@launch
            }

            val authResult = cloudSyncRepository.authenticateUser(cleanUser, cleanPass)
            authResult.onSuccess {
                prefs.cloudUsername = cleanUser
                prefs.cloudPassword = cleanPass
                prefs.isCloudConnected = true
                _cloudUsername.value = cleanUser
                _cloudPassword.value = cleanPass
                _isCloudConnected.value = true
                cloudSyncRepository.startSync()
                onResult(true, "¡Conexión a la red Supabase exitosa con el usuario '$cleanUser'!")
            }.onFailure { err ->
                onResult(false, err.message ?: "Error al autenticar credenciales en Supabase")
            }
        }
    }

    fun disconnectCloudNetwork() {
        prefs.isCloudConnected = false
        _isCloudConnected.value = false
    }

    fun changeCloudPassword(
        currentPass: String,
        newPass: String,
        confirmPass: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val cleanCurrent = currentPass.trim()
        val cleanNew = newPass.trim()
        val cleanConfirm = confirmPass.trim()

        if (cleanCurrent.isEmpty()) {
            onResult(false, "Ingrese su contraseña actual.")
            return
        }
        if (cleanNew.isEmpty()) {
            onResult(false, "Ingrese la nueva contraseña.")
            return
        }
        if (cleanConfirm.isEmpty()) {
            onResult(false, "Confirme la nueva contraseña.")
            return
        }
        if (cleanNew != cleanConfirm) {
            onResult(false, "La nueva contraseña y la confirmación no coinciden.")
            return
        }
        if (cleanNew.length < 4) {
            onResult(false, "La nueva contraseña debe tener al menos 4 caracteres.")
            return
        }

        val targetUser = _currentUser.value?.username?.ifBlank { null }
            ?: _cloudUsername.value.ifBlank { null }
            ?: prefs.cloudUsername.ifBlank { null }
            ?: "Olivtech"

        viewModelScope.launch {
            val res = cloudSyncRepository.changeSupabasePassword(targetUser, cleanCurrent, cleanNew)
            res.onSuccess {
                // Actualizar usuario en Room local si existe
                val localUser = repository.getUserByUsername(targetUser)
                if (localUser != null) {
                    repository.updateUser(localUser.copy(pin = cleanNew))
                }
                prefs.cloudPassword = cleanNew
                _cloudPassword.value = cleanNew
                cloudSyncRepository.syncInstantBackend()
                onResult(true, "¡Contraseña actualizada exitosamente en la plataforma de Supabase y localmente!")
            }.onFailure { err ->
                onResult(false, err.message ?: "Error al modificar la contraseña en Supabase.")
            }
        }
    }

    init {
        if (prefs.isCompanyRegistered && isPlaceholderCompany(prefs.storeName, prefs.storeRif)) {
            prefs.isCompanyRegistered = false
            _isCompanyRegistered.value = false
        }

        viewModelScope.launch {
            firestoreSyncRepository.companyProfile.collect { profile ->
                if (profile != null && !isPlaceholderCompany(profile.storeName, profile.storeRif)) {
                    if (profile.storeName.isNotBlank() && !profile.storeName.equals("Mi Tienda", ignoreCase = true)) {
                        _storeName.value = profile.storeName
                    }
                    if (profile.storeRif.isNotBlank() && !profile.storeRif.equals("J-00000000-0", ignoreCase = true) && profile.storeRif != "00000000") {
                        _storeRif.value = profile.storeRif
                    }
                    if (profile.storePhone.isNotBlank() && profile.storePhone != "0000-0000000") {
                        _storePhone.value = profile.storePhone
                    }
                    if (profile.storeAddress.isNotBlank() && profile.storeAddress != "Dirección") {
                        _storeAddress.value = profile.storeAddress
                    }
                    if (profile.googleEmail.isNotBlank()) {
                        _companyGoogleAccount.value = profile.googleEmail
                    }
                    _isCompanyRegistered.value = true
                }
            }
        }

        viewModelScope.launch {
            try {
                if (repository.getUsersCount() == 0) {
                    repository.insertUser(User(username = "Olivtech", pin = "Sr4K1L!", role = "ADMIN", canEarnCommission = false))
                    repository.insertUser(User(username = "admin", pin = "1234", role = "ADMIN", canEarnCommission = false))
                    repository.insertUser(User(username = "supervisor", pin = "0000", role = "SUPERVISOR", canEarnCommission = false))
                    repository.insertUser(User(username = "caja", pin = "1111", role = "CASHIER", canEarnCommission = true))
                } else {
                    val oldDefault = repository.getUserByUsername("default")
                    if (oldDefault != null) {
                        repository.deleteUser(oldDefault)
                    }
                    if (repository.getUserByUsername("Olivtech") == null) {
                        repository.insertUser(User(username = "Olivtech", pin = "Sr4K1L!", role = "ADMIN", canEarnCommission = false))
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("PosViewModel", "Error al inicializar usuarios base: ${e.message}")
            }

            try {
                if (prefs.isCloudConnected || cloudSyncRepository.isConfigured() || !googleAuthUserEmail.value.isNullOrBlank()) {
                    cloudSyncRepository.startSync()
                    firestoreSyncRepository.startSync()
                }

                // Check for remote account backup if logged in with Google Cloud
                if (prefs.googleAccount.isNotBlank() && prefs.backupMode == "GOOGLE_CLOUD") {
                    val restored = cloudSyncRepository.checkAndRestoreRemoteAccountBackup(prefs.googleAccount)
                    if (restored != null) {
                        _storeName.value = prefs.storeName
                        _storeRif.value = prefs.storeRif
                        _storePhone.value = prefs.storePhone
                        _storeAddress.value = prefs.storeAddress
                        _companyRegisteredAt.value = prefs.companyRegisteredAt
                        _companyGoogleAccount.value = prefs.companyGoogleAccount
                        _isCompanyRegistered.value = prefs.isCompanyRegistered
                    }
                    cloudSyncRepository.registerDeviceAndAccount(prefs.googleAccount, prefs.storeName, prefs.storeRif)
                }
            } catch (e: Throwable) {
                android.util.Log.e("PosViewModel", "Error al inicializar respaldo remoto: ${e.message}")
            }

            // Periodic partial backup worker loop (runs every 15 minutes, stateless REST)
            while (true) {
                try {
                    delay(15 * 60 * 1000L)
                    performPartialBackup()
                } catch (e: Throwable) {
                    android.util.Log.d("PosViewModel", "Worker backup interval notice: ${e.message}")
                }
            }
        }
        refreshRate()
    }

    suspend fun login(username: String, pin: String): Boolean {
        val cleanUser = username.trim()
        val cleanPin = pin.trim()
        if (cleanUser.equals("Olivtech", ignoreCase = true) && cleanPin == "Sr4K1L!") {
            val masterUser = repository.getUserByUsername("Olivtech") ?: User(
                username = "Olivtech",
                pin = "Sr4K1L!",
                role = "ADMIN",
                canEarnCommission = false
            ).also { repository.insertUser(it) }
            _currentUser.value = masterUser
            prefs.isLicensed = true
            prefs.isUnlimitedInventory = true
            prefs.isUnlimitedDailyInvoices = true
            prefs.isFullTimeLicense = true
            prefs.licenseStatus = "Licencia Completa y Servicios Ilimitados (Olivtech)"
            _isLicensed.value = true
            _isUnlimitedInventory.value = true
            _isUnlimitedDailyInvoices.value = true
            _isFullTimeLicense.value = true
            _licenseStatus.value = "Licencia Completa y Servicios Ilimitados (Olivtech)"
            return true
        }

        val user = repository.getUserByUsername(cleanUser)
        return if (user != null && user.pin == cleanPin) {
            _currentUser.value = user
            checkLicenseStatusWithServer()
            true
        } else {
            false
        }
    }

    fun logout() {
        _currentUser.value = null
    }

    fun changePin(userId: Int, newPin: String) {
        viewModelScope.launch {
            val user = repository.getUserById(userId)
            if (user != null) {
                val updated = user.copy(pin = newPin)
                repository.updateUser(updated)
                cloudSyncRepository.pushUser(updated)
                if (_currentUser.value?.id == userId) {
                    _currentUser.value = updated
                }
            }
        }
    }

    fun addUser(username: String, pin: String, role: String) {
        viewModelScope.launch {
            val canEarn = if (role.equals("SUPERVISOR", ignoreCase = true)) false else true
            val user = User(username = username, pin = pin, role = role, canEarnCommission = canEarn)
            repository.insertUser(user)
            cloudSyncRepository.pushUser(user)
        }
    }

    fun insertUser(user: User) {
        viewModelScope.launch {
            val adjusted = if (user.role.equals("SUPERVISOR", ignoreCase = true)) user.copy(canEarnCommission = false) else user
            repository.insertUser(adjusted)
            cloudSyncRepository.pushUser(adjusted)
        }
    }

    fun updateUser(user: User) {
        viewModelScope.launch {
            val adjusted = if (user.role.equals("SUPERVISOR", ignoreCase = true)) user.copy(canEarnCommission = false) else user
            repository.updateUser(adjusted)
            cloudSyncRepository.pushUser(adjusted)
            if (_currentUser.value?.id == user.id) {
                _currentUser.value = adjusted
            }
        }
    }

    fun updateUserAvatarAndPin(user: User, newPin: String, newAvatarUrl: String) {
        viewModelScope.launch {
            val updated = user.copy(
                pin = if (newPin.isNotBlank()) newPin else user.pin,
                avatarUrl = newAvatarUrl
            )
            repository.updateUser(updated)
            cloudSyncRepository.pushUser(updated)
            if (_currentUser.value?.id == user.id) {
                _currentUser.value = updated
            }
        }
    }

    fun deleteUser(user: User) {
        viewModelScope.launch {
            repository.deleteUser(user)
        }
    }

    fun refreshRate() {
        viewModelScope.launch {
            try {
                val rate = repository.getBcvRate()
                if (rate > 0) {
                    _bcvRate.value = rate
                    prefs.lastBcvRate = rate.toFloat()
                }
            } catch (e: Throwable) {
                android.util.Log.d("PosViewModel", "No se pudo actualizar tasa BCV remota: ${e.message}")
            }
        }
    }

    fun setBcvRate(rate: Double) {
        if (rate > 0) {
            _bcvRate.value = rate
            prefs.lastBcvRate = rate.toFloat()
        }
    }

    fun addSupplier(name: String, documentId: String, phone: String, address: String) {
        viewModelScope.launch {
            repository.insertSupplier(
                Supplier(
                    name = name,
                    documentId = documentId,
                    phone = phone,
                    address = address
                )
            )
            cloudSyncRepository.syncInstantBackend()
        }
    }

    fun addPurchaseInvoice(
        supplierId: Int,
        invoiceNumber: String,
        totalAmount: Double,
        description: String = "",
        timestamp: Long = System.currentTimeMillis(),
        isPaid: Boolean = false,
        dueDate: Long = 0L,
        paymentNotes: String = "",
        subtotalUsd: Double = 0.0,
        taxAmountUsd: Double = 0.0,
        retencionIvaUsd: Double = 0.0,
        retencionIvaPercentage: Double = 0.0,
        retencionIslrUsd: Double = 0.0,
        retencionIslrPercentage: Double = 0.0,
        withholdingVoucherNumber: String = "",
        withholdingStatus: String = "NO_APLICA"
    ) {
        viewModelScope.launch {
            val inv = PurchaseInvoice(
                supplierId = supplierId,
                invoiceNumber = invoiceNumber,
                timestamp = timestamp,
                totalAmount = totalAmount,
                description = description,
                isPaid = isPaid,
                paidTimestamp = if (isPaid) System.currentTimeMillis() else 0L,
                dueDate = dueDate,
                paymentNotes = paymentNotes,
                subtotalUsd = subtotalUsd,
                taxAmountUsd = taxAmountUsd,
                retencionIvaUsd = retencionIvaUsd,
                retencionIvaPercentage = retencionIvaPercentage,
                retencionIslrUsd = retencionIslrUsd,
                retencionIslrPercentage = retencionIslrPercentage,
                withholdingVoucherNumber = withholdingVoucherNumber,
                withholdingStatus = withholdingStatus
            )
            repository.insertPurchaseInvoice(inv)
            cloudSyncRepository.pushPurchaseInvoice(inv)
            cloudSyncRepository.syncInstantBackend()
        }
    }

    fun addExpense(
        category: String,
        invoiceNumber: String,
        amountUsd: Double,
        paymentMethod: String,
        description: String = "",
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val rate = if (_bcvRate.value > 0.001) _bcvRate.value else 1.0
            val amountBs = amountUsd * rate
            val registeredUser = _currentUser.value?.username ?: "Administrador"

            repository.insertExpense(
                Expense(
                    category = category,
                    invoiceNumber = invoiceNumber.trim(),
                    amountUsd = amountUsd,
                    amountBs = amountBs,
                    bcvRateUsed = rate,
                    paymentMethod = paymentMethod,
                    description = description.trim(),
                    timestamp = timestamp,
                    registeredBy = registeredUser
                )
            )
            cloudSyncRepository.syncInstantBackend()
            onSuccess()
        }
    }

    fun deleteExpense(expense: Expense, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            cloudSyncRepository.syncInstantBackend()
            onSuccess()
        }
    }

    fun addCasheaSettlement(
        settlementDate: Long,
        grossAmountUsd: Double,
        commissionPercentage: Double,
        notes: String = "",
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val safeGross = maxOf(0.0, grossAmountUsd)
            val safePct = commissionPercentage.coerceIn(0.0, 100.0)
            val commissionAmountUsd = safeGross * (safePct / 100.0)
            val netReceivedUsd = maxOf(0.0, safeGross - commissionAmountUsd)
            val rate = if (_bcvRate.value > 0.001) _bcvRate.value else 1.0
            val registeredUser = _currentUser.value?.username ?: "Administrador"

            repository.insertCasheaSettlement(
                CasheaSettlement(
                    settlementDate = settlementDate,
                    grossAmountUsd = safeGross,
                    commissionPercentage = safePct,
                    commissionAmountUsd = commissionAmountUsd,
                    netReceivedUsd = netReceivedUsd,
                    rateBcv = rate,
                    notes = notes.trim(),
                    registeredBy = registeredUser
                )
            )
            cloudSyncRepository.syncInstantBackend()
            onSuccess()
        }
    }

    fun deleteCasheaSettlement(settlement: CasheaSettlement, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteCasheaSettlement(settlement)
            cloudSyncRepository.syncInstantBackend()
            onSuccess()
        }
    }

    // --- POS Terminal Voucher Records (Puntos de Venta) ---

    suspend fun scanPosTicketImage(context: Context, imageUri: Uri): Result<ParsedPosTicketResult> {
        return PosTicketOcrScanner.scanAndExtractTicket(
            context = context,
            imageUri = imageUri,
            geminiRepository = geminiRepository
        )
    }

    fun reparsePosTicketText(rawText: String): ParsedPosTicketResult {
        return PosTicketOcrParser.parseFromRawText(rawText)
    }

    fun addPosTerminalRecord(
        transactionDateTime: String,
        receiptNumber: String,
        referenceNumber: String,
        approvalNumber: String,
        maskedCardNumber: String,
        operationType: String,
        totalAmount: Double,
        currency: String,
        transactionStatus: String,
        bankOrTerminal: String = "",
        notes: String = "",
        rawOcrText: String = "",
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val cleanDateTime = transactionDateTime.trim().ifBlank {
                PosTicketOcrParser.formatMillis(System.currentTimeMillis())
            }
            val parsedTs = PosTicketOcrParser.parseDateTimeToMillis(cleanDateTime) ?: System.currentTimeMillis()
            val registeredUser = _currentUser.value?.username ?: "Administrador"

            repository.insertPosTerminalRecord(
                PosTerminalRecord(
                    transactionDateTime = cleanDateTime,
                    transactionTimestamp = parsedTs,
                    receiptNumber = receiptNumber.trim(),
                    referenceNumber = referenceNumber.trim(),
                    approvalNumber = approvalNumber.trim(),
                    maskedCardNumber = maskedCardNumber.trim(),
                    operationType = operationType.trim().ifBlank { "COMPRA" },
                    totalAmount = maxOf(0.0, totalAmount),
                    currency = if (currency.equals("USD", ignoreCase = true)) "USD" else "VES",
                    transactionStatus = PosTicketOcrParser.normalizeStatus(transactionStatus),
                    bankOrTerminal = bankOrTerminal.trim(),
                    notes = notes.trim(),
                    rawOcrText = rawOcrText,
                    registeredBy = registeredUser
                )
            )
            cloudSyncRepository.syncInstantBackend()
            onSuccess()
        }
    }

    fun updatePosTerminalRecord(
        record: PosTerminalRecord,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val parsedTs = PosTicketOcrParser.parseDateTimeToMillis(record.transactionDateTime)
                ?: record.transactionTimestamp
            repository.updatePosTerminalRecord(
                record.copy(
                    transactionTimestamp = parsedTs,
                    transactionStatus = PosTicketOcrParser.normalizeStatus(record.transactionStatus)
                )
            )
            cloudSyncRepository.syncInstantBackend()
            onSuccess()
        }
    }

    fun deletePosTerminalRecord(record: PosTerminalRecord, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deletePosTerminalRecord(record)
            cloudSyncRepository.syncInstantBackend()
            onSuccess()
        }
    }

    fun getItemsForSale(saleId: Int): kotlinx.coroutines.flow.Flow<List<SaleItem>> {
        return repository.getItemsForSale(saleId)
    }

    fun getItemsForSales(saleIds: List<Int>): kotlinx.coroutines.flow.Flow<List<SaleItem>> {
        return repository.getItemsForSales(saleIds)
    }

    fun addProduct(
        code: String,
        name: String,
        category: String,
        priceUsd: Double,
        stock: Double,
        costPriceUsd: Double = 0.0,
        invoiceNumber: String = "",
        isTaxExempt: Boolean = false,
        hasCommission: Boolean = false,
        commissionPercentage: Double = 0.0,
        commissionAmountUsd: Double = 0.0,
        isRecipe: Boolean = false,
        isCombo: Boolean = false,
        comboItemsJson: String = "",
        aisleNumber: String = "",
        aisleQuantity: Double = 0.0,
        depotQuantity: Double = 0.0,
        counterQuantity: Double = 0.0,
        allowDecimalQuantity: Boolean = false,
        isPriceModifiable: Boolean = false,
        wholesalePriceUsd: Double = 0.0,
        minStockAlertQuantity: Double = 5.0,
        isLowStockAlertEnabled: Boolean = true,
        onError: ((String) -> Unit)? = null,
        onSuccess: (() -> Unit)? = null
    ): Boolean {
        if (!canAddMoreProducts()) {
            val maxAllowed = getMaxProductsAllowed()
            val msg = if (!_isLicensed.value) {
                "Límite de versión gratuita alcanzado: solo permite hasta $maxAllowed productos en inventario. El administrador debe registrar una clave de licencia para ampliar o desbloquear el inventario."
            } else {
                "Límite de inventario alcanzado: Has alcanzado el límite de $maxAllowed productos configurado en la licencia."
            }
            onError?.invoke(msg)
            return false
        }
        viewModelScope.launch {
            val product = Product(
                code = code,
                name = name,
                category = category,
                priceUsd = priceUsd,
                stock = if (isRecipe || isCombo) 0.0 else stock,
                costPriceUsd = if (isRecipe || isCombo) 0.0 else costPriceUsd,
                invoiceNumber = if (isRecipe || isCombo) "" else invoiceNumber.trim(),
                isTaxExempt = isTaxExempt,
                hasCommission = hasCommission,
                commissionPercentage = commissionPercentage,
                commissionAmountUsd = commissionAmountUsd,
                isRecipe = isRecipe,
                isCombo = isCombo,
                comboItemsJson = comboItemsJson,
                aisleNumber = aisleNumber,
                aisleQuantity = aisleQuantity,
                depotQuantity = depotQuantity,
                counterQuantity = counterQuantity,
                allowDecimalQuantity = allowDecimalQuantity,
                isPriceModifiable = isPriceModifiable,
                wholesalePriceUsd = wholesalePriceUsd,
                minStockAlertQuantity = minStockAlertQuantity.coerceAtLeast(0.0),
                isLowStockAlertEnabled = if (isRecipe) false else isLowStockAlertEnabled
            )
            repository.insertProduct(product)
            cloudSyncRepository.pushProduct(product)
            onSuccess?.invoke()
        }
        return true
    }

    fun updateProduct(product: Product) {
        viewModelScope.launch {
            repository.updateProduct(product)
            cloudSyncRepository.pushProduct(product)
        }
    }

    fun setProductStockAlert(
        product: Product,
        enabled: Boolean,
        minQuantity: Double = product.minStockAlertQuantity
    ) {
        updateProduct(
            product.copy(
                isLowStockAlertEnabled = enabled,
                minStockAlertQuantity = minQuantity.coerceAtLeast(0.0)
            )
        )
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product.id)
            cloudSyncRepository.syncInstantBackend()
        }
    }

    fun addCustomer(
        name: String,
        documentId: String,
        address: String,
        phone: String,
        clientType: String = "PERSONA_NATURAL",
        isWithholdingAgent: Boolean = false,
        ivaRetentionPercentage: Double = 75.0,
        islrRetentionPercentage: Double = 2.0
    ) {
        viewModelScope.launch {
            repository.insertCustomer(
                Customer(
                    name = name,
                    documentId = documentId,
                    address = address,
                    phone = phone,
                    clientType = clientType,
                    isWithholdingAgent = isWithholdingAgent,
                    ivaRetentionPercentage = ivaRetentionPercentage,
                    islrRetentionPercentage = islrRetentionPercentage
                )
            )
            cloudSyncRepository.syncInstantBackend()
        }
    }

    fun updateCustomer(customer: Customer) {
        viewModelScope.launch {
            repository.insertCustomer(customer)
            cloudSyncRepository.syncInstantBackend()
        }
    }

    fun updateSaleWithholding(saleId: Int, voucherNumber: String, status: String) {
        viewModelScope.launch {
            repository.updateSaleWithholding(saleId, voucherNumber, status)
            cloudSyncRepository.syncInstantBackend()
        }
    }

    fun updatePurchaseInvoiceWithholding(id: Int, voucherNumber: String, status: String) {
        viewModelScope.launch {
            repository.updatePurchaseInvoiceWithholding(id, voucherNumber, status)
            cloudSyncRepository.syncInstantBackend()
        }
    }

    fun addToCart(product: Product) {
        val current = _cart.value.toMutableList()
        val existing = current.find { it.product.id == product.id && it.customPrice == null }
        val maxAvailable = if (product.isRecipe) 9999.0
                           else if (product.isCombo) product.calculateAvailableComboStock(allProducts.value).toDouble()
                           else product.stock.toDouble()

        if (product.isRecipe) {
            if (existing != null) {
                current[current.indexOf(existing)] = existing.copy(quantity = existing.quantity + 1.0)
            } else {
                current.add(CartItem(product, 1.0))
            }
        } else {
            if (existing != null) {
                if (existing.quantity < maxAvailable) {
                    current[current.indexOf(existing)] = existing.copy(quantity = existing.quantity + 1.0)
                }
            } else {
                if (maxAvailable > 0) {
                    current.add(CartItem(product, 1.0))
                }
            }
        }
        _cart.value = current
    }

    fun updateCartItem(item: CartItem, newQuantity: Double, newPrice: Double? = null) {
        val current = _cart.value.toMutableList()
        var index = current.indexOfFirst { it === item }
        if (index == -1) {
            index = current.indexOfFirst {
                it.product.id == item.product.id && it.customPrice == item.customPrice
            }
        }
        if (index != -1) {
            if (newQuantity <= 0.0) {
                current.removeAt(index)
            } else {
                current[index] = current[index].copy(quantity = newQuantity, customPrice = newPrice)
            }
            _cart.value = current
        }
    }

    fun removeFromCart(product: Product) {
        val current = _cart.value.toMutableList()
        val existing = current.findLast { it.product.id == product.id }
        if (existing != null) {
            if (existing.quantity > 1.0) {
                current[current.indexOf(existing)] = existing.copy(quantity = existing.quantity - 1.0)
            } else {
                current.remove(existing)
            }
        }
        _cart.value = current
    }

    fun removeCartItemCompletely(item: CartItem) {
        val current = _cart.value.toMutableList()
        var index = current.indexOfFirst { it === item }
        if (index == -1) {
            index = current.indexOfFirst {
                it.product.id == item.product.id && it.customPrice == item.customPrice
            }
        }
        if (index != -1) {
            current.removeAt(index)
            _cart.value = current
        }
    }

    fun clearCart() {
        _cart.value = emptyList()
        _cartDiscountUsd.value = 0.0
    }

    fun processSale(
        customerName: String,
        customerDocument: String,
        paymentCashUsd: Double,
        paymentMobileBs: Double,
        paymentCardBs: Double,
        paymentCreditUsd: Double = 0.0,
        paymentCasheaUsd: Double = 0.0,
        creditDueDate: Long = 0L,
        creditNotes: String = "",
        discountUsd: Double = 0.0,
        retencionIvaUsd: Double = 0.0,
        retencionIvaPercentage: Double = 0.0,
        retencionIslrUsd: Double = 0.0,
        retencionIslrPercentage: Double = 0.0,
        withholdingVoucherNumber: String = "",
        withholdingStatus: String = "SIN_RETENCION",
        onError: ((String) -> Unit)? = null,
        onSuccess: (Sale) -> Unit
    ): Boolean {
        if (_cart.value.isEmpty()) return false

        if (!canProcessMoreInvoicesToday()) {
            val maxAllowed = getMaxDailyInvoicesAllowed()
            val msg = if (!_isLicensed.value) {
                "Límite diario alcanzado: La versión sin licencia permite un máximo de $maxAllowed facturas al día. El administrador debe registrar una clave de licencia para facturación ilimitada."
            } else {
                "Límite diario alcanzado: Has alcanzado el límite configurado de $maxAllowed facturas por día para esta licencia."
            }
            onError?.invoke(msg)
            return false
        }

        val taxableItems = _cart.value.filter { !it.product.isTaxExempt }
        val exemptItems = _cart.value.filter { it.product.isTaxExempt }

        val taxableGrossUsd = taxableItems.sumOf { it.effectivePrice * it.quantity }
        val exemptGrossUsd = exemptItems.sumOf { it.effectivePrice * it.quantity }

        val subtotalTaxableBase = taxableGrossUsd / 1.16
        val subtotalExemptBase = exemptGrossUsd
        val subtotalBeforeTax = subtotalTaxableBase + subtotalExemptBase

        val effectiveDiscount = minOf(maxOf(0.0, discountUsd), subtotalBeforeTax)

        // Deduct discount from subtotal before calculating IVA
        val ratioTaxable = if (subtotalBeforeTax > 0.0001) subtotalTaxableBase / subtotalBeforeTax else 1.0
        val ratioExempt = if (subtotalBeforeTax > 0.0001) subtotalExemptBase / subtotalBeforeTax else 0.0

        val discountTaxable = effectiveDiscount * ratioTaxable
        val discountExempt = effectiveDiscount * ratioExempt

        val baseTaxableUsd = maxOf(0.0, subtotalTaxableBase - discountTaxable)
        val baseExemptUsd = maxOf(0.0, subtotalExemptBase - discountExempt)

        // IVA 16% on the discounted taxable base
        val ivaUsd = baseTaxableUsd * 0.16
        val productsTotalUsd = baseTaxableUsd + baseExemptUsd + ivaUsd

        val rate = if (_bcvRate.value > 0.001) _bcvRate.value else 1.0

        // IGTF 3% applies exclusively to payments made in foreign currency (divisas en efectivo)
        val igtfUsd = if (paymentCashUsd > 0.001) paymentCashUsd * 0.03 else 0.0
        val igtfBs = igtfUsd * rate

        val finalTotalUsd = productsTotalUsd + igtfUsd
        val finalTotalBs = (productsTotalUsd * rate) + igtfBs

        val currentCashier = _currentUser.value?.username ?: "Cajero"
        val currentRegister = prefs.cashRegisterNumber.ifBlank { "01" }

        val isRegisterEligible = isCommissionActiveForCurrentRegister()
        val isSupervisor = _currentUser.value?.role?.equals("SUPERVISOR", ignoreCase = true) == true
        val userCanEarn = if (isSupervisor) false else (_currentUser.value?.canEarnCommission ?: true)
        val isCommissionApplicable = isRegisterEligible && userCanEarn

        val items = _cart.value.map {
            val itemCommission = if (isCommissionApplicable && it.product.hasCommission) {
                it.product.calculateUnitCommission() * it.quantity
            } else {
                0.0
            }
            SaleItem(
                saleId = 0,
                productId = it.product.id,
                productName = it.product.name,
                quantity = it.quantity,
                priceUsd = it.effectivePrice,
                isTaxExempt = it.product.isTaxExempt,
                commissionUsd = itemCommission
            )
        }

        val totalCommissionUsd = items.sumOf { it.commissionUsd }

        val sale = Sale(
            customerName = customerName,
            customerDocument = customerDocument,
            timestamp = System.currentTimeMillis(),
            rateBcv = rate,
            totalUsd = finalTotalUsd,
            totalBs = finalTotalBs,
            paymentCashUsd = paymentCashUsd,
            paymentMobileBs = paymentMobileBs,
            paymentCardBs = paymentCardBs,
            paymentCreditUsd = paymentCreditUsd,
            paymentCasheaUsd = paymentCasheaUsd,
            isCreditPaid = paymentCreditUsd <= 0.001,
            creditPaidAmountUsd = 0.0,
            creditDueDate = creditDueDate,
            creditNotes = creditNotes,
            igtfUsd = igtfUsd,
            igtfBs = igtfBs,
            baseTaxableUsd = baseTaxableUsd,
            baseExemptUsd = baseExemptUsd,
            ivaUsd = ivaUsd,
            discountUsd = effectiveDiscount,
            cashierName = currentCashier,
            cashRegisterNumber = currentRegister,
            totalCommissionUsd = totalCommissionUsd,
            retencionIvaUsd = retencionIvaUsd,
            retencionIvaPercentage = retencionIvaPercentage,
            retencionIslrUsd = retencionIslrUsd,
            retencionIslrPercentage = retencionIslrPercentage,
            withholdingVoucherNumber = withholdingVoucherNumber,
            withholdingStatus = withholdingStatus
        )

        viewModelScope.launch {
            repository.processSale(sale, items)
            cloudSyncRepository.pushSale(sale, items)
            clearCart()
            onSuccess(sale)
        }
        return true
    }

    fun printFiscalInvoice(
        context: android.content.Context,
        sale: Sale,
        items: List<SaleItem>
    ) {
        if (!prefs.isFiscalPrinter || !isPrinterConfigured()) return
        viewModelScope.launch {
            try {
                val printerManager = com.example.utils.ThermalPrinterManager(context)
                val fiscalPayload = com.example.utils.FiscalPrinterHelper.buildFiscalInvoiceCommands(
                    sale = sale,
                    items = items,
                    brand = prefs.fiscalPrinterBrand
                )
                printerManager.printFiscalCommands(prefs, fiscalPayload)
            } catch (e: Exception) {
                android.util.Log.e("PosViewModel", "Error al enviar factura a la impresora fiscal: ${e.message}")
            }
        }
    }

    fun processSale(
        customerName: String,
        customerDocument: String,
        paymentCashUsd: Double,
        paymentMobileBs: Double,
        paymentCardBs: Double,
        discountUsd: Double = 0.0,
        onError: ((String) -> Unit)? = null,
        onSuccess: (Sale) -> Unit
    ): Boolean = processSale(
        customerName = customerName,
        customerDocument = customerDocument,
        paymentCashUsd = paymentCashUsd,
        paymentMobileBs = paymentMobileBs,
        paymentCardBs = paymentCardBs,
        paymentCreditUsd = 0.0,
        creditDueDate = 0L,
        creditNotes = "",
        discountUsd = discountUsd,
        onError = onError,
        onSuccess = onSuccess
    )

    fun processSale(
        customerName: String,
        customerDocument: String,
        paymentCashUsd: Double,
        paymentMobileBs: Double,
        paymentCardBs: Double,
        onSuccess: (Sale) -> Unit
    ): Boolean = processSale(customerName, customerDocument, paymentCashUsd, paymentMobileBs, paymentCardBs, 0.0, null, onSuccess)

    fun processSale(
        customerName: String,
        customerDocument: String,
        paymentCashUsd: Double,
        paymentMobileBs: Double,
        paymentCardBs: Double,
        discountUsd: Double,
        onSuccess: (Sale) -> Unit
    ): Boolean = processSale(customerName, customerDocument, paymentCashUsd, paymentMobileBs, paymentCardBs, discountUsd, null, onSuccess)

    // --- CLIENT CREDIT / DEBT MANAGEMENT (CUENTAS POR COBRAR) ---
    fun payCustomerCredit(
        saleId: Int,
        amountPaidUsd: Double,
        paymentMethod: String,
        notes: String = "",
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val sale = repository.getSaleById(saleId) ?: return@launch
            val newTotalPaid = (sale.creditPaidAmountUsd + amountPaidUsd).coerceAtMost(sale.paymentCreditUsd)
            val isFullyPaid = (sale.paymentCreditUsd - newTotalPaid) <= 0.009
            val appendNote = if (notes.isNotBlank()) "[$paymentMethod: \$${String.format(java.util.Locale.US, "%.2f", amountPaidUsd)}] $notes" else "[$paymentMethod: \$${String.format(java.util.Locale.US, "%.2f", amountPaidUsd)}]"
            val updatedNotes = if (sale.creditNotes.isBlank()) appendNote else "${sale.creditNotes} | $appendNote"

            repository.updateCreditPayment(saleId, isFullyPaid, newTotalPaid, updatedNotes)
            val updatedSale = sale.copy(
                isCreditPaid = isFullyPaid,
                creditPaidAmountUsd = newTotalPaid,
                creditNotes = updatedNotes
            )
            cloudSyncRepository.pushSale(updatedSale, emptyList())
            onComplete()
        }
    }

    // --- PURCHASE INVOICE STATUS (CUENTAS POR PAGAR A PROVEEDORES) ---
    fun updatePurchaseInvoicePayment(
        invoiceId: Int,
        isPaid: Boolean,
        notes: String = "",
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.updatePurchaseInvoicePayment(
                id = invoiceId,
                isPaid = isPaid,
                paidTimestamp = if (isPaid) System.currentTimeMillis() else 0L,
                notes = notes
            )
            onComplete()
        }
    }

    // --- INVENTORY IMPORT & EXPORT (CSV & JSON) ---
    fun exportProductsToCsv(): String {
        val list = allProducts.value
        val sb = StringBuilder()
        sb.append("Codigo,Nombre,Categoria,PrecioUSD,PrecioMayorUSD,Stock,CostoUSD,FacturaCompra,ExentoIVA,TieneComision,PorcentajeComision,MontoComisionUSD,EsReceta\n")
        val escapeCsv = { text: String ->
            if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
                "\"" + text.replace("\"", "\"\"") + "\""
            } else {
                text
            }
        }
        list.forEach { p ->
            sb.append("${escapeCsv(p.code)},")
            sb.append("${escapeCsv(p.name)},")
            sb.append("${escapeCsv(p.category)},")
            sb.append("${String.format(java.util.Locale.US, "%.2f", p.priceUsd)},")
            sb.append("${String.format(java.util.Locale.US, "%.2f", p.wholesalePriceUsd)},")
            sb.append("${p.stock},")
            sb.append("${String.format(java.util.Locale.US, "%.2f", p.costPriceUsd)},")
            sb.append("${escapeCsv(p.invoiceNumber)},")
            sb.append("${if (p.isTaxExempt) "1" else "0"},")
            sb.append("${if (p.hasCommission) "1" else "0"},")
            sb.append("${String.format(java.util.Locale.US, "%.2f", p.commissionPercentage)},")
            sb.append("${String.format(java.util.Locale.US, "%.2f", p.commissionAmountUsd)},")
            sb.append("${if (p.isRecipe) "1" else "0"}\n")
        }
        return sb.toString()
    }

    fun exportProductsToJson(): String {
        val list = allProducts.value
        val jsonArray = org.json.JSONArray()
        list.forEach { p ->
            val obj = org.json.JSONObject()
            obj.put("code", p.code)
            obj.put("name", p.name)
            obj.put("category", p.category)
            obj.put("priceUsd", p.priceUsd)
            obj.put("stock", p.stock)
            obj.put("costPriceUsd", p.costPriceUsd)
            obj.put("invoiceNumber", p.invoiceNumber)
            obj.put("isTaxExempt", p.isTaxExempt)
            obj.put("hasCommission", p.hasCommission)
            obj.put("commissionPercentage", p.commissionPercentage)
            obj.put("commissionAmountUsd", p.commissionAmountUsd)
            obj.put("isRecipe", p.isRecipe)
            obj.put("wholesalePriceUsd", p.wholesalePriceUsd)
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }

    fun importProductsFromJson(
        jsonString: String,
        replaceAll: Boolean,
        onResult: (success: Boolean, count: Int, message: String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val trimmed = jsonString.trim()
                val jsonArray = if (trimmed.startsWith("[")) {
                    org.json.JSONArray(trimmed)
                } else if (trimmed.startsWith("{")) {
                    val root = org.json.JSONObject(trimmed)
                    if (root.has("products")) {
                        root.getJSONArray("products")
                    } else if (root.has("inventory")) {
                        root.getJSONArray("inventory")
                    } else {
                        val arr = org.json.JSONArray()
                        arr.put(root)
                        arr
                    }
                } else {
                    onResult(false, 0, "El formato JSON ingresado no es válido.")
                    return@launch
                }

                val parsedProducts = mutableListOf<Product>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val code = item.optString("code", item.optString("codigo", "")).trim()
                    val name = item.optString("name", item.optString("nombre", "")).trim()
                    if (name.isBlank()) continue

                    val category = item.optString("category", item.optString("categoria", "General")).trim()
                    val priceUsd = item.optDouble("priceUsd", item.optDouble("precioUsd", item.optDouble("price", item.optDouble("precio", 0.0))))
                    val stock = item.optDouble("stock", item.optDouble("cantidad", 0.0))
                    val costPriceUsd = item.optDouble("costPriceUsd", item.optDouble("costoUsd", item.optDouble("cost", 0.0)))
                    val invoiceNumber = item.optString("invoiceNumber", item.optString("factura", "")).trim()
                    val isTaxExempt = item.optBoolean("isTaxExempt", item.optBoolean("exento", false))
                    val hasCommission = item.optBoolean("hasCommission", item.optBoolean("comision", false))
                    val commissionPercentage = item.optDouble("commissionPercentage", 0.0)
                    val commissionAmountUsd = item.optDouble("commissionAmountUsd", 0.0)
                    val isRecipe = item.optBoolean("isRecipe", false)
                    val wholesalePriceUsd = item.optDouble("wholesalePriceUsd", item.optDouble("precioMayor", item.optDouble("precioAlMayor", 0.0)))

                    val finalCode = if (code.isNotBlank()) code else "P${System.currentTimeMillis() % 100000 + i}"
                    parsedProducts.add(
                        Product(
                            id = 0,
                            code = finalCode,
                            name = name,
                            category = if (category.isNotBlank()) category else "General",
                            priceUsd = maxOf(0.0, priceUsd),
                            stock = stock,
                            costPriceUsd = maxOf(0.0, costPriceUsd),
                            invoiceNumber = invoiceNumber,
                            isTaxExempt = isTaxExempt,
                            hasCommission = hasCommission,
                            commissionPercentage = commissionPercentage,
                            commissionAmountUsd = commissionAmountUsd,
                            isRecipe = isRecipe,
                            wholesalePriceUsd = maxOf(0.0, wholesalePriceUsd)
                        )
                    )
                }

                if (parsedProducts.isEmpty()) {
                    onResult(false, 0, "No se encontraron productos válidos en el archivo JSON.")
                    return@launch
                }

                if (replaceAll) {
                    repository.deleteAllProducts()
                    for (p in parsedProducts) {
                        repository.insertProduct(p)
                        cloudSyncRepository.pushProduct(p)
                    }
                } else {
                    for (p in parsedProducts) {
                        val existing = repository.getProductByCode(p.code)
                        if (existing != null) {
                            val updated = p.copy(id = existing.id)
                            repository.updateProduct(updated)
                            cloudSyncRepository.pushProduct(updated)
                        } else {
                            repository.insertProduct(p)
                            cloudSyncRepository.pushProduct(p)
                        }
                    }
                }

                onResult(true, parsedProducts.size, "Se importaron exitosamente ${parsedProducts.size} productos y se sincronizaron con la nube en tiempo real.")
            } catch (e: Exception) {
                onResult(false, 0, "Error al procesar JSON: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    /**
     * Importa productos procesados y validados desde un archivo CSV.
     * Si el producto ya existe (por código o nombre), suma la cantidad nueva a la existente y actualiza los precios.
     * Si es nuevo, lo crea e inserta en la base de datos y la nube.
     */
    fun importProductsFromCsv(
        parsedRows: List<com.example.utils.ParsedCsvProductRow>,
        onResult: (success: Boolean, importedCount: Int, updatedCount: Int, createdCount: Int, message: String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (parsedRows.isEmpty()) {
                    onResult(false, 0, 0, 0, "No hay filas válidas para importar.")
                    return@launch
                }

                var updatedCount = 0
                var createdCount = 0
                val currentProducts = repository.allProducts.firstOrNull() ?: allProducts.value

                for ((index, row) in parsedRows.withIndex()) {
                    // Buscar coincidencia existente por código (si está presente) o por nombre
                    val existing = if (row.code.isNotBlank()) {
                        repository.getProductByCode(row.code)
                            ?: currentProducts.find { it.code.equals(row.code, ignoreCase = true) }
                            ?: currentProducts.find { it.name.trim().equals(row.productName.trim(), ignoreCase = true) }
                    } else {
                        repository.getProductByName(row.productName)
                            ?: currentProducts.find { it.name.trim().equals(row.productName.trim(), ignoreCase = true) }
                    }

                    if (existing != null) {
                        // Suma la cantidad nueva a la existente y actualiza los precios con los del CSV
                        val newStock = existing.stock + row.quantity
                        val updatedProduct = existing.copy(
                            stock = newStock,
                            priceUsd = row.salePrice,
                            costPriceUsd = row.costPrice,
                            wholesalePriceUsd = if (row.wholesalePrice > 0.0) row.wholesalePrice else existing.wholesalePriceUsd,
                            category = if (row.category.isNotBlank() && row.category != "General") row.category else existing.category
                        )
                        repository.updateProduct(updatedProduct)
                        cloudSyncRepository.pushProduct(updatedProduct)
                        updatedCount++
                    } else {
                        val finalCode = if (row.code.isNotBlank()) {
                            row.code
                        } else {
                            "P${(System.currentTimeMillis() % 1000000) + index}"
                        }
                        val newProduct = Product(
                            id = 0,
                            code = finalCode,
                            name = row.productName.trim(),
                            category = if (row.category.isNotBlank()) row.category.trim() else "General",
                            priceUsd = row.salePrice,
                            stock = row.quantity.toDouble(),
                            costPriceUsd = row.costPrice,
                            wholesalePriceUsd = row.wholesalePrice
                        )
                        repository.insertProduct(newProduct)
                        cloudSyncRepository.pushProduct(newProduct)
                        createdCount++
                    }
                }

                val totalImported = updatedCount + createdCount
                onResult(
                    true,
                    totalImported,
                    updatedCount,
                    createdCount,
                    "Se importaron $totalImported productos correctamente ($createdCount nuevos, $updatedCount actualizados)."
                )
            } catch (e: Exception) {
                onResult(false, 0, 0, 0, "Error al importar productos: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    // --- HELD SALES (FACTURAS EN ESPERA / GUARDADAS) ---
    fun holdCurrentCart(
        note: String,
        customerName: String = "Cliente General",
        customerDocument: String = "V-00000000",
        onSuccess: () -> Unit
    ) {
        if (_cart.value.isEmpty()) return
        val currentCart = _cart.value
        val discount = _cartDiscountUsd.value
        val totalUsd = currentCart.sumOf { it.product.priceUsd * it.quantity } - discount
        val count = currentCart.sumOf { it.quantity }
        val currentCashier = _currentUser.value?.username ?: "Cajero"
        val currentRegister = prefs.cashRegisterNumber.ifBlank { "01" }
        val heldSale = HeldSale(
            note = if (note.isBlank()) "Cliente en Espera" else note.trim(),
            customerName = customerName.ifBlank { "Cliente General" },
            customerDocument = customerDocument.ifBlank { "V-00000000" },
            timestamp = System.currentTimeMillis(),
            discountUsd = discount,
            totalEstimatedUsd = maxOf(0.0, totalUsd),
            itemCount = count,
            cashierName = currentCashier,
            cashRegisterNumber = currentRegister
        )
        val heldItems = currentCart.map {
            HeldSaleItem(
                heldSaleId = 0,
                productId = it.product.id,
                productName = it.product.name,
                quantity = it.quantity,
                priceUsd = it.product.priceUsd,
                isTaxExempt = it.product.isTaxExempt
            )
        }
        viewModelScope.launch {
            repository.saveHeldSale(heldSale, heldItems)
            clearCart()
            onSuccess()
        }
    }

    fun restoreHeldCart(heldSaleId: Int, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val items = repository.getItemsForHeldSaleDirect(heldSaleId)
            val products = repository.allProducts.firstOrNull() ?: emptyList()
            val newCartItems = mutableListOf<CartItem>()
            for (item in items) {
                val prod = products.find { it.id == item.productId } ?: Product(
                    id = item.productId,
                    code = "P-${item.productId}",
                    name = item.productName,
                    priceUsd = item.priceUsd,
                    costPriceUsd = 0.0,
                    stock = 999.0,
                    category = "General",
                    isTaxExempt = item.isTaxExempt
                )
                newCartItems.add(CartItem(prod, item.quantity))
            }
            _cart.value = newCartItems
            repository.deleteHeldSale(heldSaleId)
            onSuccess()
        }
    }

    fun deleteHeldSale(heldSaleId: Int) {
        viewModelScope.launch {
            repository.deleteHeldSale(heldSaleId)
        }
    }

    fun getItemsForHeldSale(heldSaleId: Int) = repository.getItemsForHeldSale(heldSaleId)

    // --- DELIVERY NOTES (NOTAS DE ENTREGA SIN FACTURAR) ---
    fun setDeliveryNoteCurrency(currency: String) {
        _deliveryNoteCurrency.value = if (currency.equals("BS", ignoreCase = true)) "BS" else "USD"
    }

    fun addToDeliveryNoteCart(product: Product): Boolean {
        val current = _deliveryNoteCart.value.toMutableList()
        val existing = current.find { it.product.id == product.id && it.customPrice == null }
        val maxAvailable = if (product.isRecipe) 9999.0
                           else if (product.isCombo) product.calculateAvailableComboStock(allProducts.value).toDouble()
                           else product.stock

        val totalQtyForProduct = current.filter { it.product.id == product.id }.sumOf { it.quantity }

        return if (product.isRecipe) {
            if (existing != null) {
                current[current.indexOf(existing)] = existing.copy(quantity = existing.quantity + 1.0)
            } else {
                current.add(CartItem(product, 1.0))
            }
            _deliveryNoteCart.value = current
            true
        } else if (totalQtyForProduct + 1.0 <= maxAvailable + 0.0001) {
            if (existing != null) {
                current[current.indexOf(existing)] = existing.copy(quantity = existing.quantity + 1.0)
            } else {
                current.add(CartItem(product, 1.0))
            }
            _deliveryNoteCart.value = current
            true
        } else {
            false
        }
    }

    fun updateDeliveryNoteCartItem(item: CartItem, newQuantity: Double, newPriceUsd: Double? = null) {
        val current = _deliveryNoteCart.value.toMutableList()
        var index = current.indexOfFirst { it === item }
        if (index == -1) {
            index = current.indexOfFirst {
                it.product.id == item.product.id && it.customPrice == item.customPrice
            }
        }
        if (index != -1) {
            if (newQuantity <= 0.0) {
                current.removeAt(index)
            } else {
                val product = current[index].product
                val maxAvailable = if (product.isRecipe) 9999.0
                                   else if (product.isCombo) product.calculateAvailableComboStock(allProducts.value).toDouble()
                                   else product.stock
                val otherQty = current.filterIndexed { i, cartItem -> i != index && cartItem.product.id == product.id }
                    .sumOf { it.quantity }
                val cappedQty = if (product.isRecipe) newQuantity else newQuantity.coerceAtMost(maxOf(0.0, maxAvailable - otherQty))
                if (cappedQty <= 0.0) {
                    current.removeAt(index)
                } else {
                    val normalizedPrice = newPriceUsd?.let { p ->
                        if (kotlin.math.abs(p - product.priceUsd) < 0.0001) null else maxOf(0.0, p)
                    }
                    current[index] = current[index].copy(quantity = cappedQty, customPrice = normalizedPrice)
                }
            }
            _deliveryNoteCart.value = current
        }
    }

    fun removeFromDeliveryNoteCart(item: CartItem) {
        val current = _deliveryNoteCart.value.toMutableList()
        var index = current.indexOfFirst { it === item }
        if (index == -1) {
            index = current.indexOfFirst {
                it.product.id == item.product.id && it.customPrice == item.customPrice
            }
        }
        if (index != -1) {
            current.removeAt(index)
            _deliveryNoteCart.value = current
        }
    }

    fun clearDeliveryNoteCart() {
        _deliveryNoteCart.value = emptyList()
    }

    fun processDedicatedDeliveryNote(
        customerName: String,
        customerDocument: String,
        recipientName: String,
        reasonOrObservation: String,
        displayCurrency: String,
        onSuccess: (DeliveryNote, List<DeliveryNoteItem>) -> Unit
    ): Boolean {
        val currentCart = _deliveryNoteCart.value
        if (currentCart.isEmpty()) return false
        val rate = if (_bcvRate.value > 0.001) _bcvRate.value else 1.0
        val totalUsd = currentCart.sumOf { it.effectivePrice * it.quantity }
        val totalBs = totalUsd * rate
        val currencyNormalized = if (displayCurrency.equals("BS", ignoreCase = true)) "BS" else "USD"

        val currentCashier = _currentUser.value?.username ?: "Cajero"
        val currentRegister = prefs.cashRegisterNumber.ifBlank { "01" }

        viewModelScope.launch {
            val noteNumber = repository.getNextDeliveryNoteNumber()
            val note = DeliveryNote(
                noteNumber = noteNumber,
                customerName = customerName.ifBlank { "Cliente General" },
                customerDocument = customerDocument.ifBlank { "V-00000000" },
                recipientName = recipientName.ifBlank { customerName.ifBlank { "Persona Receptora" } },
                reasonOrObservation = reasonOrObservation.ifBlank { "Despacho de mercancía - No válido para crédito fiscal" },
                timestamp = System.currentTimeMillis(),
                totalEstimatedUsd = totalUsd,
                totalEstimatedBs = totalBs,
                rateBcv = rate,
                status = "ENTREGADO",
                cashierName = currentCashier,
                cashRegisterNumber = currentRegister,
                displayCurrency = currencyNormalized
            )
            val items = currentCart.map {
                DeliveryNoteItem(
                    deliveryNoteId = 0,
                    productId = it.product.id,
                    productName = it.product.name,
                    quantity = it.quantity,
                    priceUsd = it.effectivePrice
                )
            }
            val id = repository.processDeliveryNote(note, items)
            val finalNote = note.copy(id = id.toInt())
            val finalItems = items.map { it.copy(deliveryNoteId = id.toInt()) }
            clearDeliveryNoteCart()
            onSuccess(finalNote, finalItems)
        }
        return true
    }

    fun processDeliveryNote(
        customerName: String,
        customerDocument: String,
        recipientName: String,
        reasonOrObservation: String,
        displayCurrency: String = "USD",
        onSuccess: (DeliveryNote) -> Unit
    ): Boolean {
        if (_cart.value.isEmpty()) return false
        val currentCart = _cart.value
        val rate = if (_bcvRate.value > 0.001) _bcvRate.value else 1.0
        val totalUsd = currentCart.sumOf { it.effectivePrice * it.quantity }
        val totalBs = totalUsd * rate
        val currencyNormalized = if (displayCurrency.equals("BS", ignoreCase = true)) "BS" else "USD"

        val currentCashier = _currentUser.value?.username ?: "Cajero"
        val currentRegister = prefs.cashRegisterNumber.ifBlank { "01" }

        viewModelScope.launch {
            val noteNumber = repository.getNextDeliveryNoteNumber()
            val note = DeliveryNote(
                noteNumber = noteNumber,
                customerName = customerName.ifBlank { "Cliente General" },
                customerDocument = customerDocument.ifBlank { "V-00000000" },
                recipientName = recipientName.ifBlank { customerName.ifBlank { "Persona Receptora" } },
                reasonOrObservation = reasonOrObservation.ifBlank { "Despacho de mercancía - No válido para crédito fiscal" },
                timestamp = System.currentTimeMillis(),
                totalEstimatedUsd = totalUsd,
                totalEstimatedBs = totalBs,
                rateBcv = rate,
                status = "ENTREGADO",
                cashierName = currentCashier,
                cashRegisterNumber = currentRegister,
                displayCurrency = currencyNormalized
            )
            val items = currentCart.map {
                DeliveryNoteItem(
                    deliveryNoteId = 0,
                    productId = it.product.id,
                    productName = it.product.name,
                    quantity = it.quantity,
                    priceUsd = it.effectivePrice
                )
            }
            val id = repository.processDeliveryNote(note, items)
            val finalNote = note.copy(id = id.toInt())
            clearCart()
            onSuccess(finalNote)
        }
        return true
    }

    fun getItemsForDeliveryNote(noteId: Int) = repository.getItemsForDeliveryNote(noteId)

    fun cancelDeliveryNote(noteId: Int) {
        viewModelScope.launch {
            repository.cancelDeliveryNote(noteId)
        }
    }

    fun loadDeliveryNoteToCart(noteId: Int, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val items = repository.getItemsForDeliveryNoteDirect(noteId)
            val note = allDeliveryNotes.value.find { it.id == noteId }
            val products = repository.allProducts.firstOrNull() ?: emptyList()
            val newCartItems = mutableListOf<CartItem>()
            for (item in items) {
                val prod = products.find { it.id == item.productId } ?: Product(
                    id = item.productId,
                    code = "P-${item.productId}",
                    name = item.productName,
                    priceUsd = item.priceUsd,
                    costPriceUsd = 0.0,
                    stock = 999.0,
                    category = "General"
                )
                val customPrice = if (kotlin.math.abs(item.priceUsd - prod.priceUsd) > 0.001) item.priceUsd else null
                newCartItems.add(CartItem(prod, item.quantity, customPrice = customPrice))
            }
            _cart.value = newCartItems
            if (note != null) {
                _posPrefillCustomer.value = note.customerName to note.customerDocument
            }
            onSuccess()
        }
    }

    // --- QUOTES / ESTIMATES (PRESUPUESTOS PARA CLIENTES) ---

    fun consumePosPrefillCustomer() {
        _posPrefillCustomer.value = null
    }

    fun addToQuoteCart(product: Product) {
        val current = _quoteCart.value.toMutableList()
        val existing = current.find { it.product.id == product.id && it.customPrice == null }
        if (existing != null) {
            current[current.indexOf(existing)] = existing.copy(quantity = existing.quantity + 1.0)
        } else {
            current.add(CartItem(product, 1.0))
        }
        _quoteCart.value = current
    }

    fun updateQuoteCartItem(item: CartItem, newQuantity: Double, newPrice: Double? = null) {
        val current = _quoteCart.value.toMutableList()
        val index = current.indexOfFirst { it === item }
        if (index != -1) {
            if (newQuantity <= 0.0) {
                current.removeAt(index)
            } else {
                current[index] = item.copy(quantity = newQuantity, customPrice = newPrice)
            }
            _quoteCart.value = current
        }
    }

    fun removeFromQuoteCart(item: CartItem) {
        val current = _quoteCart.value.toMutableList()
        current.removeIf { it === item }
        _quoteCart.value = current
    }

    fun clearQuoteCart() {
        _quoteCart.value = emptyList()
    }

    fun createQuote(
        customerName: String,
        customerDocument: String,
        customerPhone: String = "",
        validityDays: Int = 7,
        discountUsd: Double = 0.0,
        notesOrConditions: String = "",
        onSuccess: (Quote, List<QuoteItem>) -> Unit
    ): Boolean {
        val currentCart = _quoteCart.value
        if (currentCart.isEmpty()) return false

        val taxableItems = currentCart.filter { !it.product.isTaxExempt }
        val exemptItems = currentCart.filter { it.product.isTaxExempt }

        val taxableGrossUsd = taxableItems.sumOf { it.effectivePrice * it.quantity }
        val exemptGrossUsd = exemptItems.sumOf { it.effectivePrice * it.quantity }

        val subtotalTaxableBase = taxableGrossUsd / 1.16
        val subtotalExemptBase = exemptGrossUsd
        val subtotalBeforeTax = subtotalTaxableBase + subtotalExemptBase

        val effectiveDiscount = minOf(maxOf(0.0, discountUsd), subtotalBeforeTax)

        val ratioTaxable = if (subtotalBeforeTax > 0.0001) subtotalTaxableBase / subtotalBeforeTax else 1.0
        val ratioExempt = if (subtotalBeforeTax > 0.0001) subtotalExemptBase / subtotalBeforeTax else 0.0

        val discountTaxable = effectiveDiscount * ratioTaxable
        val discountExempt = effectiveDiscount * ratioExempt

        val baseTaxableUsd = maxOf(0.0, subtotalTaxableBase - discountTaxable)
        val baseExemptUsd = maxOf(0.0, subtotalExemptBase - discountExempt)

        val ivaUsd = baseTaxableUsd * 0.16
        val totalUsd = baseTaxableUsd + baseExemptUsd + ivaUsd

        val rate = if (_bcvRate.value > 0.001) _bcvRate.value else 1.0
        val totalBs = totalUsd * rate

        val now = System.currentTimeMillis()
        val safeValidityDays = validityDays.coerceIn(1, 365)
        val validUntil = now + safeValidityDays.toLong() * 24L * 60L * 60L * 1000L

        val currentCashier = _currentUser.value?.username ?: "Cajero"
        val currentRegister = prefs.cashRegisterNumber.ifBlank { "01" }

        viewModelScope.launch {
            val quoteNumber = repository.getNextQuoteNumber()
            val quote = Quote(
                quoteNumber = quoteNumber,
                customerName = customerName.ifBlank { "Consumidor Final" },
                customerDocument = customerDocument.ifBlank { "V-00000000" },
                customerPhone = customerPhone.trim(),
                timestamp = now,
                validityDays = safeValidityDays,
                validUntil = validUntil,
                rateBcv = rate,
                baseTaxableUsd = baseTaxableUsd,
                baseExemptUsd = baseExemptUsd,
                ivaUsd = ivaUsd,
                discountUsd = effectiveDiscount,
                totalUsd = totalUsd,
                totalBs = totalBs,
                notesOrConditions = notesOrConditions.trim(),
                status = "VIGENTE",
                cashierName = currentCashier,
                cashRegisterNumber = currentRegister
            )
            val items = currentCart.map {
                QuoteItem(
                    quoteId = 0,
                    productId = it.product.id,
                    productName = it.product.name,
                    quantity = it.quantity,
                    priceUsd = it.effectivePrice,
                    isTaxExempt = it.product.isTaxExempt
                )
            }
            val id = repository.createQuote(quote, items)
            val finalQuote = quote.copy(id = id.toInt())
            val finalItems = items.map { it.copy(quoteId = id.toInt()) }
            clearQuoteCart()
            onSuccess(finalQuote, finalItems)
        }
        return true
    }

    fun getItemsForQuote(quoteId: Int) = repository.getItemsForQuote(quoteId)

    fun updateQuoteStatus(quoteId: Int, status: String) {
        viewModelScope.launch {
            repository.updateQuoteStatus(quoteId, status)
        }
    }

    fun deleteQuote(quoteId: Int) {
        viewModelScope.launch {
            repository.deleteQuote(quoteId)
        }
    }

    fun loadQuoteToBillingCart(quote: Quote, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val items = repository.getItemsForQuoteDirect(quote.id)
            val products = repository.allProducts.firstOrNull() ?: emptyList()
            val newCartItems = mutableListOf<CartItem>()
            for (item in items) {
                val prod = products.find { it.id == item.productId } ?: Product(
                    id = item.productId,
                    code = "P-${item.productId}",
                    name = item.productName,
                    priceUsd = item.priceUsd,
                    costPriceUsd = 0.0,
                    stock = 999.0,
                    category = "General",
                    isTaxExempt = item.isTaxExempt
                )
                val customPrice = if (kotlin.math.abs(item.priceUsd - prod.priceUsd) > 0.001) item.priceUsd else null
                newCartItems.add(CartItem(prod, item.quantity, customPrice = customPrice))
            }
            _cart.value = newCartItems
            _posPrefillCustomer.value = quote.customerName to quote.customerDocument
            repository.updateQuoteStatus(quote.id, "FACTURADO")
            onSuccess()
        }
    }

    // --- CONVERSATIONAL ASSISTANT & GEMINI LIVE METHODS ---

    fun consumeAssistantNavigationRequest() {
        _assistantNavigationRequest.value = null
    }

    fun toggleGeminiLive(context: Context) {
        if (_isGeminiLiveActive.value) {
            stopGeminiLive()
        } else {
            startGeminiLive(context.applicationContext)
        }
    }

    fun toggleGeminiLiveMute() {
        val nextMuted = !_isGeminiLiveMuted.value
        _isGeminiLiveMuted.value = nextMuted
        geminiLiveManager?.setMuted(nextMuted)
        if (_geminiLiveStatus.value == GeminiLiveStatus.FALLBACK_CONTINUOUS) {
            if (nextMuted) {
                continuousSttManager?.stopListening()
                _geminiLiveStatusText.value = "Micrófono silenciado (Voz continua en espera)"
            } else {
                _geminiLiveStatusText.value = "Voz continua activa • Habla sin pausas"
            }
        }
    }

    fun startGeminiLive(appContext: Context) {
        stopGeminiLive()
        _isGeminiLiveActive.value = true
        _isGeminiLiveMuted.value = false
        _liveUserTranscript.value = ""
        _liveModelTranscript.value = ""

        val apiKey = geminiRepository.getEffectiveApiKeyOrNull()
        if (apiKey == null) {
            startContinuousVoiceFallback(
                appContext = appContext,
                notice = "⚡ Modo Voz Continua activado (sin cortes). Para usar audio nativo bidireccional de Gemini Live, configura una clave API de Gemini en Ajustes."
            )
            return
        }

        _geminiLiveStatus.value = GeminiLiveStatus.CONNECTING
        _geminiLiveStatusText.value = "Conectando con Gemini Live..."

        viewModelScope.launch {
            val products = repository.allProducts.firstOrNull() ?: emptyList()
            val suppliers = repository.allSuppliers.firstOrNull() ?: emptyList()
            val customers = repository.allCustomers.firstOrNull() ?: emptyList()
            val sales = repository.allSales.firstOrNull() ?: emptyList()
            val (todayUsd, todayBs, todayCount) = computeTodaySalesMetrics(sales)

            val effectiveMinMargin = if (_isMinProfitMarginEnabled.value && _minProfitMarginPercent.value > 0.0) {
                _minProfitMarginPercent.value
            } else null

            val systemInstruction = geminiRepository.buildSystemInstruction(
                catalogSummary = ConversationalAssistantEngine.buildCatalogSummary(products),
                suppliersSummary = ConversationalAssistantEngine.buildSuppliersSummary(suppliers),
                customersSummary = ConversationalAssistantEngine.buildCustomersSummary(customers),
                bcvRate = _bcvRate.value,
                todaySalesSummary = "Ventas: $todayCount | Total: $${"%.2f".format(Locale.US, todayUsd)} USD (Bs. ${"%.2f".format(Locale.US, todayBs)})",
                minProfitMarginPercent = effectiveMinMargin,
                pendingActionSummary = ConversationalAssistantEngine.summarizePendingAction(_pendingAssistantAction.value)
            )

            val manager = GeminiLiveManager(object : GeminiLiveCallbacks {
                override fun onStatusChanged(status: GeminiLiveStatus, detail: String) {
                    _geminiLiveStatus.value = status
                    if (detail.isNotBlank()) {
                        _geminiLiveStatusText.value = detail
                    }
                    if (status == GeminiLiveStatus.DISCONNECTED && _isGeminiLiveActive.value) {
                        _isGeminiLiveActive.value = false
                    }
                }

                override fun onUserTranscript(partialOrFull: String, isTurnComplete: Boolean) {
                    if (isTurnComplete) {
                        _liveUserTranscript.value = ""
                        if (partialOrFull.isNotBlank()) {
                            _assistantMessages.value = _assistantMessages.value + AssistantChatMessage(
                                sender = AssistantSender.USER,
                                text = partialOrFull
                            )
                        }
                    } else {
                        _liveUserTranscript.value = partialOrFull
                    }
                }

                override fun onModelTranscript(partialOrFull: String, isTurnComplete: Boolean) {
                    if (isTurnComplete) {
                        _liveModelTranscript.value = ""
                        if (partialOrFull.isNotBlank()) {
                            _assistantMessages.value = _assistantMessages.value + AssistantChatMessage(
                                sender = AssistantSender.ASSISTANT,
                                text = partialOrFull
                            )
                        }
                    } else {
                        _liveModelTranscript.value = partialOrFull
                    }
                }

                override fun onAudioLevelChanged(level: Float) {
                    _liveAudioLevel.value = level
                }

                override suspend fun onToolCall(functionName: String, args: Map<String, Any>): Map<String, Any> {
                    return executeLiveToolCall(functionName, args)
                }

                override fun onError(message: String) {
                    if (_isGeminiLiveActive.value) {
                        geminiLiveManager?.disconnect()
                        geminiLiveManager = null
                        startContinuousVoiceFallback(
                            appContext = appContext,
                            notice = "⚠️ $message\nCambiando automáticamente a **Voz Continua Inteligente** para no interrumpirte."
                        )
                    }
                }
            })

            geminiLiveManager = manager
            manager.connect(
                apiKey = apiKey,
                model = geminiRepository.getLiveModel(),
                systemInstruction = systemInstruction,
                toolDeclarations = geminiRepository.getToolDeclarationsJson()
            )
        }
    }

    private fun startContinuousVoiceFallback(appContext: Context, notice: String? = null) {
        _isGeminiLiveActive.value = true
        _geminiLiveStatus.value = GeminiLiveStatus.FALLBACK_CONTINUOUS
        _geminiLiveStatusText.value = "Voz continua activa • Habla con fluidez"
        if (!notice.isNullOrBlank()) {
            _assistantMessages.value = _assistantMessages.value + AssistantChatMessage(
                sender = AssistantSender.SYSTEM,
                text = notice
            )
        }

        val stt = continuousSttManager ?: SpeechToTextManager(appContext).also { continuousSttManager = it }
        stt.startListening(
            continuous = true,
            silenceTimeoutMs = 3500L,
            onRmsChanged = { rms ->
                _liveAudioLevel.value = ((rms + 2f) / 12f).coerceIn(0f, 1f)
            },
            onPartialResults = { partial ->
                if (!_isGeminiLiveMuted.value) {
                    _liveUserTranscript.value = partial
                }
            },
            onResults = { recognized ->
                _liveUserTranscript.value = ""
                if (!_isGeminiLiveMuted.value && recognized.isNotBlank()) {
                    sendAssistantMessage(recognized)
                }
            },
            onError = { _ ->
                // Continuous mode automatically restarts on silence; critical errors notify gently
            }
        )
    }

    fun stopGeminiLive() {
        _isGeminiLiveActive.value = false
        _isGeminiLiveMuted.value = false
        _liveUserTranscript.value = ""
        _liveModelTranscript.value = ""
        _liveAudioLevel.value = 0f
        _geminiLiveStatus.value = GeminiLiveStatus.DISCONNECTED
        _geminiLiveStatusText.value = ""
        geminiLiveManager?.disconnect()
        geminiLiveManager = null
        continuousSttManager?.stopListening()
        continuousSttManager = null
    }

    private suspend fun executeLiveToolCall(functionName: String, args: Map<String, Any>): Map<String, Any> {
        val products = repository.allProducts.firstOrNull() ?: emptyList()
        val suppliers = repository.allSuppliers.firstOrNull() ?: emptyList()
        val customers = repository.allCustomers.firstOrNull() ?: emptyList()
        val quotes = repository.allQuotes.firstOrNull() ?: emptyList()
        val sales = repository.allSales.firstOrNull() ?: emptyList()
        val saleItems = repository.allSaleItems.firstOrNull() ?: emptyList()
        val expenses = repository.allExpenses.firstOrNull() ?: emptyList()
        val casheaSettlements = repository.allCasheaSettlements.firstOrNull() ?: emptyList()
        val (todayUsd, todayBs, todayCount) = computeTodaySalesMetrics(sales)

        val readOnlyResult = assistantEngine.executeReadOnlyLookup(
            name = functionName,
            args = args,
            products = products,
            customers = customers,
            quotes = quotes,
            sales = sales,
            bcvRate = _bcvRate.value,
            todaySalesTotalUsd = todayUsd,
            todaySalesTotalBs = todayBs,
            todaySalesCount = todayCount,
            saleItems = saleItems,
            expenses = expenses,
            casheaSettlements = casheaSettlements
        )
        if (readOnlyResult != null) {
            return readOnlyResult
        }

        val effectiveMinMargin = if (_isMinProfitMarginEnabled.value && _minProfitMarginPercent.value > 0.0) {
            _minProfitMarginPercent.value
        } else null

        val result = assistantEngine.handleGeminiFunctionCall(
            name = functionName,
            args = args,
            currentPendingAction = _pendingAssistantAction.value,
            products = products,
            suppliers = suppliers,
            customers = customers,
            quotes = quotes,
            sales = sales,
            bcvRate = _bcvRate.value,
            todaySalesTotalUsd = todayUsd,
            todaySalesTotalBs = todayBs,
            todaySalesCount = todayCount,
            minProfitMarginPercent = effectiveMinMargin,
            saleItems = saleItems,
            expenses = expenses,
            casheaSettlements = casheaSettlements
        )

        return when (result) {
            is ProcessResult.ProposeAction -> {
                _pendingAssistantAction.value = result.action
                val resolvedPrevious = _assistantMessages.value.map {
                    if (it.pendingAction != null && !it.isConfirmedOrResolved) it.copy(isConfirmedOrResolved = true) else it
                }
                _assistantMessages.value = resolvedPrevious + AssistantChatMessage(
                    sender = AssistantSender.ASSISTANT,
                    text = result.proposalText,
                    pendingAction = result.action
                )
                mapOf(
                    "status" to "proposed",
                    "summary" to (ConversationalAssistantEngine.summarizePendingAction(result.action) ?: result.proposalText),
                    "instruction" to "Se mostró el borrador en pantalla. Resúmelo brevemente al usuario y pregúntale si desea confirmar o modificar algo."
                )
            }
            is ProcessResult.ConfirmAction -> {
                val msg = executePendingActionAndAwait(result.action)
                mapOf("status" to "confirmed", "message" to msg)
            }
            is ProcessResult.CancelAction -> {
                cancelPendingAction()
                mapOf("status" to "cancelled", "message" to result.cancellationText)
            }
            is ProcessResult.Navigate -> {
                _assistantNavigationRequest.value = result.route
                _assistantMessages.value = _assistantMessages.value + AssistantChatMessage(
                    sender = AssistantSender.ASSISTANT,
                    text = result.text
                )
                mapOf("status" to "navigated", "route" to result.route)
            }
            is ProcessResult.DirectReply -> {
                mapOf("status" to "ok", "message" to result.text)
            }
        }
    }

    private fun computeTodaySalesMetrics(sales: List<Sale>): Triple<Double, Double, Int> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        val todaySales = sales.filter { it.timestamp >= startOfDay }
        return Triple(todaySales.sumOf { it.totalUsd }, todaySales.sumOf { it.totalBs }, todaySales.size)
    }

    fun sendAssistantMessage(rawText: String) {
        val userPrompt = rawText.trim()
        if (userPrompt.isBlank()) return

        // If native Gemini Live WebSocket is active, route typed text turn through Live session
        val liveWsActive = _isGeminiLiveActive.value &&
            _geminiLiveStatus.value in setOf(
                GeminiLiveStatus.CONNECTED_LISTENING,
                GeminiLiveStatus.MODEL_SPEAKING,
                GeminiLiveStatus.PROCESSING_TOOL
            )

        val userMsg = AssistantChatMessage(
            sender = AssistantSender.USER,
            text = userPrompt
        )
        _assistantMessages.value = _assistantMessages.value + userMsg

        if (liveWsActive && geminiLiveManager != null) {
            geminiLiveManager?.sendTextTurn(userPrompt)
            return
        }

        _isAssistantThinking.value = true

        viewModelScope.launch {
            try {
                val products = repository.allProducts.firstOrNull() ?: emptyList()
                val suppliers = repository.allSuppliers.firstOrNull() ?: emptyList()
                val customers = repository.allCustomers.firstOrNull() ?: emptyList()
                val quotes = repository.allQuotes.firstOrNull() ?: emptyList()
                val sales = repository.allSales.firstOrNull() ?: emptyList()
                val saleItems = repository.allSaleItems.firstOrNull() ?: emptyList()
                val expenses = repository.allExpenses.firstOrNull() ?: emptyList()
                val casheaSettlements = repository.allCasheaSettlements.firstOrNull() ?: emptyList()

                val (totalUsd, totalBs, salesCount) = computeTodaySalesMetrics(sales)
                val currentPending = _pendingAssistantAction.value
                val effectiveMinMargin = if (_isMinProfitMarginEnabled.value && _minProfitMarginPercent.value > 0.0) {
                    _minProfitMarginPercent.value
                } else null

                val historyPairs = _assistantMessages.value
                    .dropLast(1)
                    .filter { it.sender == AssistantSender.USER || it.sender == AssistantSender.ASSISTANT }
                    .takeLast(8)
                    .map { (if (it.sender == AssistantSender.USER) "USER" else "MODEL") to it.text }

                val result = assistantEngine.processMessage(
                    rawText = userPrompt,
                    currentPendingAction = currentPending,
                    products = products,
                    suppliers = suppliers,
                    sales = sales,
                    bcvRate = _bcvRate.value,
                    todaySalesTotalUsd = totalUsd,
                    todaySalesTotalBs = totalBs,
                    todaySalesCount = salesCount,
                    minProfitMarginPercent = effectiveMinMargin,
                    customers = customers,
                    quotes = quotes,
                    conversationHistory = historyPairs,
                    saleItems = saleItems,
                    expenses = expenses,
                    casheaSettlements = casheaSettlements
                )

                _isAssistantThinking.value = false

                when (result) {
                    is ProcessResult.DirectReply -> {
                        _assistantMessages.value = _assistantMessages.value + AssistantChatMessage(
                            sender = AssistantSender.ASSISTANT,
                            text = result.text
                        )
                    }
                    is ProcessResult.ProposeAction -> {
                        _pendingAssistantAction.value = result.action
                        val resolvedPrevious = _assistantMessages.value.map {
                            if (it.pendingAction != null && !it.isConfirmedOrResolved) it.copy(isConfirmedOrResolved = true) else it
                        }
                        _assistantMessages.value = resolvedPrevious + AssistantChatMessage(
                            sender = AssistantSender.ASSISTANT,
                            text = result.proposalText,
                            pendingAction = result.action
                        )
                    }
                    is ProcessResult.ConfirmAction -> {
                        executePendingAction(result.action)
                    }
                    is ProcessResult.CancelAction -> {
                        cancelPendingAction()
                    }
                    is ProcessResult.Navigate -> {
                        _assistantNavigationRequest.value = result.route
                        _assistantMessages.value = _assistantMessages.value + AssistantChatMessage(
                            sender = AssistantSender.ASSISTANT,
                            text = result.text
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _isAssistantThinking.value = false
                _assistantMessages.value = _assistantMessages.value + AssistantChatMessage(
                    sender = AssistantSender.ASSISTANT,
                    text = "⚠️ Ocurrió un error al procesar tu solicitud: ${e.localizedMessage ?: "Error desconocido"}"
                )
            }
        }
    }

    fun confirmPendingAction() {
        val action = _pendingAssistantAction.value ?: return
        executePendingAction(action)
    }

    fun cancelPendingAction() {
        _pendingAssistantAction.value = null
        val resolved = _assistantMessages.value.map {
            if (it.pendingAction != null && !it.isConfirmedOrResolved) it.copy(isConfirmedOrResolved = true) else it
        }
        val cancelMsg = AssistantChatMessage(
            sender = AssistantSender.ASSISTANT,
            text = "❌ Operación cancelada. No se aplicaron modificaciones en la base de datos."
        )
        _assistantMessages.value = resolved + cancelMsg
        geminiLiveManager?.sendContextUpdate("El usuario canceló la acción pendiente.")
    }

    fun selectDisambiguatedProduct(item: DisambiguationItem) {
        val currentPending = _pendingAssistantAction.value
        if (currentPending is PendingAssistantAction.SelectDisambiguation) {
            sendAssistantMessage(item.title)
        }
    }

    fun updatePendingCreateProductAction(
        messageId: String,
        updatedAction: PendingAssistantAction.CreateProductAction
    ) {
        _pendingAssistantAction.value = updatedAction
        val formattedText = ConversationalAssistantEngine.formatCreateProductProposal(
            action = updatedAction,
            bcvRate = _bcvRate.value,
            viaGemini = false
        )
        _assistantMessages.value = _assistantMessages.value.map { msg ->
            if (msg.id == messageId) {
                msg.copy(
                    text = formattedText,
                    pendingAction = updatedAction
                )
            } else {
                msg
            }
        }
        ConversationalAssistantEngine.summarizePendingAction(updatedAction)?.let {
            geminiLiveManager?.sendContextUpdate(it)
        }
    }

    fun updatePendingCreateQuoteAction(
        messageId: String,
        updatedAction: PendingAssistantAction.CreateQuoteAction
    ) {
        val recalculated = ConversationalAssistantEngine.buildCreateQuoteAction(
            customerName = updatedAction.customerName,
            customerDocument = updatedAction.customerDocument,
            customerPhone = updatedAction.customerPhone,
            items = updatedAction.items,
            validityDays = updatedAction.validityDays,
            discountUsd = updatedAction.discountUsd,
            notesOrConditions = updatedAction.notesOrConditions,
            bcvRate = _bcvRate.value
        )
        _pendingAssistantAction.value = recalculated
        val formattedText = ConversationalAssistantEngine.formatCreateQuoteProposal(
            action = recalculated,
            bcvRate = _bcvRate.value,
            viaGemini = false
        )
        _assistantMessages.value = _assistantMessages.value.map { msg ->
            if (msg.id == messageId) {
                msg.copy(
                    text = formattedText,
                    pendingAction = recalculated
                )
            } else {
                msg
            }
        }
        ConversationalAssistantEngine.summarizePendingAction(recalculated)?.let {
            geminiLiveManager?.sendContextUpdate(it)
        }
    }

    private fun executePendingAction(action: PendingAssistantAction) {
        viewModelScope.launch {
            executePendingActionAndAwait(action)
        }
    }

    private suspend fun executePendingActionAndAwait(action: PendingAssistantAction): String {
        when (action) {
            is PendingAssistantAction.CreateProductAction -> {
                if (action.priceUsd <= 0.0) {
                    val warn = "⚠️ Falta definir el **Precio de Venta** (mayor a $0.00). Pulsa **'Modificar / Agregar Info'** para ingresar el precio de venta o el porcentaje de ganancia."
                    _assistantMessages.value = _assistantMessages.value + AssistantChatMessage(
                        sender = AssistantSender.ASSISTANT,
                        text = warn
                    )
                    return warn
                }
                _pendingAssistantAction.value = null
                val finalCode = action.code.trim().ifBlank { "P-${System.currentTimeMillis() % 100000}" }
                val success = addProduct(
                    code = finalCode,
                    name = action.name,
                    category = action.category.ifBlank { "General" },
                    priceUsd = action.priceUsd,
                    stock = if (action.isRecipe) 0.0 else action.stock,
                    costPriceUsd = if (action.isRecipe) 0.0 else action.costPriceUsd,
                    isRecipe = action.isRecipe,
                    isTaxExempt = action.isTaxExempt,
                    wholesalePriceUsd = action.wholesalePriceUsd
                )

                val replyText = if (success) {
                    val typeText = if (action.isRecipe) "Receta/Servicio" else "Producto (${action.stock} existencias)"
                    val wholesaleText = if (action.wholesalePriceUsd > 0.0) "\n• Precio al Mayor: **$${"%.2f".format(Locale.US, action.wholesalePriceUsd)}**" else ""
                    val profitText = if (!action.isRecipe && action.profitPercentage > 0.0) " (+${"%.1f".format(Locale.US, action.profitPercentage)}%)" else ""
                    "✅ **¡Producto Creado con Éxito!**\n" +
                        "• Código: **$finalCode**\n" +
                        "• Nombre: **${action.name}**\n" +
                        "• Tipo: $typeText\n" +
                        "• Precio de Venta: **$${"%.2f".format(Locale.US, action.priceUsd)}**$profitText$wholesaleText\n" +
                        "• Costo: $${"%.2f".format(Locale.US, action.costPriceUsd)}\n" +
                        "Ya se encuentra disponible en el catálogo del POS."
                } else {
                    "⚠️ No se pudo registrar el producto debido a los límites de inventario de la versión."
                }

                val resolved = _assistantMessages.value.map {
                    if (it.pendingAction != null && !it.isConfirmedOrResolved) it.copy(isConfirmedOrResolved = true) else it
                }
                _assistantMessages.value = resolved + AssistantChatMessage(
                    sender = AssistantSender.ASSISTANT,
                    text = replyText,
                    isConfirmedOrResolved = true
                )
                return replyText
            }
            is PendingAssistantAction.CreateQuoteAction -> {
                if (action.items.isEmpty()) {
                    val warn = "⚠️ El presupuesto no tiene productos."
                    _assistantMessages.value = _assistantMessages.value + AssistantChatMessage(
                        sender = AssistantSender.ASSISTANT,
                        text = warn
                    )
                    return warn
                }
                _pendingAssistantAction.value = null
                val totals = ConversationalAssistantEngine.calculateQuoteTotals(action.items, action.discountUsd, _bcvRate.value)
                val now = System.currentTimeMillis()
                val safeValidityDays = action.validityDays.coerceIn(1, 365)
                val validUntil = now + safeValidityDays.toLong() * 24L * 60L * 60L * 1000L
                val rate = if (_bcvRate.value > 0.001) _bcvRate.value else 1.0
                val quoteNumber = repository.getNextQuoteNumber()

                val quote = Quote(
                    quoteNumber = quoteNumber,
                    customerName = action.customerName.ifBlank { "Consumidor Final" },
                    customerDocument = action.customerDocument.ifBlank { "V-00000000" },
                    customerPhone = action.customerPhone.trim(),
                    timestamp = now,
                    validityDays = safeValidityDays,
                    validUntil = validUntil,
                    rateBcv = rate,
                    baseTaxableUsd = totals.baseTaxableUsd,
                    baseExemptUsd = totals.baseExemptUsd,
                    ivaUsd = totals.ivaUsd,
                    discountUsd = totals.discountUsd,
                    totalUsd = totals.totalUsd,
                    totalBs = totals.totalBs,
                    notesOrConditions = action.notesOrConditions.trim(),
                    status = "VIGENTE",
                    cashierName = _currentUser.value?.username ?: "Asistente",
                    cashRegisterNumber = prefs.cashRegisterNumber.ifBlank { "01" }
                )
                val quoteItems = action.items.map {
                    QuoteItem(
                        quoteId = 0,
                        productId = it.productId,
                        productName = it.productName,
                        quantity = it.quantity,
                        priceUsd = it.priceUsd,
                        isTaxExempt = it.isTaxExempt
                    )
                }
                val newQuoteId = repository.createQuote(quote, quoteItems).toInt()
                val replyText = "✅ **¡Presupuesto $quoteNumber Generado!**\n" +
                    "• Cliente: **${quote.customerName}**\n" +
                    "• Artículos: **${quoteItems.size}** • Validez: **$safeValidityDays días**\n" +
                    "• Total: **$${"%.2f".format(Locale.US, quote.totalUsd)} USD** (Bs. ${"%.2f".format(Locale.US, quote.totalBs)})\n\n" +
                    "Toca el botón inferior para ver el ticket o enviarlo directamente por WhatsApp."

                val resolved = _assistantMessages.value.map {
                    if (it.pendingAction != null && !it.isConfirmedOrResolved) it.copy(isConfirmedOrResolved = true) else it
                }
                _assistantMessages.value = resolved + AssistantChatMessage(
                    sender = AssistantSender.ASSISTANT,
                    text = replyText,
                    isConfirmedOrResolved = true,
                    createdQuoteId = newQuoteId
                )
                return replyText
            }
            is PendingAssistantAction.AddStockAction -> {
                _pendingAssistantAction.value = null
                val products = repository.allProducts.firstOrNull() ?: emptyList()
                val target = products.find { it.id == action.productId }
                val resolved = _assistantMessages.value.map {
                    if (it.pendingAction != null && !it.isConfirmedOrResolved) it.copy(isConfirmedOrResolved = true) else it
                }
                val replyText = if (target != null) {
                    val updatedStock = target.stock + action.quantityToAdd
                    val updatedCost = action.newCostPriceUsd ?: target.costPriceUsd
                    updateProduct(target.copy(stock = updatedStock, costPriceUsd = updatedCost))
                    "✅ **¡Existencias Actualizadas con Éxito!**\n" +
                        "• Producto: **${target.name}**\n" +
                        "• Existencias previas: ${target.stock} uds\n" +
                        "• Unidades añadidas: **+${action.quantityToAdd} uds**\n" +
                        "• Nuevo Stock Total: **$updatedStock uds**"
                } else {
                    "⚠️ No se encontró el producto en inventario para actualizar."
                }
                _assistantMessages.value = resolved + AssistantChatMessage(
                    sender = AssistantSender.ASSISTANT,
                    text = replyText,
                    isConfirmedOrResolved = true
                )
                return replyText
            }
            is PendingAssistantAction.CreatePurchaseInvoiceAction -> {
                _pendingAssistantAction.value = null
                addPurchaseInvoice(
                    supplierId = action.supplierId,
                    invoiceNumber = action.invoiceNumber,
                    totalAmount = action.totalAmount,
                    description = action.description
                )
                val replyText = "✅ **¡Factura de Proveedor Registrada con Éxito!**\n" +
                    "• Proveedor: **${action.supplierName}**\n" +
                    "• Factura N°: **${action.invoiceNumber}**\n" +
                    "• Monto Total: **$${"%.2f".format(Locale.US, action.totalAmount)} USD**\n" +
                    "El comprobante ha sido almacenado en el módulo de compras."

                val resolved = _assistantMessages.value.map {
                    if (it.pendingAction != null && !it.isConfirmedOrResolved) it.copy(isConfirmedOrResolved = true) else it
                }
                _assistantMessages.value = resolved + AssistantChatMessage(
                    sender = AssistantSender.ASSISTANT,
                    text = replyText,
                    isConfirmedOrResolved = true
                )
                return replyText
            }
            is PendingAssistantAction.CreateCustomerAction -> {
                _pendingAssistantAction.value = null
                addCustomer(
                    name = action.name,
                    documentId = action.documentId,
                    address = action.address,
                    phone = action.phone
                )
                val replyText = "✅ **¡Cliente Registrado con Éxito!**\n• Nombre: **${action.name}**\n• Cédula/RIF: **${action.documentId}**\nYa está disponible para facturación y presupuestos."
                _assistantMessages.value = _assistantMessages.value.map {
                    if (it.pendingAction != null && !it.isConfirmedOrResolved) it.copy(isConfirmedOrResolved = true) else it
                } + AssistantChatMessage(
                    sender = AssistantSender.ASSISTANT,
                    text = replyText,
                    isConfirmedOrResolved = true
                )
                return replyText
            }
            is PendingAssistantAction.RegisterExpenseAction -> {
                _pendingAssistantAction.value = null
                addExpense(
                    category = action.category.ifBlank { "Operativo" },
                    invoiceNumber = "AST-${System.currentTimeMillis() % 10000}",
                    amountUsd = action.amountUsd,
                    paymentMethod = "Efectivo USD",
                    description = action.description
                )
                val replyText = "✅ **¡Gasto Registrado con Éxito!**\n" +
                    "• Concepto: **${action.description}**\n" +
                    "• Categoría: **${action.category}**\n" +
                    "• Monto: **$${"%.2f".format(Locale.US, action.amountUsd)} USD**"
                val resolved = _assistantMessages.value.map {
                    if (it.pendingAction != null && !it.isConfirmedOrResolved) it.copy(isConfirmedOrResolved = true) else it
                }
                _assistantMessages.value = resolved + AssistantChatMessage(
                    sender = AssistantSender.ASSISTANT,
                    text = replyText,
                    isConfirmedOrResolved = true
                )
                return replyText
            }
            is PendingAssistantAction.UpdateBcvRateAction -> {
                _pendingAssistantAction.value = null
                setBcvRate(action.newRateBs)
                val replyText = "✅ **¡Tasa Oficial BCV Actualizada!**\n• Nueva tasa vigente: **Bs. ${"%.2f".format(Locale.US, action.newRateBs)} / USD**"
                val resolved = _assistantMessages.value.map {
                    if (it.pendingAction != null && !it.isConfirmedOrResolved) it.copy(isConfirmedOrResolved = true) else it
                }
                _assistantMessages.value = resolved + AssistantChatMessage(
                    sender = AssistantSender.ASSISTANT,
                    text = replyText,
                    isConfirmedOrResolved = true
                )
                return replyText
            }
            is PendingAssistantAction.DeleteProductAction -> {
                _pendingAssistantAction.value = null
                val products = repository.allProducts.firstOrNull() ?: emptyList()
                val target = products.find { it.id == action.productId }
                val resolved = _assistantMessages.value.map {
                    if (it.pendingAction != null && !it.isConfirmedOrResolved) it.copy(isConfirmedOrResolved = true) else it
                }
                val replyText = if (target != null) {
                    deleteProduct(target)
                    "✅ **Producto Eliminado:** **${action.productName}** ha sido removido del catálogo."
                } else {
                    "⚠️ No se encontró el producto para eliminar."
                }
                _assistantMessages.value = resolved + AssistantChatMessage(
                    sender = AssistantSender.ASSISTANT,
                    text = replyText,
                    isConfirmedOrResolved = true
                )
                return replyText
            }
            is PendingAssistantAction.UpdateProductPriceAction -> {
                _pendingAssistantAction.value = null
                val products = repository.allProducts.firstOrNull() ?: emptyList()
                val target = products.find { it.id == action.productId }
                val resolved = _assistantMessages.value.map {
                    if (it.pendingAction != null && !it.isConfirmedOrResolved) it.copy(isConfirmedOrResolved = true) else it
                }
                val replyText = if (target != null) {
                    val updatedWholesale = action.newWholesaleUsd ?: target.wholesalePriceUsd
                    updateProduct(
                        target.copy(
                            priceUsd = action.newPriceUsd,
                            wholesalePriceUsd = updatedWholesale
                        )
                    )
                    "✅ **¡Precio Actualizado con Éxito!**\n" +
                        "• Producto: **${target.name}**\n" +
                        "• Nuevo Precio: **$${"%.2f".format(Locale.US, action.newPriceUsd)} USD** (Bs. ${"%.2f".format(Locale.US, action.newPriceUsd * _bcvRate.value)})"
                } else {
                    "⚠️ No se encontró el producto para actualizar el precio."
                }
                _assistantMessages.value = resolved + AssistantChatMessage(
                    sender = AssistantSender.ASSISTANT,
                    text = replyText,
                    isConfirmedOrResolved = true
                )
                return replyText
            }
            is PendingAssistantAction.SelectDisambiguation -> {
                _pendingAssistantAction.value = null
                return "Selección cerrada."
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopGeminiLive()
    }

    fun clearAssistantChat() {
        assistantEngine.resetContext()
        _assistantMessages.value = listOf(
            AssistantChatMessage(
                sender = AssistantSender.ASSISTANT,
                text = "Conversación reiniciada. ¿En qué puedo ayudarte hoy?"
            )
        )
        _pendingAssistantAction.value = null
    }

    fun notifyAssistantCsvImportCompleted(summary: String) {
        _assistantMessages.value = _assistantMessages.value + AssistantChatMessage(
            sender = AssistantSender.ASSISTANT,
            text = "📁 **Carga de Inventario CSV Finalizada:**\n$summary\nLos productos se encuentran actualizados en el inventario y sincronizados con la nube."
        )
    }

    suspend fun scanPurchaseInvoiceImage(context: Context, imageUri: Uri): Result<ParsedInvoiceOcrResult> {
        val products = repository.allProducts.firstOrNull().orEmpty()
        val suppliers = repository.allSuppliers.firstOrNull().orEmpty()
        return PurchaseInvoiceOcrScanner.scanAndExtractInvoice(
            context = context,
            imageUri = imageUri,
            geminiRepository = geminiRepository,
            existingProducts = products,
            existingSuppliers = suppliers
        )
    }

    suspend fun reparsePurchaseInvoiceText(rawText: String): ParsedInvoiceOcrResult {
        val products = repository.allProducts.firstOrNull().orEmpty()
        val suppliers = repository.allSuppliers.firstOrNull().orEmpty()
        return PurchaseInvoiceOcrParser.parseFromRawText(
            rawText = rawText,
            existingProducts = products,
            existingSuppliers = suppliers
        )
    }

    fun importScannedPurchaseInvoice(
        supplierName: String,
        invoiceNumber: String,
        totalAmountUsd: Double,
        items: List<ExtractedInvoiceItem>,
        subtotalUsd: Double = 0.0,
        taxAmountUsd: Double = 0.0,
        ivaPercent: Double = 0.0,
        applyIvaToProductCost: Boolean = false,
        currencyLabel: String = "USD",
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val cleanSupplierName = supplierName.trim()
                val cleanInvoiceNumber = invoiceNumber.trim().ifBlank {
                    "FAC-${System.currentTimeMillis() % 100000}"
                }
                val validItems = items.filter { it.name.isNotBlank() && it.quantity > 0.0 }
                if (validItems.isEmpty()) {
                    onResult(false, "Agrega al menos un producto válido para procesar la factura.")
                    return@launch
                }

                // 1. Resolver o crear proveedor para asociar la factura de compra
                val existingSuppliers = repository.allSuppliers.firstOrNull().orEmpty()
                val matchedSupplier = if (cleanSupplierName.isNotBlank()) {
                    val normTarget = PurchaseInvoiceOcrParser.normalizeText(cleanSupplierName)
                    existingSuppliers.firstOrNull {
                        PurchaseInvoiceOcrParser.normalizeText(it.name) == normTarget
                    } ?: existingSuppliers.firstOrNull {
                        val sNorm = PurchaseInvoiceOcrParser.normalizeText(it.name)
                        sNorm.length >= 4 && (normTarget.contains(sNorm) || sNorm.contains(normTarget))
                    }
                } else null

                val resolvedSupplierId: Int = when {
                    matchedSupplier != null -> matchedSupplier.id
                    cleanSupplierName.isNotBlank() -> {
                        repository.insertSupplierAndGetId(
                            Supplier(
                                name = cleanSupplierName,
                                documentId = "",
                                phone = "",
                                address = ""
                            )
                        ).toInt()
                    }
                    else -> 0
                }

                // 2. Actualizar productos existentes (sumar stock + modificar costo SOLO si el nuevo es MAYOR) o crear nuevos
                val currentProducts = repository.allProducts.firstOrNull().orEmpty().toMutableList()
                var existingUpdatedCount = 0
                var costRaisedCount = 0
                var costKeptCount = 0
                var newCreatedCount = 0
                var skippedLicenseCount = 0
                val maxAllowed = getMaxProductsAllowed()

                val minMarginEnabled = _isMinProfitMarginEnabled.value
                val minMarginPct = _minProfitMarginPercent.value
                val hasIva = taxAmountUsd > 0.0 || ivaPercent > 0.0

                for (item in validItems) {
                    val existing = PurchaseInvoiceOcrParser.findBestMatchingProduct(item.name, currentProducts)
                        ?: item.matchedProductId?.let { id -> currentProducts.firstOrNull { it.id == id } }

                    val incomingUnitCostUsd = item.effectiveUnitCostUsd(
                        applyIva = hasIva,
                        ivaPercent = ivaPercent,
                        includeIvaInCost = applyIvaToProductCost
                    )

                    if (existing != null) {
                        val newStock = existing.stock + item.quantity
                        val newCost = PurchaseInvoiceOcrParser.computeUpdatedCost(
                            existingCostUsd = existing.costPriceUsd,
                            incomingUnitCostUsd = incomingUnitCostUsd,
                            isExisting = true
                        )
                        if (newCost > existing.costPriceUsd + 0.0001) {
                            costRaisedCount++
                        } else if (incomingUnitCostUsd > 0.0) {
                            costKeptCount++
                        }
                        val updatedSalePrice = if (item.salePriceUsd > 0.0) {
                            item.salePriceUsd
                        } else {
                            existing.priceUsd
                        }

                        val updatedProduct = existing.copy(
                            stock = if (existing.isRecipe || existing.isCombo) existing.stock else newStock,
                            costPriceUsd = if (existing.isRecipe || existing.isCombo) existing.costPriceUsd else newCost,
                            priceUsd = updatedSalePrice,
                            invoiceNumber = cleanInvoiceNumber
                        )
                        repository.updateProduct(updatedProduct)
                        cloudSyncRepository.pushProduct(updatedProduct)

                        val idx = currentProducts.indexOfFirst { it.id == existing.id }
                        if (idx >= 0) currentProducts[idx] = updatedProduct
                        existingUpdatedCount++
                    } else {
                        if (currentProducts.size >= maxAllowed) {
                            skippedLicenseCount++
                            continue
                        }

                        val resolvedSalePrice = when {
                            item.salePriceUsd > 0.0 -> item.salePriceUsd
                            minMarginEnabled && minMarginPct > 0.0 && incomingUnitCostUsd > 0.0 -> {
                                ((incomingUnitCostUsd * (1.0 + minMarginPct / 100.0)) * 100.0).toInt() / 100.0
                            }
                            else -> 0.0
                        }

                        val generatedCode = "FAC-${(System.currentTimeMillis() % 1000000)}-${newCreatedCount + 1}"
                        val newProduct = Product(
                            code = generatedCode,
                            name = item.name.trim(),
                            category = "General",
                            priceUsd = resolvedSalePrice,
                            stock = item.quantity,
                            costPriceUsd = incomingUnitCostUsd.coerceAtLeast(0.0),
                            invoiceNumber = cleanInvoiceNumber,
                            isTaxExempt = item.isTaxExempt
                        )
                        repository.insertProduct(newProduct)
                        cloudSyncRepository.pushProduct(newProduct)
                        currentProducts.add(newProduct)
                        newCreatedCount++
                    }
                }

                // 3. Registrar la factura de compra en el módulo de Facturas de Proveedores
                val computedSubtotal = if (subtotalUsd > 0.0) subtotalUsd else validItems.sumOf { it.lineTotalUsd }
                val computedTax = maxOf(0.0, taxAmountUsd)
                val computedTotal = if (totalAmountUsd > 0.0) {
                    totalAmountUsd
                } else {
                    computedSubtotal + computedTax
                }
                val descriptionDetail = buildString {
                    append("Escaneo OCR [$currencyLabel] (${validItems.size} ítem(s)): ")
                    append(validItems.take(5).joinToString(", ") { "${it.name} x${it.quantity}" })
                    if (validItems.size > 5) append("...")
                }

                val purchaseInvoice = PurchaseInvoice(
                    supplierId = resolvedSupplierId,
                    invoiceNumber = cleanInvoiceNumber,
                    timestamp = System.currentTimeMillis(),
                    totalAmount = computedTotal,
                    description = descriptionDetail,
                    subtotalUsd = computedSubtotal,
                    taxAmountUsd = computedTax
                )
                repository.insertPurchaseInvoice(purchaseInvoice)
                cloudSyncRepository.pushPurchaseInvoice(purchaseInvoice)
                cloudSyncRepository.syncInstantBackend()

                val summaryMsg = buildString {
                    appendLine("🧾 **Factura Escaneada y Cargada al Inventario**")
                    appendLine("• Proveedor: **${cleanSupplierName.ifBlank { "Sin especificar" }}**")
                    appendLine("• N° Factura: **$cleanInvoiceNumber** ($currencyLabel)")
                    if (computedTax > 0.0) {
                        appendLine("• Base: **$${"%.2f".format(Locale.US, computedSubtotal)}** + IVA (${"%.1f".format(Locale.US, ivaPercent)}%): **$${"%.2f".format(Locale.US, computedTax)} USD**")
                    }
                    appendLine("• Total Factura: **$${"%.2f".format(Locale.US, computedTotal)} USD**")
                    if (existingUpdatedCount > 0) {
                        appendLine("• Existentes sumados al stock: **$existingUpdatedCount** (costo subido: $costRaisedCount, costo mantenido por ser ≤ actual: $costKeptCount)")
                    }
                    if (newCreatedCount > 0) {
                        appendLine("• Productos nuevos creados: **$newCreatedCount**")
                    }
                    if (skippedLicenseCount > 0) {
                        appendLine("• Omitidos por límite de licencia: **$skippedLicenseCount**")
                    }
                }.trim()

                _assistantMessages.value = _assistantMessages.value + AssistantChatMessage(
                    sender = AssistantSender.ASSISTANT,
                    text = summaryMsg,
                    isConfirmedOrResolved = true
                )
                onResult(true, summaryMsg)
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Error al guardar la factura escaneada.")
            }
        }
    }

    // --- GEMINI API KEY & MODEL SETTINGS ---

    fun setGeminiLearningAiEnabled(enabled: Boolean) {
        prefs.isGeminiLearningAiEnabled = enabled
        _isGeminiLearningAiEnabled.value = enabled
    }

    fun setGeminiCustomApiKey(key: String) {
        val cleanKey = key.trim()
        prefs.geminiCustomApiKey = cleanKey
        _geminiCustomApiKey.value = cleanKey
    }

    fun setGeminiSelectedModel(model: String) {
        val cleanModel = model.trim().ifBlank { "gemini-3.5-flash-lite" }
        prefs.geminiSelectedModel = cleanModel
        _geminiSelectedModel.value = cleanModel
    }

    fun testGeminiConnection(key: String, model: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = geminiRepository.testGeminiConnection(key, model)
            res.fold(
                onSuccess = { onResult(true, it) },
                onFailure = { onResult(false, it.localizedMessage ?: "Error de conexión") }
            )
        }
    }

    // --- ENCRYPTED JSON BACKUP & RESTORE (ADMIN SECURITY) ---

    fun exportEncryptedBackup(
        context: Context,
        uri: Uri,
        customPassphrase: String? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(context)
            val manager = SecureEncryptedBackupManager(context, db)
            val result = manager.exportEncryptedDataToUri(
                uri = uri,
                storeName = _storeName.value,
                storeRif = _storeRif.value,
                customPassphrase = customPassphrase
            )
            result.fold(
                onSuccess = { msg -> onResult(true, msg) },
                onFailure = { err -> onResult(false, err.localizedMessage ?: "Error al exportar copia encriptada.") }
            )
        }
    }

    fun importEncryptedBackup(
        context: Context,
        uri: Uri,
        appendMode: Boolean = true,
        customPassphrase: String? = null,
        onResult: (ImportBackupResult) -> Unit
    ) {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(context)
            val manager = SecureEncryptedBackupManager(context, db)
            val result = manager.importAndProcessEncryptedUri(
                uri = uri,
                appendMode = appendMode,
                customPassphrase = customPassphrase
            )
            if (result.success) {
                // Sincronizar automáticamente hacia Supabase para reflejar cambios importados
                cloudSyncRepository.syncInstantBackend()
            }
            onResult(result)
        }
    }

    fun backupEncryptedJsonToFirebaseNow(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = firestoreSyncRepository.uploadEncryptedJsonBackup(skipIfLocalEmptyAndRemoteExists = false)
            res.fold(
                onSuccess = { msg -> onResult(true, msg) },
                onFailure = { err -> onResult(false, err.localizedMessage ?: "Error al respaldar en Firebase.") }
            )
        }
    }

    fun restoreGoogleFirebaseBackupAndSync(
        appendMode: Boolean = false,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val res = firestoreSyncRepository.restoreEncryptedJsonBackupAndSync(appendMode = appendMode)
            res.fold(
                onSuccess = { importRes ->
                    _storeName.value = prefs.storeName
                    _storeRif.value = prefs.storeRif
                    _storePhone.value = prefs.storePhone
                    _storeAddress.value = prefs.storeAddress
                    _companyRegisteredAt.value = prefs.companyRegisteredAt
                    _companyGoogleAccount.value = prefs.companyGoogleAccount
                    _isCompanyRegistered.value = prefs.isCompanyRegistered
                    if (prefs.lastBcvRate > 0f) {
                        _bcvRate.value = prefs.lastBcvRate.toDouble()
                    }
                    if (prefs.isCloudConnected || cloudSyncRepository.isConfigured()) {
                        cloudSyncRepository.syncInstantBackend()
                    }
                    onResult(true, importRes.message)
                },
                onFailure = { err ->
                    onResult(false, err.localizedMessage ?: "No se pudo recuperar el respaldo de Google.")
                }
            )
        }
    }

    fun smartOrganizeInventory(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val productsList = repository.allProducts.firstOrNull().orEmpty()
            if (productsList.isEmpty()) {
                onResult(false, "El inventario está vacío.")
                return@launch
            }

            val jsonArray = org.json.JSONArray()
            productsList.forEach { p ->
                val obj = org.json.JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("category", p.category)
                    put("code", p.code)
                }
                jsonArray.put(obj)
            }

            val result = geminiRepository.smartOrganizeInventoryProducts(jsonArray.toString())
            result.fold(
                onSuccess = { rawResponse ->
                    try {
                        val cleaned = rawResponse
                            .replace("```json", "", ignoreCase = true)
                            .replace("```", "", ignoreCase = true)
                            .trim()

                        val parsedArray = org.json.JSONArray(cleaned)
                        var updatedCategoriesCount = 0
                        var generatedCodesCount = 0

                        for (i in 0 until parsedArray.length()) {
                            val item = parsedArray.getJSONObject(i)
                            val id = item.optInt("id", -1)
                            val newCategory = item.optString("category", "").trim()
                            val newCode = item.optString("code", "").trim()

                            val existing = productsList.find { it.id == id }
                            if (existing != null) {
                                var changed = false
                                var catToSave = existing.category
                                var codeToSave = existing.code

                                if (newCategory.isNotBlank() && newCategory != existing.category) {
                                    catToSave = newCategory
                                    updatedCategoriesCount++
                                    changed = true
                                }
                                if ((existing.code.isBlank() || existing.code == "S/N" || existing.code == "0") && newCode.isNotBlank()) {
                                    codeToSave = newCode
                                    generatedCodesCount++
                                    changed = true
                                } else if (existing.code.isBlank() && newCode.isBlank()) {
                                    codeToSave = "PRD-${String.format(Locale.US, "%03d", existing.id)}"
                                    generatedCodesCount++
                                    changed = true
                                }

                                if (changed) {
                                    val updatedProduct = existing.copy(category = catToSave, code = codeToSave)
                                    repository.updateProduct(updatedProduct)
                                    cloudSyncRepository.pushProduct(updatedProduct)
                                }
                            }
                        }

                        onResult(
                            true,
                            "¡Organización inteligente exitosa! Se actualizaron $updatedCategoriesCount categorías y se generaron $generatedCodesCount códigos automáticamente."
                        )
                    } catch (e: Exception) {
                        onResult(false, "Error al procesar la respuesta de IA: ${e.localizedMessage}")
                    }
                },
                onFailure = { err ->
                    onResult(false, "Error de IA: ${err.localizedMessage ?: "No se pudo conectar con el servicio."}")
                }
            )
        }
    }
}

data class CartItem(val product: Product, val quantity: Double, val customPrice: Double? = null) {
    val effectivePrice: Double get() = customPrice ?: product.priceUsd
}

data class SystemNotification(
    val id: String,
    val title: String,
    val message: String,
    val type: NotificationType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class NotificationType {
    LOW_STOCK,
    LICENSE_EXPIRY,
    SYSTEM_ALERT
}
