package com.example.utils

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.local.Customer
import com.example.data.local.Product
import com.example.data.local.Sale
import com.example.data.local.SaleItem
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.io.InputStreamReader

@JsonClass(generateAdapter = true)
data class BackupData(
    val products: List<Product>,
    val customers: List<Customer>,
    val sales: List<Sale>,
    val saleItems: List<SaleItem>
)

class BackupManager(private val context: Context, private val database: AppDatabase) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    suspend fun exportToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val products = database.productDao().getAllProducts().first()
            val customers = database.customerDao().getAllCustomers().first()
            val sales = database.saleDao().getAllSales().first()
            
            val saleItems = mutableListOf<SaleItem>()
            for (sale in sales) {
                saleItems.addAll(database.saleDao().getItemsForSale(sale.id).first())
            }

            val backup = BackupData(products, customers, sales, saleItems)
            val json = moshi.adapter(BackupData::class.java).toJson(backup)

            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { writer ->
                    writer.write(json)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            var json = ""
            context.contentResolver.openInputStream(uri)?.use { input ->
                InputStreamReader(input).use { reader ->
                    json = reader.readText()
                }
            }

            val backup = moshi.adapter(BackupData::class.java).fromJson(json) ?: return@withContext false

            for (p in backup.products) database.productDao().insertProduct(p)
            for (c in backup.customers) database.customerDao().insertCustomer(c)
            for (s in backup.sales) {
                // To preserve IDs properly, we would need a more complex merge.
                // For simplicity, we just insert.
                database.saleDao().insertSale(s)
            }
            // Insert saleItems
            database.saleDao().insertSaleItems(backup.saleItems)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
