package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Product::class,
        Customer::class,
        Sale::class,
        SaleItem::class,
        Supplier::class,
        PurchaseInvoice::class,
        User::class,
        HeldSale::class,
        HeldSaleItem::class,
        DeliveryNote::class,
        DeliveryNoteItem::class,
        Expense::class,
        Quote::class,
        QuoteItem::class,
        CasheaSettlement::class,
        PosTerminalRecord::class
    ],
    version = 28,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun customerDao(): CustomerDao
    abstract fun saleDao(): SaleDao
    abstract fun supplierDao(): SupplierDao
    abstract fun purchaseInvoiceDao(): PurchaseInvoiceDao
    abstract fun userDao(): UserDao
    abstract fun heldSaleDao(): HeldSaleDao
    abstract fun deliveryNoteDao(): DeliveryNoteDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun quoteDao(): QuoteDao
    abstract fun casheaSettlementDao(): CasheaSettlementDao
    abstract fun posTerminalRecordDao(): PosTerminalRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cashea_settlements` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `settlementDate` INTEGER NOT NULL,
                        `grossAmountUsd` REAL NOT NULL,
                        `commissionPercentage` REAL NOT NULL,
                        `commissionAmountUsd` REAL NOT NULL,
                        `netReceivedUsd` REAL NOT NULL,
                        `rateBcv` REAL NOT NULL,
                        `notes` TEXT NOT NULL,
                        `registeredBy` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `delivery_notes` ADD COLUMN `displayCurrency` TEXT NOT NULL DEFAULT 'USD'"
                )
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pos_terminal_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transactionDateTime` TEXT NOT NULL,
                        `transactionTimestamp` INTEGER NOT NULL,
                        `receiptNumber` TEXT NOT NULL,
                        `referenceNumber` TEXT NOT NULL,
                        `approvalNumber` TEXT NOT NULL,
                        `maskedCardNumber` TEXT NOT NULL,
                        `operationType` TEXT NOT NULL,
                        `totalAmount` REAL NOT NULL,
                        `currency` TEXT NOT NULL,
                        `transactionStatus` TEXT NOT NULL,
                        `bankOrTerminal` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `rawOcrText` TEXT NOT NULL,
                        `registeredBy` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `users` ADD COLUMN `allowedModules` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `products` ADD COLUMN `minStockAlertQuantity` REAL NOT NULL DEFAULT 5.0"
                )
                db.execSQL(
                    "ALTER TABLE `products` ADD COLUMN `isLowStockAlertEnabled` INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_database"
                )
                .addMigrations(MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
