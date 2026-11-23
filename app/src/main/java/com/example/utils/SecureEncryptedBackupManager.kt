package com.example.utils

import android.content.Context
import android.net.Uri
import com.example.data.local.*
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Data structure containing complete POS, inventory, billing, quotes, expenses, and user records.
 */
@JsonClass(generateAdapter = true)
data class EncryptedPosBackupPackage(
    val formatVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val storeName: String = "",
    val storeRif: String = "",
    val storePhone: String = "",
    val storeAddress: String = "",
    val bcvRate: Double = 0.0,
    val isCompanyRegistered: Boolean = false,
    val companyRegisteredAt: Long = 0L,
    val products: List<Product> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val sales: List<Sale> = emptyList(),
    val saleItems: List<SaleItem> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val purchaseInvoices: List<PurchaseInvoice> = emptyList(),
    val deliveryNotes: List<DeliveryNote> = emptyList(),
    val deliveryNoteItems: List<DeliveryNoteItem> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val users: List<User> = emptyList(),
    val quotes: List<Quote> = emptyList(),
    val quoteItems: List<QuoteItem> = emptyList(),
    val casheaSettlements: List<CasheaSettlement> = emptyList()
)

data class EncryptedBackupPayloadResult(
    val encryptedBytes: ByteArray,
    val formatVersion: Int,
    val exportedAt: Long,
    val productsCount: Int,
    val salesCount: Int,
    val customersCount: Int
)

data class ImportBackupResult(
    val success: Boolean,
    val message: String,
    val productsCount: Int = 0,
    val customersCount: Int = 0,
    val salesCount: Int = 0,
    val invoicesCount: Int = 0,
    val deliveryNotesCount: Int = 0,
    val expensesCount: Int = 0
)

