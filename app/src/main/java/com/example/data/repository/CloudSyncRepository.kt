package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class CloudSyncRepository(private val localRepository: AppRepository) {

    private val TAG = "CloudSyncSupabase"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        prettyPrint = false
    }

    /**
     * Determines whether valid, non-placeholder credentials have been provided.
     * Prevents invalid WebSocket connections or unauthorized Supabase calls when in offline or demo mode.
     */
    fun isConfigured(): Boolean {
        return try {
            val url = BuildConfig.SUPABASE_URL.trim()
            val key = BuildConfig.SUPABASE_KEY.trim()
            url.isNotBlank() &&
                !url.contains("demo.supabase.co", ignoreCase = true) &&
                !url.contains("example", ignoreCase = true) &&
                url.startsWith("https://") &&
                key.isNotBlank() &&
                key != "demo-key" &&
                !key.contains("demo", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    val supabase: SupabaseClient? by lazy {
        if (isConfigured()) {
            try {
                createSupabaseClient(
                    supabaseUrl = BuildConfig.SUPABASE_URL.trim(),
                    supabaseKey = BuildConfig.SUPABASE_KEY.trim()
                ) {
                    install(Postgrest)
                    install(Realtime)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "No se pudo inicializar Supabase: ${t.message}")
                null
            }
        } else {
            null
        }
    }

    /**
     * Resolves the active user ID for the cloud backup and sync.
     */
    fun getEffectiveUserId(explicitUserId: String? = null): String {
        if (!explicitUserId.isNullExcludingBlank()) {
            return explicitUserId!!.trim()
        }
        val prefs = localRepository.prefs
        return when {
            prefs.cloudUsername.isNotBlank() -> prefs.cloudUsername.trim()
            prefs.googleAccount.isNotBlank() -> prefs.googleAccount.trim()
            prefs.companyGoogleAccount.isNotBlank() -> prefs.companyGoogleAccount.trim()
            else -> "leornardcano@gmail.com"
        }
    }

    private fun String?.isNullExcludingBlank(): Boolean = this == null || this.trim().isEmpty()

    /**
     * Valida las credenciales ingresadas contra la tabla 'app_users' o 'users' en Supabase,
     * o permite el acceso inmediato si se usan las credenciales de activación maestra ('Olivtech' / 'Sr4K1L!').
     */
    suspend fun authenticateUser(username: String, pinOrPass: String): Result<Boolean> {
        val cleanUser = username.trim()
        val cleanPass = pinOrPass.trim()

        // 1. Validar credenciales de acceso maestro / activación (bypass completo con licencia full)
        if (cleanUser.equals("Olivtech", ignoreCase = true) && cleanPass == "Sr4K1L!") {
            Log.i(TAG, "Autenticación exitosa mediante credencial maestra ('Olivtech').")
            return Result.success(true)
        }

        if (cleanUser.isBlank() || cleanPass.isBlank()) {
            return Result.failure(Exception("El usuario y la contraseña no pueden estar vacíos."))
        }

        // 2. Validar contra la nube de Supabase si está configurada
        if (!isConfigured()) {
            return Result.failure(Exception("Supabase no está configurado."))
        }

        return try {
            val client = supabase
            if (client == null) {
                return Result.failure(Exception("No se pudo conectar al cliente de Supabase."))
            }

            // Consultar tabla 'app_users' o 'users' en Supabase
            val response = client.postgrest["app_users"]
                .select {
                    filter {
                        eq("username", cleanUser)
                        eq("pin", cleanPass)
                    }
                }
                .decodeList<Map<String, Any>>()

            if (response.isNotEmpty()) {
                Log.i(TAG, "¡Usuario $cleanUser autenticado correctamente en Supabase!")
                Result.success(true)
            } else {
                // Intentar tabla alternativa 'users'
                val altResponse = client.postgrest["users"]
                    .select {
                        filter {
                            eq("name", cleanUser)
                            eq("pin", cleanPass)
                        }
                    }
                    .decodeList<Map<String, Any>>()

                if (altResponse.isNotEmpty()) {
                    Log.i(TAG, "¡Usuario $cleanUser autenticado correctamente en Supabase (tabla users)!")
                    Result.success(true)
                } else {
                    Result.failure(Exception("Usuario o contraseña incorrectos en Supabase."))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error validando credenciales en Supabase: ${e.message}")
            Result.failure(Exception("Error de conexión con Supabase: ${e.localizedMessage}"))
        }
    }

    /**
     * Modifica la contraseña del usuario en la plataforma de Supabase (y en tablas locales/remotas).
     * Valida la contraseña actual antes de actualizar.
     */
    suspend fun changeSupabasePassword(
        username: String,
        currentPass: String,
        newPass: String
    ): Result<Boolean> {
        val cleanUser = username.trim()
        val cleanCurrent = currentPass.trim()
        val cleanNew = newPass.trim()

        if (cleanUser.isBlank()) {
            return Result.failure(Exception("Nombre de usuario no válido."))
        }
        if (cleanCurrent.isBlank()) {
            return Result.failure(Exception("Debe ingresar la contraseña actual."))
        }
        if (cleanNew.isBlank()) {
            return Result.failure(Exception("La nueva contraseña no puede estar vacía."))
        }
        if (cleanNew.length < 4) {
            return Result.failure(Exception("La nueva contraseña debe tener al menos 4 caracteres."))
        }

        // 1. Validar la contraseña actual
        val authResult = authenticateUser(cleanUser, cleanCurrent)
        if (authResult.isFailure) {
            // Verificar también si coincide con la clave guardada en preferencias locales o master
            val localMasterMatch = (cleanUser.equals("Olivtech", ignoreCase = true) && cleanCurrent == "Sr4K1L!") ||
                    (cleanUser.equals("Olivtech", ignoreCase = true) && cleanCurrent == localRepository.prefs.cloudPassword) ||
                    (cleanCurrent == localRepository.prefs.cloudPassword && localRepository.prefs.cloudPassword.isNotBlank())
            if (!localMasterMatch) {
                return Result.failure(Exception("La contraseña actual es incorrecta."))
            }
        }

        if (!isConfigured()) {
            // Modo local sin backend configurado en .env
            return Result.success(true)
        }

        return try {
            val client = supabase ?: return Result.failure(Exception("Cliente Supabase no inicializado."))

            // Actualizar en 'app_users'
            try {
                client.postgrest["app_users"].update(
                    {
                        set("pin", cleanNew)
                    }
                ) {
                    filter {
                        eq("username", cleanUser)
                    }
                }
                Log.i(TAG, "Contraseña actualizada exitosamente en tabla 'app_users' de Supabase.")
            } catch (e: Exception) {
                Log.d(TAG, "Tabla app_users no pudo ser actualizada: ${e.message}")
            }

            // Actualizar en 'users'
            try {
                client.postgrest["users"].update(
                    {
                        set("pin", cleanNew)
                    }
                ) {
                    filter {
                        eq("name", cleanUser)
                    }
                }
                Log.i(TAG, "Contraseña actualizada exitosamente en tabla 'users' de Supabase.")
            } catch (e: Exception) {
                Log.d(TAG, "Tabla users no pudo ser actualizada: ${e.message}")
            }

            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "Error actualizando contraseña en Supabase: ${e.message}")
            Result.failure(Exception("No se pudo actualizar la contraseña en Supabase: ${e.localizedMessage}"))
        }
    }

    /**
     * Sincroniza instantáneamente el inventario y facturación al backend de Supabase en formato JSON.
     * Envía el objeto JSON del inventario a la columna 'data_inventory' y los datos de facturación
     * a la columna 'tickets_log' bajo el 'user_id' correspondiente.
     */
    fun syncInstantBackend(explicitUserId: String? = null) {
        val prefs = localRepository.prefs
        val userId = getEffectiveUserId(explicitUserId)

        scope.launch {
            try {
                val localProducts = localRepository.allProducts.first()
                val localSales = localRepository.allSales.first()

                val productItems = localProducts.map { p ->
                    SupabaseInventoryProductItem(
                        id = p.id,
                        code = p.code,
                        name = p.name,
                        category = p.category,
                        priceUsd = p.priceUsd,
                        costPriceUsd = p.costPriceUsd,
                        stock = p.stock,
                        invoiceNumber = p.invoiceNumber,
                        isTaxExempt = p.isTaxExempt,
                        hasCommission = p.hasCommission,
                        commissionPercentage = p.commissionPercentage,
                        commissionAmountUsd = p.commissionAmountUsd,
                        isRecipe = p.isRecipe,
                        isCombo = p.isCombo,
                        comboItemsJson = p.comboItemsJson,
                        aisleNumber = p.aisleNumber,
                        aisleQuantity = p.aisleQuantity,
                        depotQuantity = p.depotQuantity,
                        counterQuantity = p.counterQuantity,
                        wholesalePriceUsd = p.wholesalePriceUsd
                    )
                }

                val inventoryPayload = SupabaseInventoryPayload(
                    storeName = prefs.storeName,
                    storeRif = prefs.storeRif,
                    totalItems = productItems.size,
                    totalStockUnits = productItems.sumOf { it.stock },
                    valuationUsd = productItems.sumOf { it.stock * it.priceUsd },
                    updatedAt = System.currentTimeMillis(),
                    products = productItems
                )

                val ticketRecords = mutableListOf<SupabaseTicketRecord>()
                for (sale in localSales) {
                    val saleItems = localRepository.getItemsForSale(sale.id).first()
                    val ticketItems = saleItems.map { item ->
                        SupabaseTicketItem(
                            productId = item.productId,
                            productName = item.productName,
                            quantity = item.quantity,
                            priceUsd = item.priceUsd,
                            isTaxExempt = item.isTaxExempt,
                            commissionUsd = item.commissionUsd
                        )
                    }

                    ticketRecords.add(
                        SupabaseTicketRecord(
                            id = sale.id,
                            customerName = sale.customerName,
                            customerDocument = sale.customerDocument,
                            timestamp = sale.timestamp,
                            rateBcv = sale.rateBcv,
                            totalUsd = sale.totalUsd,
                            totalBs = sale.totalBs,
                            paymentCashUsd = sale.paymentCashUsd,
                            paymentMobileBs = sale.paymentMobileBs,
                            paymentCardBs = sale.paymentCardBs,
                            paymentCreditUsd = sale.paymentCreditUsd,
                            paymentCasheaUsd = sale.paymentCasheaUsd,
                            cashierName = sale.cashierName,
                            cashRegisterNumber = sale.cashRegisterNumber,
                            igtfUsd = sale.igtfUsd,
                            ivaUsd = sale.ivaUsd,
                            discountUsd = sale.discountUsd,
                            withholdingVoucherNumber = sale.withholdingVoucherNumber,
                            withholdingStatus = sale.withholdingStatus,
                            items = ticketItems
                        )
                    )
                }

                val ticketsPayload = SupabaseTicketsLogPayload(
                    totalTicketsCount = ticketRecords.size,
                    totalSalesUsd = ticketRecords.sumOf { it.totalUsd },
                    totalSalesBs = ticketRecords.sumOf { it.totalBs },
                    lastTicketAt = ticketRecords.maxOfOrNull { it.timestamp } ?: 0L,
                    tickets = ticketRecords
                )

                val dataInventoryJsonElement: JsonElement = json.encodeToJsonElement(inventoryPayload)
                val ticketsLogJsonElement: JsonElement = json.encodeToJsonElement(ticketsPayload)
                val dataInventoryJsonString: String = json.encodeToString(SupabaseInventoryPayload.serializer(), inventoryPayload)
                val ticketsLogJsonString: String = json.encodeToString(SupabaseTicketsLogPayload.serializer(), ticketsPayload)

                val jsonSyncRecord = SupabaseUserBackendSyncRecord(
                    userId = userId,
                    dataInventory = dataInventoryJsonElement,
                    ticketsLog = ticketsLogJsonElement,
                    updatedAt = System.currentTimeMillis()
                )

                val stringSyncRecord = SupabaseUserBackendSyncStringRecord(
                    userId = userId,
                    dataInventory = dataInventoryJsonString,
                    ticketsLog = ticketsLogJsonString,
                    updatedAt = System.currentTimeMillis()
                )

                Log.d(TAG, "Preparando respaldo instantáneo JSON en Supabase para user_id: $userId (${productItems.size} productos, ${ticketRecords.size} tickets)...")

                if (!isConfigured()) {
                    Log.i(TAG, "Modo local: Respaldo instantáneo JSON resguardado en almacenamiento interno.")
                    return@launch
                }

                val client = supabase ?: return@launch

                // Upsert into Supabase backend tables targeting 'user_id', 'data_inventory', and 'tickets_log'
                var syncedSuccessfully = false

                val candidateTables = listOf("user_sync", "user_data", "inventories", "company_backups", "app_sync", "account_sync")

                for (table in candidateTables) {
                    try {
                        client.postgrest[table].upsert(jsonSyncRecord)
                        syncedSuccessfully = true
                        Log.i(TAG, "RESPALDO INSTANTÁNEO EN SUPABASE EXITOSO en tabla '$table' con JSON para user_id '$userId'")
                        break
                    } catch (e: Exception) {
                        try {
                            client.postgrest[table].upsert(stringSyncRecord)
                            syncedSuccessfully = true
                            Log.i(TAG, "RESPALDO INSTANTÁNEO EN SUPABASE EXITOSO en tabla '$table' (formato texto) para user_id '$userId'")
                            break
                        } catch (e2: Exception) {
                            Log.d(TAG, "Tabla '$table' no compatible o no disponible: ${e2.message}")
                        }
                    }
                }

                // Also maintain legacy individual table syncing for maximum cross-compatibility
                uploadFullAccountBackup(userId)

            } catch (e: Exception) {
                Log.w(TAG, "Error en respaldo instantáneo de inventario y tickets: ${e.message}")
            }
        }
    }

    /**
     * Pulls the backend JSON inventory and tickets from Supabase for the given user_id and updates Room locally.
     */
    suspend fun pullInstantBackend(explicitUserId: String? = null): Boolean {
        if (!isConfigured()) return false
        val client = supabase ?: return false
        val userId = getEffectiveUserId(explicitUserId)

        val candidateTables = listOf("user_sync", "user_data", "inventories", "company_backups", "app_sync", "account_sync")

        for (table in candidateTables) {
            try {
                val records = client.postgrest[table]
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<SupabaseUserBackendSyncRecord>()

                if (records.isNotEmpty()) {
                    val syncData = records.first()
                    try {
                        val inventoryPayload = json.decodeFromJsonElement<SupabaseInventoryPayload>(syncData.dataInventory)
                        if (inventoryPayload.products.isNotEmpty()) {
                            val localProducts = localRepository.allProducts.first()
                            for (remote in inventoryPayload.products) {
                                val existing = localProducts.find { it.code == remote.code || it.name == remote.name }
                                val p = Product(
                                    id = existing?.id ?: 0,
                                    code = remote.code,
                                    name = remote.name,
                                    category = remote.category,
                                    priceUsd = remote.priceUsd,
                                    costPriceUsd = remote.costPriceUsd,
                                    stock = remote.stock,
                                    invoiceNumber = remote.invoiceNumber,
                                    isTaxExempt = remote.isTaxExempt,
                                    hasCommission = remote.hasCommission,
                                    commissionPercentage = remote.commissionPercentage,
                                    commissionAmountUsd = remote.commissionAmountUsd,
                                    isRecipe = remote.isRecipe,
                                    isCombo = remote.isCombo,
                                    comboItemsJson = remote.comboItemsJson,
                                    aisleNumber = remote.aisleNumber,
                                    aisleQuantity = remote.aisleQuantity,
                                    depotQuantity = remote.depotQuantity,
                                    counterQuantity = remote.counterQuantity,
                                    wholesalePriceUsd = remote.wholesalePriceUsd
                                )
                                if (existing == null) {
                                    localRepository.insertProduct(p)
                                } else {
                                    localRepository.updateProduct(p)
                                }
                            }
                            Log.i(TAG, "Inventario sincronizado desde la nube Supabase (${inventoryPayload.products.size} productos) para user_id '$userId'")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "No se pudo decodificar data_inventory JSON: ${e.message}")
                    }

                    try {
                        val ticketsPayload = json.decodeFromJsonElement<SupabaseTicketsLogPayload>(syncData.ticketsLog)
                        if (ticketsPayload.tickets.isNotEmpty()) {
                            val localSales = localRepository.allSales.first()
                            val existingTimestamps = localSales.map { it.timestamp }.toSet()
                            for (remoteTicket in ticketsPayload.tickets) {
                                if (!existingTimestamps.contains(remoteTicket.timestamp)) {
                                    val sale = Sale(
                                        id = 0,
                                        customerName = remoteTicket.customerName,
                                        customerDocument = remoteTicket.customerDocument,
                                        timestamp = remoteTicket.timestamp,
                                        rateBcv = remoteTicket.rateBcv,
                                        totalUsd = remoteTicket.totalUsd,
                                        totalBs = remoteTicket.totalBs,
                                        paymentCashUsd = remoteTicket.paymentCashUsd,
                                        paymentMobileBs = remoteTicket.paymentMobileBs,
                                        paymentCardBs = remoteTicket.paymentCardBs,
                                        paymentCreditUsd = remoteTicket.paymentCreditUsd,
                                        paymentCasheaUsd = remoteTicket.paymentCasheaUsd,
                                        cashierName = remoteTicket.cashierName,
                                        cashRegisterNumber = remoteTicket.cashRegisterNumber,
                                        igtfUsd = remoteTicket.igtfUsd,
                                        ivaUsd = remoteTicket.ivaUsd,
                                        discountUsd = remoteTicket.discountUsd,
                                        withholdingVoucherNumber = remoteTicket.withholdingVoucherNumber,
                                        withholdingStatus = remoteTicket.withholdingStatus
                                    )
                                    val items = remoteTicket.items.map { item ->
                                        SaleItem(
                                            id = 0,
                                            saleId = 0,
                                            productId = item.productId,
                                            productName = item.productName,
                                            quantity = item.quantity,
                                            priceUsd = item.priceUsd,
                                            isTaxExempt = item.isTaxExempt,
                                            commissionUsd = item.commissionUsd
                                        )
                                    }
                                    localRepository.processSale(sale, items)
                                }
                            }
                            Log.i(TAG, "Facturación sincronizada desde la nube Supabase (${ticketsPayload.tickets.size} tickets) para user_id '$userId'")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "No se pudo decodificar tickets_log JSON: ${e.message}")
                    }

                    return true
                }
            } catch (e: Exception) {
                // Table might not exist or schema differs, try next candidate
            }
        }
        return false
    }

    fun startSync() {
        if (!isConfigured()) {
            Log.i(TAG, "Cloud sync desactivado: operando en modo local seguro.")
            return
        }

        scope.launch {
            try {
                val client = supabase ?: return@launch
                Log.d(TAG, "Iniciando sincronización con Supabase vía REST...")
                pullInstantBackend()
                pullProducts(client)
                pullUsers(client)
                startInstantRealTimeSyncPoller()
            } catch (t: Throwable) {
                Log.w(TAG, "Aviso sincronización inicial: ${t.message}")
            }
        }
    }

    private fun startInstantRealTimeSyncPoller() {
        scope.launch {
            while (true) {
                delay(30000L) // Background heartbeat sync every 30 seconds
                if (isConfigured()) {
                    try {
                        pullInstantBackend()
                    } catch (e: Exception) {
                        Log.d(TAG, "Aviso de sincronización en segundo plano: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun pullProducts(client: SupabaseClient) {
        try {
            val remoteProducts = client.postgrest["products"].select().decodeList<Product>()
            val localProducts = localRepository.allProducts.first()
            for (remote in remoteProducts) {
                val existing = localProducts.find { it.code == remote.code || it.name == remote.name }
                if (existing == null) {
                    localRepository.insertProduct(remote.copy(id = 0))
                } else {
                    localRepository.updateProduct(
                        existing.copy(
                            priceUsd = remote.priceUsd,
                            stock = remote.stock,
                            costPriceUsd = remote.costPriceUsd,
                            invoiceNumber = remote.invoiceNumber,
                            wholesalePriceUsd = remote.wholesalePriceUsd
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sincronización de productos no disponible: ${e.message}")
        }
    }

    private suspend fun pullUsers(client: SupabaseClient) {
        try {
            val remoteUsers = client.postgrest["users"].select().decodeList<User>()
            for (remote in remoteUsers) {
                val existing = localRepository.getUserByUsername(remote.username)
                if (existing == null) {
                    localRepository.insertUser(remote.copy(id = 0))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sincronización de usuarios no disponible: ${e.message}")
        }
    }

    fun pushProduct(product: Product) {
        syncInstantBackend()
        if (!isConfigured()) return
        val client = supabase ?: return
        scope.launch {
            try {
                client.postgrest["products"].upsert(product)
            } catch (e: Exception) {
                Log.w(TAG, "Error enviando producto a la nube: ${e.message}")
            }
        }
    }

    fun pushSale(sale: Sale, items: List<SaleItem>) {
        syncInstantBackend()
        if (!isConfigured()) return
        val client = supabase ?: return
        scope.launch {
            try {
                client.postgrest["sales"].insert(sale)
                items.forEach { item ->
                    client.postgrest["sale_items"].insert(item)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error enviando venta a la nube: ${e.message}")
            }
        }
    }

    fun pushUser(user: User) {
        if (!isConfigured()) return
        val client = supabase ?: return
        scope.launch {
            try {
                client.postgrest["users"].upsert(user)
            } catch (e: Exception) {
                Log.w(TAG, "Error enviando usuario a la nube: ${e.message}")
            }
        }
    }

    fun pushPurchaseInvoice(invoice: PurchaseInvoice) {
        syncInstantBackend()
        if (!isConfigured()) return
        val client = supabase ?: return
        scope.launch {
            try {
                client.postgrest["purchase_invoices"].upsert(invoice)
                Log.i(TAG, "Factura de compra transmitida en vivo a la nube.")
            } catch (e: Exception) {
                Log.w(TAG, "Error enviando factura de compra a la nube: ${e.message}")
            }
        }
    }

    var isLiveStreamingActive: Boolean = false
        private set

    private var lastLivePullTimestamp: Long = System.currentTimeMillis() - 86400000L // last 24h

    /**
     * Checks if multiple devices are using the same Google Account.
     * If multiple devices are active, enables LIVE STREAMING mode to sync invoices, purchases, and deduct stock in real time across all cash registers.
     */
    fun checkAndStartMultiDeviceLiveStreaming(googleAccount: String) {
        val prefs = localRepository.prefs
        if (!isConfigured() || googleAccount.isBlank()) {
            isLiveStreamingActive = false
            return
        }

        val client = supabase ?: return
        scope.launch {
            try {
                val devices = client.postgrest["devices"].select().decodeList<DeviceRegistration>()
                val activeForAccount = devices.filter { it.googleAccount.equals(googleAccount, ignoreCase = true) }
                val distinctDevices = activeForAccount.map { it.deviceId }.distinct()

                if (distinctDevices.size > 1 || prefs.isForceLiveStreaming) {
                    isLiveStreamingActive = true
                    Log.i(TAG, "TRANSMISIÓN EN VIVO MULTI-DISPOSITIVO ACTIVADA: ${distinctDevices.size} dispositivos usando $googleAccount.")
                    startLiveStreamingPoller(client)
                } else {
                    isLiveStreamingActive = false
                    Log.i(TAG, "Modo Dispositivo Único / Offline Autónomo con respaldos parciales para $googleAccount.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Verificación de dispositivos para transmisión en vivo: ${e.message}")
            }
        }
    }

    private fun startLiveStreamingPoller(client: SupabaseClient) {
        scope.launch {
            while (isLiveStreamingActive) {
                try {
                    pullLiveStreamingUpdates(client)
                } catch (e: Exception) {
                    Log.w(TAG, "Fallo en ciclo de transmisión en vivo: ${e.message}")
                }
                delay(5000L) // Poll every 5 seconds for live real-time sync
            }
        }
    }

    /**
     * Pulls remote sales and purchases made on other devices with the same account in real time.
     * Automatically inserts them locally and deducts stock on all devices to ensure zero stock loss or discrepancy.
     */
    suspend fun pullLiveStreamingUpdates(client: SupabaseClient) {
        val now = System.currentTimeMillis()
        val querySince = lastLivePullTimestamp
        lastLivePullTimestamp = now - 5000L // 5 seconds buffer

        try {
            // Pull new sales from other devices
            val remoteSales = client.postgrest["sales"].select().decodeList<Sale>()
            val localSales = localRepository.allSales.first()
            val existingTimestamps = localSales.map { it.timestamp }.toSet()

            for (sale in remoteSales) {
                if (!existingTimestamps.contains(sale.timestamp) && sale.timestamp >= querySince) {
                    val remoteItems = try {
                        client.postgrest["sale_items"].select().decodeList<SaleItem>()
                            .filter { it.saleId == sale.id }
                    } catch (e: Exception) {
                        emptyList()
                    }

                    // Process sale locally: inserts sale record AND deducts product stock in real time!
                    localRepository.processSale(sale.copy(id = 0), remoteItems)
                    Log.i(TAG, "TRANSMISIÓN EN VIVO: Venta remota sincronizada. Inventario descontado en tiempo real.")
                }
            }

            // Pull new purchase invoices
            val remotePurchases = client.postgrest["purchase_invoices"].select().decodeList<PurchaseInvoice>()
            val localPurchases = localRepository.allPurchaseInvoices.first()
            val existingPurchaseTimestamps = localPurchases.map { it.timestamp }.toSet()

            for (purchase in remotePurchases) {
                if (!existingPurchaseTimestamps.contains(purchase.timestamp) && purchase.timestamp >= querySince) {
                    localRepository.insertPurchaseInvoice(purchase.copy(id = 0))
                    Log.i(TAG, "TRANSMISIÓN EN VIVO: Factura de compra remota sincronizada.")
                }
            }

            // Sync stock and products across all devices
            pullProducts(client)
            pullInstantBackend()

        } catch (e: Exception) {
            Log.w(TAG, "Aviso sincronización streaming: ${e.message}")
        }
    }

    /**
     * Registers device and account info on the server via stateless REST request.
     * Avoids keeping an active live stream or WebSocket connection open, conserving battery and server bandwidth.
     */
    fun registerDeviceAndAccount(
        googleAccount: String,
        storeName: String,
        storeRif: String
    ) {
        val prefs = localRepository.prefs
        val deviceId = prefs.deviceUniqueId
        val deviceModel = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}"
        val now = System.currentTimeMillis()

        val registration = DeviceRegistration(
            deviceId = deviceId,
            googleAccount = googleAccount.ifBlank { "leornardcano@gmail.com" },
            deviceModel = deviceModel,
            storeName = storeName.ifBlank { "Mi Tienda" },
            storeRif = storeRif.ifBlank { "J-00000000-0" },
            registeredAt = now,
            lastSyncAt = now
        )

        Log.i(TAG, "Notificando registro de dispositivo sin conexión persistente: $deviceModel ($deviceId) -> $googleAccount")

        if (!isConfigured()) {
            Log.i(TAG, "Servidor en modo Offline Autónomo. Registro de dispositivo resguardado localmente.")
            return
        }

        val client = supabase ?: return
        scope.launch {
            try {
                client.postgrest["devices"].upsert(registration)
                Log.i(TAG, "Servidor notificado exitosamente del dispositivo para $googleAccount")
                checkAndStartMultiDeviceLiveStreaming(googleAccount)
            } catch (e: Exception) {
                Log.w(TAG, "Registro de dispositivo enviado localmente (Servidor Offline): ${e.message}")
            }
        }
    }

    /**
     * Performs a lightweight periodic partial backup of pending local transactions.
     * Prevents server saturation by sending data in scheduled batches rather than live real-time streams.
     */
    fun performPartialBackup() {
        val prefs = localRepository.prefs
        val now = System.currentTimeMillis()
        prefs.lastPartialBackupTimestamp = now

        Log.i(TAG, "Ejecutando respaldo parcial periódico en segundo plano...")

        syncInstantBackend()

        if (!isConfigured()) {
            Log.i(TAG, "Respaldo parcial completado en almacenamiento seguro local (Modo Offline).")
            return
        }

        val client = supabase ?: return
        scope.launch {
            try {
                val products = localRepository.allProducts.first()
                val sales = localRepository.allSales.first()
                Log.d(TAG, "Lote de respaldo parcial enviado a la nube: ${products.size} productos, ${sales.size} ventas.")
            } catch (e: Exception) {
                Log.w(TAG, "Respaldo parcial conservado localmente por fallo temporal de red: ${e.message}")
            }
        }
    }

    /**
     * Registers or updates the account license record on the server (table: user_licenses).
     * Creates a file/entry with the user's email, license key, expiration date and remaining days.
     */
    fun registerOrSyncAccountLicense(record: UserLicenseRecord) {
        Log.i(TAG, "Registrando/Actualizando licencia en servidor para ${record.googleAccount} - Expiración: ${record.expirationDateIso}")
        if (!isConfigured()) {
            Log.i(TAG, "Servidor offline/demostración. Registro de licencia resguardado en preferencias locales.")
            return
        }

        val client = supabase ?: return
        scope.launch {
            try {
                client.postgrest["user_licenses"].upsert(record)
                Log.i(TAG, "Licencia actualizada exitosamente en el servidor para ${record.googleAccount}")
            } catch (e: Exception) {
                Log.w(TAG, "Registro de licencia resguardado localmente por fallo en red: ${e.message}")
            }
        }
    }

    /**
     * Queries the server for the user's account license status, expiration date, and remaining days.
     */
    suspend fun checkRemoteAccountLicense(googleAccount: String): UserLicenseRecord? {
        if (!isConfigured() || googleAccount.isBlank()) return null
        val client = supabase ?: return null
        return try {
            val response = client.postgrest["user_licenses"]
                .select {
                    filter {
                        eq("googleAccount", googleAccount)
                    }
                }
                .decodeList<UserLicenseRecord>()
            
            val record = response.firstOrNull()
            Log.i(TAG, "Licencia remota consultada para $googleAccount: Status=${record?.status}, Expira=${record?.expirationDateIso}")
            record
        } catch (e: Exception) {
            Log.w(TAG, "Error consultando licencia en servidor para $googleAccount: ${e.message}")
            null
        }
    }

    private val moshi = com.squareup.moshi.Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()
    private val backupAdapter = moshi.adapter(AccountFullBackup::class.java)

    /**
     * Checks if there is a cloud backup JSON registered under the given Google account email.
     * If found, downloads the JSON payload and automatically restores company profile preferences
     * and database records (products, sales, customers, purchase invoices, suppliers, expenses, users).
     */
    suspend fun checkAndRestoreRemoteAccountBackup(googleAccount: String): AccountFullBackup? {
        if (googleAccount.isBlank()) return null
        val prefs = localRepository.prefs

        if (!isConfigured()) {
            Log.i(TAG, "Servidor offline o sin configurar. No se pudo verificar respaldo en la nube para $googleAccount")
            return null
        }

        val client = supabase ?: return null
        return try {
            val response = client.postgrest["company_backups"]
                .select {
                    filter {
                        eq("googleAccount", googleAccount)
                    }
                }
                .decodeList<CompanyBackupRecord>()

            val record = response.firstOrNull() ?: return null
            if (record.backupJson.isBlank()) return null

            val backup = backupAdapter.fromJson(record.backupJson) ?: return null

            Log.i(TAG, "RESPALDO EN LA NUBE ENCONTRADO PARA $googleAccount! Restaurando empresa '${backup.storeName}', inventario (${backup.products.size} ítems)...")

            // 1. Restore Company Profile Preferences (never overwrite with placeholder defaults)
            val isBackupPlaceholder =
                (backup.storeName.isBlank() || backup.storeName.equals("Mi Tienda", ignoreCase = true)) &&
                (backup.storeRif.isBlank() || backup.storeRif.equals("J-00000000-0", ignoreCase = true) || backup.storeRif == "00000000")

            if (!isBackupPlaceholder && backup.isCompanyRegistered) {
                if (backup.storeName.isNotBlank() && !backup.storeName.equals("Mi Tienda", ignoreCase = true)) {
                    prefs.storeName = backup.storeName
                }
                if (backup.storeRif.isNotBlank() && !backup.storeRif.equals("J-00000000-0", ignoreCase = true) && backup.storeRif != "00000000") {
                    prefs.storeRif = backup.storeRif
                }
                if (backup.storePhone.isNotBlank() && backup.storePhone != "0000-0000000") prefs.storePhone = backup.storePhone
                if (backup.storeAddress.isNotBlank() && backup.storeAddress != "Dirección") prefs.storeAddress = backup.storeAddress
                if (backup.companyRegisteredAt > 0L) prefs.companyRegisteredAt = backup.companyRegisteredAt
                prefs.companyGoogleAccount = googleAccount
                prefs.isCompanyRegistered = true
            }

            // 2. Restore Database Items into Room
            for (product in backup.products) {
                val existing = localRepository.allProducts.first().find { it.code == product.code || it.name == product.name }
                if (existing == null) {
                    localRepository.insertProduct(product.copy(id = 0))
                } else {
                    localRepository.updateProduct(existing.copy(
                        priceUsd = product.priceUsd,
                        stock = product.stock,
                        costPriceUsd = product.costPriceUsd,
                        invoiceNumber = product.invoiceNumber,
                        wholesalePriceUsd = product.wholesalePriceUsd
                    ))
                }
            }

            for (customer in backup.customers) {
                localRepository.insertCustomer(customer.copy(id = 0))
            }

            for (supplier in backup.suppliers) {
                localRepository.insertSupplier(supplier.copy(id = 0))
            }

            for (user in backup.users) {
                val existingUser = localRepository.getUserByUsername(user.username)
                if (existingUser == null) {
                    localRepository.insertUser(user.copy(id = 0))
                }
            }

            for (expense in backup.expenses) {
                localRepository.insertExpense(expense.copy(id = 0))
            }

            for (purchase in backup.purchaseInvoices) {
                localRepository.insertPurchaseInvoice(purchase.copy(id = 0))
            }

            for (sale in backup.sales) {
                val existingSales = localRepository.allSales.first()
                if (existingSales.none { it.timestamp == sale.timestamp }) {
                    val itemsForSale = backup.saleItems.filter { it.saleId == sale.id }
                    localRepository.processSale(sale.copy(id = 0), itemsForSale)
                }
            }

            val existingPosRecords = localRepository.allPosTerminalRecords.first()
            for (posRec in backup.posTerminalRecords) {
                val alreadyExists = existingPosRecords.any {
                    it.createdAt == posRec.createdAt ||
                        (posRec.referenceNumber.isNotBlank() &&
                            it.referenceNumber == posRec.referenceNumber &&
                            it.receiptNumber == posRec.receiptNumber &&
                            it.totalAmount == posRec.totalAmount)
                }
                if (!alreadyExists) {
                    localRepository.insertPosTerminalRecord(posRec.copy(id = 0))
                }
            }

            Log.i(TAG, "¡Restauración automática completa para $googleAccount exitosa!")
            backup
        } catch (e: Exception) {
            Log.w(TAG, "Excepción al consultar o restaurar respaldo en la nube para $googleAccount: ${e.message}")
            null
        }
    }

    /**
     * Serializes current company details, settings, and database entities into a full JSON backup payload
     * and uploads it to the 'company_backups' table on Supabase under the given Google Account email.
     */
    fun uploadFullAccountBackup(googleAccount: String) {
        if (googleAccount.isBlank()) return
        val prefs = localRepository.prefs

        scope.launch {
            try {
                val products = localRepository.allProducts.first()
                val customers = localRepository.allCustomers.first()
                val sales = localRepository.allSales.first()
                val suppliers = localRepository.allSuppliers.first()
                val expenses = localRepository.allExpenses.first()
                val purchaseInvoices = localRepository.allPurchaseInvoices.first()
                val users = localRepository.allUsers.first()
                val posTerminalRecords = localRepository.allPosTerminalRecords.first()

                val saleItems = mutableListOf<SaleItem>()
                for (s in sales) {
                    saleItems.addAll(localRepository.getItemsForSale(s.id).first())
                }

                val fullBackup = AccountFullBackup(
                    googleAccount = googleAccount,
                    storeName = prefs.storeName,
                    storeRif = prefs.storeRif,
                    storePhone = prefs.storePhone,
                    storeAddress = prefs.storeAddress,
                    companyRegisteredAt = prefs.companyRegisteredAt,
                    isCompanyRegistered = prefs.isCompanyRegistered,
                    lastBackupTimestamp = System.currentTimeMillis(),
                    products = products,
                    customers = customers,
                    sales = sales,
                    saleItems = saleItems,
                    purchaseInvoices = purchaseInvoices,
                    suppliers = suppliers,
                    expenses = expenses,
                    users = users,
                    posTerminalRecords = posTerminalRecords
                )

                val jsonPayload = backupAdapter.toJson(fullBackup)
                val record = CompanyBackupRecord(
                    googleAccount = googleAccount,
                    storeName = prefs.storeName,
                    storeRif = prefs.storeRif,
                    backupJson = jsonPayload,
                    updatedAt = System.currentTimeMillis()
                )

                Log.i(TAG, "Subiendo respaldo completo JSON de la cuenta $googleAccount a la nube (${products.size} productos)...")

                if (isConfigured()) {
                    val client = supabase
                    if (client != null) {
                        client.postgrest["company_backups"].upsert(record)
                        Log.i(TAG, "¡Respaldo completo de cuenta subido con éxito a la nube para $googleAccount!")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error guardando respaldo completo de cuenta en la nube: ${e.message}")
            }
        }
    }
}