class SecureEncryptedBackupManager(
    private val context: Context,
    private val database: AppDatabase
) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    companion object {
        private const val CIPHER_ALGO = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
        private const val IV_LENGTH_BYTES = 12
        private const val SALT_LENGTH_BYTES = 16
        private const val PBKDF2_ITERATIONS = 65536
        private const val KEY_LENGTH_BITS = 256

        // System master secret for seamless cross-device interoperability between app installations
        const val DEFAULT_SYSTEM_PASSPHRASE = "CAJA_POS_SECURE_MASTER_SYNC_KEY_2026_V1"
        private val FILE_MAGIC_HEADER = "CAJAPOS_ENC_JSON_V1".toByteArray(StandardCharsets.UTF_8)
        private val FILE_MAGIC_HEADER_V2 = "CAJAPOS_ENC_GZIP_V2".toByteArray(StandardCharsets.UTF_8)
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun gzipCompress(data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(data) }
        return baos.toByteArray()
    }

    private fun gzipDecompress(data: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    }

    /**
     * Serializes all Room database entities (and optional store preferences) into an encrypted,
     * tamper-evident AES-256-GCM byte payload.
     */
    suspend fun exportEncryptedBytes(
        storeName: String = "",
        storeRif: String = "",
        prefs: PreferencesManager? = null,
        customPassphrase: String? = null,
        compressGzip: Boolean = true
    ): Result<EncryptedBackupPayloadResult> = withContext(Dispatchers.IO) {
        try {
            val products = database.productDao().getAllProducts().first()
            val customers = database.customerDao().getAllCustomers().first()
            val sales = database.saleDao().getAllSales().first()
            val saleItems = database.saleDao().getAllSaleItems().first()
            val suppliers = database.supplierDao().getAllSuppliers().first()
            val purchaseInvoices = database.purchaseInvoiceDao().getAllInvoices().first()
            val deliveryNotes = database.deliveryNoteDao().getAllDeliveryNotes().first()
            val deliveryNoteItems = deliveryNotes.flatMap {
                database.deliveryNoteDao().getItemsForDeliveryNoteDirect(it.id)
            }
            val expenses = database.expenseDao().getAllExpenses().first()
            val users = database.userDao().getAllUsers().first()
            val quotes = database.quoteDao().getAllQuotes().first()
            val quoteItems = quotes.flatMap {
                database.quoteDao().getItemsForQuoteDirect(it.id)
            }
            val casheaSettlements = database.casheaSettlementDao().getAllSettlements().first()

            val exportedAt = System.currentTimeMillis()
            val formatVersion = if (compressGzip) 2 else 1

            val backupPackage = EncryptedPosBackupPackage(
                formatVersion = formatVersion,
                exportedAt = exportedAt,
                storeName = storeName.ifBlank { prefs?.storeName.orEmpty() },
                storeRif = storeRif.ifBlank { prefs?.storeRif.orEmpty() },
                storePhone = prefs?.storePhone.orEmpty(),
                storeAddress = prefs?.storeAddress.orEmpty(),
                bcvRate = prefs?.lastBcvRate?.toDouble() ?: 0.0,
                isCompanyRegistered = prefs?.isCompanyRegistered ?: false,
                companyRegisteredAt = prefs?.companyRegisteredAt ?: 0L,
                products = products,
                customers = customers,
                sales = sales,
                saleItems = saleItems,
                suppliers = suppliers,
                purchaseInvoices = purchaseInvoices,
                deliveryNotes = deliveryNotes,
                deliveryNoteItems = deliveryNoteItems,
                expenses = expenses,
                users = users,
                quotes = quotes,
                quoteItems = quoteItems,
                casheaSettlements = casheaSettlements
            )

            val jsonString = moshi.adapter(EncryptedPosBackupPackage::class.java).toJson(backupPackage)
            val rawJsonBytes = jsonString.toByteArray(StandardCharsets.UTF_8)
            val plainBytes = if (compressGzip) gzipCompress(rawJsonBytes) else rawJsonBytes
            val header = if (compressGzip) FILE_MAGIC_HEADER_V2 else FILE_MAGIC_HEADER

            val random = SecureRandom()
            val salt = ByteArray(SALT_LENGTH_BYTES).apply { random.nextBytes(this) }
            val iv = ByteArray(IV_LENGTH_BYTES).apply { random.nextBytes(this) }

            val effectivePassphrase = customPassphrase?.takeIf { it.isNotBlank() } ?: DEFAULT_SYSTEM_PASSPHRASE
            val secretKey = deriveAesKey(effectivePassphrase, salt)

            val cipher = Cipher.getInstance(CIPHER_ALGO)
            val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            cipher.updateAAD(header)

            val ciphertextWithTag = cipher.doFinal(plainBytes)

            val outStream = ByteArrayOutputStream(
                header.size + salt.size + iv.size + ciphertextWithTag.size
            )
            outStream.write(header)
            outStream.write(salt)
            outStream.write(iv)
            outStream.write(ciphertextWithTag)

            Result.success(
                EncryptedBackupPayloadResult(
                    encryptedBytes = outStream.toByteArray(),
                    formatVersion = formatVersion,
                    exportedAt = exportedAt,
                    productsCount = products.size,
                    salesCount = sales.size,
                    customersCount = customers.size
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Gathers all local database entities and serializes them into an encrypted, tamper-evident binary JSON package.
     */
    suspend fun exportEncryptedDataToUri(
        uri: Uri,
        storeName: String = "",
        storeRif: String = "",
        customPassphrase: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payloadResult = exportEncryptedBytes(
                storeName = storeName,
                storeRif = storeRif,
                customPassphrase = customPassphrase,
                compressGzip = false
            ).getOrElse { return@withContext Result.failure(it) }

            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(payloadResult.encryptedBytes)
                os.flush()
            } ?: return@withContext Result.failure(Exception("No se pudo abrir el archivo de destino para escritura."))

            val summaryMsg = "Exportación encriptada con éxito: ${payloadResult.productsCount} productos, ${payloadResult.salesCount} ventas y ${payloadResult.customersCount} clientes protegidos con AES-256 GCM."
            Result.success(summaryMsg)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Reads, verifies cryptographic authenticity, decrypts, and restores/merges data from an encrypted byte array.
     */
    suspend fun importAndProcessEncryptedBytes(
        fileBytes: ByteArray,
        appendMode: Boolean = true,
        customPassphrase: String? = null,
        cloudSyncRestore: Boolean = false,
        prefs: PreferencesManager? = null
    ): ImportBackupResult = withContext(Dispatchers.IO) {
        try {
            val headerLen = FILE_MAGIC_HEADER.size
            val minLength = headerLen + SALT_LENGTH_BYTES + IV_LENGTH_BYTES + 16 // Header + Salt + IV + GCM Auth Tag

            if (fileBytes.size < minLength) {
                return@withContext ImportBackupResult(false, "El archivo es inválido o no corresponde a una copia encriptada de la aplicación.")
            }

            val buffer = ByteBuffer.wrap(fileBytes)

            // 1. Verify Magic Header (V1 plain JSON or V2 GZIP JSON)
            val header = ByteArray(headerLen)
            buffer.get(header)
            val isGzipV2 = header.contentEquals(FILE_MAGIC_HEADER_V2)
            val isJsonV1 = header.contentEquals(FILE_MAGIC_HEADER)
            if (!isGzipV2 && !isJsonV1) {
                return@withContext ImportBackupResult(false, "Formato de archivo no reconocido. Debe ser un paquete JSON encriptado (.enc o .cajapos).")
            }

            // 2. Extract Salt and IV
            val salt = ByteArray(SALT_LENGTH_BYTES)
            buffer.get(salt)

            val iv = ByteArray(IV_LENGTH_BYTES)
            buffer.get(iv)

            // 3. Extract Ciphertext and Auth Tag
            val cipherBytes = ByteArray(buffer.remaining())
            buffer.get(cipherBytes)

            // 4. Decrypt & Authenticate Integrity
            val effectivePassphrase = customPassphrase?.takeIf { it.isNotBlank() } ?: DEFAULT_SYSTEM_PASSPHRASE
            val secretKey = deriveAesKey(effectivePassphrase, salt)

            val cipher = Cipher.getInstance(CIPHER_ALGO)
            val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            cipher.updateAAD(header)

            val decryptedBytes = try {
                cipher.doFinal(cipherBytes)
            } catch (e: Exception) {
                return@withContext ImportBackupResult(
                    false,
                    "Error de integridad: El respaldo está dañado, fue modificado externamente o la clave de desencriptación es incorrecta."
                )
            }

            val plainBytes = if (isGzipV2) gzipDecompress(decryptedBytes) else decryptedBytes
            val jsonString = String(plainBytes, StandardCharsets.UTF_8)
            val backupPackage = moshi.adapter(EncryptedPosBackupPackage::class.java).fromJson(jsonString)
                ?: return@withContext ImportBackupResult(false, "Error al decodificar la estructura JSON de la copia.")

            // 5. Apply to database
            if (!appendMode && !cloudSyncRestore) {
                database.productDao().deleteAllProducts()
            }

            val existingProducts = database.productDao().getAllProducts().first()

            // Products: Upsert / Insert (avoid doubling stock during cloud restore)
            for (p in backupPackage.products) {
                val existing = if (p.code.isNotBlank()) {
                    database.productDao().getProductByCode(p.code)
                } else {
                    existingProducts.find { it.name.equals(p.name, ignoreCase = true) }
                }
                if (existing != null) {
                    if (appendMode && !cloudSyncRestore) {
                        val mergedStock = existing.stock + p.stock
                        database.productDao().updateProduct(existing.copy(stock = mergedStock))
                    } else {
                        database.productDao().updateProduct(p.copy(id = existing.id))
                    }
                } else {
                    database.productDao().insertProduct(p.copy(id = 0))
                }
            }

            // Customers
            for (c in backupPackage.customers) {
                val existing = if (c.documentId.isNotBlank()) database.customerDao().getCustomerByDocument(c.documentId) else null
                if (existing == null) {
                    database.customerDao().insertCustomer(c.copy(id = 0))
                }
            }

            // Suppliers (deduplicate by documentId or name)
            val existingSuppliers = database.supplierDao().getAllSuppliers().first()
            for (s in backupPackage.suppliers) {
                val alreadyExists = existingSuppliers.any {
                    (s.documentId.isNotBlank() && it.documentId.equals(s.documentId, ignoreCase = true)) ||
                        it.name.equals(s.name, ignoreCase = true)
                }
                if (!alreadyExists) {
                    database.supplierDao().insertSupplier(s.copy(id = 0))
                }
            }

            // Sales & Sale Items (deduplicate by timestamp + totalUsd)
            val existingSales = database.saleDao().getAllSales().first()
            for (s in backupPackage.sales) {
                val alreadyExists = existingSales.any {
                    it.timestamp == s.timestamp && kotlin.math.abs(it.totalUsd - s.totalUsd) < 0.001
                }
                if (!alreadyExists) {
                    val newSaleId = database.saleDao().insertSale(s.copy(id = 0))
                    val relatedItems = backupPackage.saleItems.filter { it.saleId == s.id }
                    if (relatedItems.isNotEmpty()) {
                        val itemsToInsert = relatedItems.map { it.copy(id = 0, saleId = newSaleId.toInt()) }
                        database.saleDao().insertSaleItems(itemsToInsert)
                    }
                }
            }

            // Purchase Invoices (deduplicate by invoiceNumber + timestamp)
            val existingInvoices = database.purchaseInvoiceDao().getAllInvoices().first()
            for (inv in backupPackage.purchaseInvoices) {
                val alreadyExists = existingInvoices.any {
                    it.invoiceNumber.equals(inv.invoiceNumber, ignoreCase = true) && it.timestamp == inv.timestamp
                }
                if (!alreadyExists) {
                    database.purchaseInvoiceDao().insertInvoice(inv.copy(id = 0))
                }
            }

            // Delivery Notes (deduplicate by noteNumber + timestamp)
            val existingNotes = database.deliveryNoteDao().getAllDeliveryNotes().first()
            for (dn in backupPackage.deliveryNotes) {
                val alreadyExists = existingNotes.any {
                    it.noteNumber.equals(dn.noteNumber, ignoreCase = true) && it.timestamp == dn.timestamp
                }
                if (!alreadyExists) {
                    val newDnId = database.deliveryNoteDao().insertDeliveryNote(dn.copy(id = 0))
                    val relatedItems = backupPackage.deliveryNoteItems.filter { it.deliveryNoteId == dn.id }
                    if (relatedItems.isNotEmpty()) {
                        val itemsToInsert = relatedItems.map { it.copy(id = 0, deliveryNoteId = newDnId.toInt()) }
                        database.deliveryNoteDao().insertDeliveryNoteItems(itemsToInsert)
                    }
                }
            }

            // Expenses (deduplicate by timestamp + amountUsd + description)
            val existingExpenses = database.expenseDao().getAllExpenses().first()
            for (exp in backupPackage.expenses) {
                val alreadyExists = existingExpenses.any {
                    it.timestamp == exp.timestamp &&
                        kotlin.math.abs(it.amountUsd - exp.amountUsd) < 0.001 &&
                        it.description == exp.description
                }
                if (!alreadyExists) {
                    database.expenseDao().insertExpense(exp.copy(id = 0))
                }
            }

            // Quotes & Quote Items (deduplicate by quoteNumber + timestamp)
            val existingQuotes = database.quoteDao().getAllQuotes().first()
            for (q in backupPackage.quotes) {
                val alreadyExists = existingQuotes.any {
                    it.quoteNumber.equals(q.quoteNumber, ignoreCase = true) && it.timestamp == q.timestamp
                }
                if (!alreadyExists) {
                    val newQuoteId = database.quoteDao().insertQuote(q.copy(id = 0))
                    val relatedItems = backupPackage.quoteItems.filter { it.quoteId == q.id }
                    if (relatedItems.isNotEmpty()) {
                        val itemsToInsert = relatedItems.map { it.copy(id = 0, quoteId = newQuoteId.toInt()) }
                        database.quoteDao().insertQuoteItems(itemsToInsert)
                    }
                }
            }

            // Cashea Settlements (deduplicate by settlementDate + grossAmountUsd + commissionPercentage)
            val existingCashea = database.casheaSettlementDao().getAllSettlements().first()
            for (cs in backupPackage.casheaSettlements) {
                val alreadyExists = existingCashea.any {
                    it.settlementDate == cs.settlementDate &&
                        kotlin.math.abs(it.grossAmountUsd - cs.grossAmountUsd) < 0.001 &&
                        kotlin.math.abs(it.commissionPercentage - cs.commissionPercentage) < 0.001
                }
                if (!alreadyExists) {
                    database.casheaSettlementDao().insertSettlement(cs.copy(id = 0))
                }
            }

            // Users (Preserve Olivtech/default; insert missing or update existing during cloud restore)
            for (u in backupPackage.users) {
                if (u.username.equals("Olivtech", ignoreCase = true) || u.username.equals("default", ignoreCase = true)) {
                    continue
                }
                val existing = database.userDao().getUserByUsername(u.username)
                if (existing == null) {
                    database.userDao().insertUser(u.copy(id = 0))
                } else if (cloudSyncRestore && !appendMode) {
                    database.userDao().updateUser(u.copy(id = existing.id))
                }
            }

            // Restore company profile preferences when present in cloud backup
            if (prefs != null && backupPackage.storeName.isNotBlank() && !backupPackage.storeName.equals("Mi Tienda", ignoreCase = true)) {
                prefs.storeName = backupPackage.storeName
                if (backupPackage.storeRif.isNotBlank() && !backupPackage.storeRif.equals("J-00000000-0", ignoreCase = true)) {
                    prefs.storeRif = backupPackage.storeRif
                }
                if (backupPackage.storePhone.isNotBlank() && backupPackage.storePhone != "0000-0000000") {
                    prefs.storePhone = backupPackage.storePhone
                }
                if (backupPackage.storeAddress.isNotBlank() && backupPackage.storeAddress != "Dirección") {
                    prefs.storeAddress = backupPackage.storeAddress
                }
                if (backupPackage.bcvRate > 0.0) {
                    prefs.lastBcvRate = backupPackage.bcvRate.toFloat()
                }
                if (backupPackage.isCompanyRegistered) {
                    prefs.isCompanyRegistered = true
                    if (backupPackage.companyRegisteredAt > 0L) {
                        prefs.companyRegisteredAt = backupPackage.companyRegisteredAt
                    }
                }
            }

            ImportBackupResult(
                success = true,
                message = "Respaldo desencriptado y sincronizado: ${backupPackage.products.size} productos, ${backupPackage.sales.size} ventas, ${backupPackage.customers.size} clientes y ${backupPackage.purchaseInvoices.size} facturas.",
                productsCount = backupPackage.products.size,
                customersCount = backupPackage.customers.size,
                salesCount = backupPackage.sales.size,
                invoicesCount = backupPackage.purchaseInvoices.size,
                deliveryNotesCount = backupPackage.deliveryNotes.size,
                expensesCount = backupPackage.expenses.size
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ImportBackupResult(false, "Fallo durante la recuperación del respaldo: ${e.message}")
        }
    }

    /**
     * Reads, verifies the cryptographic authenticity, decrypts, and optionally appends/merges
     * or replaces data into the local Room database from a local file URI.
     */
    suspend fun importAndProcessEncryptedUri(
        uri: Uri,
        appendMode: Boolean = true,
        customPassphrase: String? = null
    ): ImportBackupResult = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportBackupResult(false, "No se pudo abrir el archivo seleccionado.")
            val fileBytes = inputStream.use { it.readBytes() }
            importAndProcessEncryptedBytes(
                fileBytes = fileBytes,
                appendMode = appendMode,
                customPassphrase = customPassphrase,
                cloudSyncRestore = false,
                prefs = null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ImportBackupResult(false, "Fallo durante la importación: ${e.message}")
        }
    }
}
