package com.example.utils

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Representa una fila procesada y validada exitosamente desde el archivo CSV.
 */
data class ParsedCsvProductRow(
    val rowNumber: Int,
    val productName: String,
    val quantity: Int,
    val costPrice: Double,
    val salePrice: Double,
    val unitMargin: Double,        // margen_unitario = precio_venta - precio_costo
    val totalCostValue: Double,    // valor_total_costo = cantidad * precio_costo
    val totalSaleValue: Double,    // valor_total_venta = cantidad * precio_venta
    val code: String = "",
    val category: String = "General",
    val wholesalePrice: Double = 0.0
)

/**
 * Representa una advertencia o fila omitida por fallo de validación.
 */
data class CsvRowWarning(
    val rowNumber: Int,
    val rawLine: String,
    val reason: String
)

/**
 * Resultado completo del parseo y validación de un archivo CSV de inventario.
 */
data class CsvParseResult(
    val fileName: String,
    val validRows: List<ParsedCsvProductRow>,
    val warnings: List<CsvRowWarning>,
    val totalQuantity: Int,
    val totalCostValue: Double,
    val totalSaleValue: Double,
    val totalMargin: Double
)

/**
 * Utilidad modular para procesar, limpiar y validar archivos CSV de inventario.
 */
object CsvInventoryParser {

    /**
     * Parsea y valida el contenido de un InputStream correspondiente a un archivo CSV.
     */
    fun parse(inputStream: InputStream, fileName: String): CsvParseResult {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val lines = mutableListOf<String>()

        reader.useLines { sequence ->
            sequence.forEach { line ->
                // Omitir líneas completamente en blanco
                if (line.trim().isNotEmpty()) {
                    lines.add(line)
                }
            }
        }

        if (lines.isEmpty()) {
            return CsvParseResult(
                fileName = fileName,
                validRows = emptyList(),
                warnings = listOf(CsvRowWarning(1, "", "El archivo CSV está completamente vacío")),
                totalQuantity = 0,
                totalCostValue = 0.0,
                totalSaleValue = 0.0,
                totalMargin = 0.0
            )
        }

        // Limpiar posible BOM en la primera línea
        val firstLine = lines[0].replace("\uFEFF", "").trim()
        val delimiter = detectDelimiter(firstLine)
        val headers = splitCsvLine(firstLine, delimiter).map { normalizeHeader(it) }

        // Identificar índices de las columnas requeridas y opcionales
        var colProduct = -1
        var colQuantity = -1
        var colCostPrice = -1
        var colSalePrice = -1
        var colCode = -1
        var colCategory = -1
        var colWholesale = -1

        headers.forEachIndexed { index, header ->
            when {
                matchesProductHeader(header) && colProduct == -1 -> colProduct = index
                matchesQuantityHeader(header) && colQuantity == -1 -> colQuantity = index
                matchesCostPriceHeader(header) && colCostPrice == -1 -> colCostPrice = index
                matchesSalePriceHeader(header) && colSalePrice == -1 -> colSalePrice = index
                matchesCodeHeader(header) && colCode == -1 -> colCode = index
                matchesCategoryHeader(header) && colCategory == -1 -> colCategory = index
                matchesWholesaleHeader(header) && colWholesale == -1 -> colWholesale = index
            }
        }

        // Si faltan encabezados clave, advertir
        val missingHeaders = mutableListOf<String>()
        if (colProduct == -1) missingHeaders.add("producto")
        if (colQuantity == -1) missingHeaders.add("cantidad")
        if (colCostPrice == -1) missingHeaders.add("precio_costo")
        if (colSalePrice == -1) missingHeaders.add("precio_venta")

        if (missingHeaders.isNotEmpty()) {
            return CsvParseResult(
                fileName = fileName,
                validRows = emptyList(),
                warnings = listOf(
                    CsvRowWarning(
                        rowNumber = 1,
                        rawLine = firstLine,
                        reason = "Faltan columnas requeridas en el encabezado: ${missingHeaders.joinToString(", ")}. Encabezados detectados: [${headers.joinToString(", ")}]"
                    )
                ),
                totalQuantity = 0,
                totalCostValue = 0.0,
                totalSaleValue = 0.0,
                totalMargin = 0.0
            )
        }

        val validRows = mutableListOf<ParsedCsvProductRow>()
        val warnings = mutableListOf<CsvRowWarning>()

        // Procesar fila por fila a partir de la línea 2
        for (i in 1 until lines.size) {
            val rowNum = i + 1
            val rawLine = lines[i]
            val tokens = splitCsvLine(rawLine, delimiter)

            val rawProduct = tokens.getOrNull(colProduct)?.trim().orEmpty()
            val rawQuantity = tokens.getOrNull(colQuantity)?.trim().orEmpty()
            val rawCostPrice = tokens.getOrNull(colCostPrice)?.trim().orEmpty()
            val rawSalePrice = tokens.getOrNull(colSalePrice)?.trim().orEmpty()
            val rawCode = if (colCode != -1) tokens.getOrNull(colCode)?.trim().orEmpty() else ""
            val rawCategory = if (colCategory != -1) tokens.getOrNull(colCategory)?.trim().orEmpty() else "General"
            val rawWholesale = if (colWholesale != -1) tokens.getOrNull(colWholesale)?.trim().orEmpty() else ""

            // 1. Validar nombre del producto
            if (rawProduct.isBlank()) {
                warnings.add(CsvRowWarning(rowNum, rawLine, "Falta el nombre del producto"))
                continue
            }

            // 2. Validar cantidad (entero >= 0)
            val parsedQuantity = parseCleanInteger(rawQuantity)
            if (parsedQuantity == null || parsedQuantity < 0) {
                warnings.add(
                    CsvRowWarning(
                        rowNum,
                        rawLine,
                        "Cantidad inválida o negativa: '$rawQuantity' (debe ser un número entero mayor o igual a 0)"
                    )
                )
                continue
            }

            // 3. Validar precio de costo (decimal >= 0 con 2 decimales)
            val parsedCostPrice = parseCleanDecimal(rawCostPrice)
            if (parsedCostPrice == null || parsedCostPrice < 0.0) {
                warnings.add(
                    CsvRowWarning(
                        rowNum,
                        rawLine,
                        "Precio de costo inválido: '$rawCostPrice' (debe ser un valor numérico >= 0)"
                    )
                )
                continue
            }

            // 4. Validar precio de venta (decimal >= 0 con 2 decimales)
            val parsedSalePrice = parseCleanDecimal(rawSalePrice)
            if (parsedSalePrice == null || parsedSalePrice < 0.0) {
                warnings.add(
                    CsvRowWarning(
                        rowNum,
                        rawLine,
                        "Precio de venta inválido: '$rawSalePrice' (debe ser un valor numérico >= 0)"
                    )
                )
                continue
            }

            val parsedWholesalePrice = parseCleanDecimal(rawWholesale) ?: 0.0

            // Cálculos automáticos requeridos por fila
            val unitMargin = round2Decimals(parsedSalePrice - parsedCostPrice)
            val totalCostValue = round2Decimals(parsedQuantity * parsedCostPrice)
            val totalSaleValue = round2Decimals(parsedQuantity * parsedSalePrice)

            validRows.add(
                ParsedCsvProductRow(
                    rowNumber = rowNum,
                    productName = rawProduct,
                    quantity = parsedQuantity,
                    costPrice = parsedCostPrice,
                    salePrice = parsedSalePrice,
                    unitMargin = unitMargin,
                    totalCostValue = totalCostValue,
                    totalSaleValue = totalSaleValue,
                    code = rawCode,
                    category = if (rawCategory.isNotBlank()) rawCategory else "General",
                    wholesalePrice = parsedWholesalePrice
                )
            )
        }

        var totalQty = 0
        var sumCost = 0.0
        var sumSale = 0.0

        for (row in validRows) {
            totalQty += row.quantity
            sumCost += row.totalCostValue
            sumSale += row.totalSaleValue
        }

        return CsvParseResult(
            fileName = fileName,
            validRows = validRows,
            warnings = warnings,
            totalQuantity = totalQty,
            totalCostValue = round2Decimals(sumCost),
            totalSaleValue = round2Decimals(sumSale),
            totalMargin = round2Decimals(sumSale - sumCost)
        )
    }

    /**
     * Detecta si el archivo utiliza coma, punto y coma o tabulación como delimitador.
     */
    private fun detectDelimiter(line: String): Char {
        val countSemicolon = line.count { it == ';' }
        val countComma = line.count { it == ',' }
        val countTab = line.count { it == '\t' }

        return when {
            countSemicolon > countComma && countSemicolon > countTab -> ';'
            countTab > countComma && countTab > countSemicolon -> '\t'
            else -> ','
        }
    }

    /**
     * Divide una línea CSV respetando celdas entre comillas.
     */
    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    // Doble comilla escapada dentro de comillas
                    current.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == delimiter && !inQuotes) {
                result.add(current.toString())
                current.clear()
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    /**
     * Normaliza un encabezado para comparación insensible a mayúsculas, tildes y separadores.
     */
    private fun normalizeHeader(text: String): String {
        val stripped = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return stripped.lowercase(Locale.ROOT)
            .replace("_", " ")
            .replace("-", " ")
            .replace(".", "")
            .replace(":", "")
            .trim()
    }

    private fun matchesProductHeader(h: String): Boolean {
        return h in listOf(
            "producto", "item", "nombre", "articulo", "descripcion",
            "name", "product", "detalle", "item name", "nombre producto",
            "descripcion del producto", "titulo", "productos"
        )
    }

    private fun matchesQuantityHeader(h: String): Boolean {
        return h in listOf(
            "cantidad", "stock", "existencia", "existencias", "qty",
            "quantity", "cant", "unidades", "inv", "inventario",
            "stock inicial", "unidades disponibles"
        )
    }

    private fun matchesCostPriceHeader(h: String): Boolean {
        return h in listOf(
            "precio costo", "precio de costo", "preciocosto", "costo",
            "cost", "cost price", "costo unitario", "precio de compra",
            "compra", "p costo", "costo usd", "costousd", "unit cost"
        )
    }

    private fun matchesSalePriceHeader(h: String): Boolean {
        return h in listOf(
            "precio venta", "precio de venta", "precioventa", "precio",
            "pvp", "price", "sale price", "precio unitario", "p venta",
            "precio usd", "preciousd", "valor venta", "unit price"
        )
    }

    private fun matchesCodeHeader(h: String): Boolean {
        return h in listOf(
            "codigo", "code", "sku", "id", "ref", "referencia",
            "barcode", "codigo de barra", "cod"
        )
    }

    private fun matchesCategoryHeader(h: String): Boolean {
        return h in listOf(
            "categoria", "category", "depto", "departamento",
            "seccion", "rubro", "grupo"
        )
    }

    private fun matchesWholesaleHeader(h: String): Boolean {
        return h in listOf(
            "precio mayor", "precio al mayor", "preciomayor",
            "precio mayor usd", "preciomayorusd", "precio al mayor usd",
            "mayor", "wholesale", "precio mayorista", "mayorista"
        )
    }

    /**
     * Limpia caracteres no deseados en números:
     * Elimina símbolos de moneda ($ € Bs USD), espacios, y normaliza comas/puntos decimales.
     */
    fun parseCleanDecimal(input: String): Double? {
        var clean = input.trim()
            .replace("$", "")
            .replace("€", "")
            .replace("USD", "", ignoreCase = true)
            .replace("Bs.", "", ignoreCase = true)
            .replace("Bs", "", ignoreCase = true)
            .replace(" ", "")
            .replace("\u00A0", "") // Non-breaking space

        if (clean.isBlank()) return null

        val isNegative = clean.startsWith("-")
        if (isNegative) {
            clean = clean.removePrefix("-")
        }

        // Manejo de separadores de miles y decimales
        val hasComma = clean.contains(',')
        val hasDot = clean.contains('.')

        val normalized = when {
            // Caso "1.250,50" -> miles punto, decimal coma
            hasDot && hasComma && clean.lastIndexOf(',') > clean.lastIndexOf('.') -> {
                clean.replace(".", "").replace(',', '.')
            }
            // Caso "1,250.50" -> miles coma, decimal punto
            hasDot && hasComma && clean.lastIndexOf('.') > clean.lastIndexOf(',') -> {
                clean.replace(",", "")
            }
            // Caso solo coma "15,50" -> decimal coma
            hasComma && !hasDot -> {
                clean.replace(',', '.')
            }
            else -> clean
        }

        val parsed = normalized.toDoubleOrNull() ?: return null
        val finalVal = if (isNegative) -parsed else parsed
        return round2Decimals(finalVal)
    }

    /**
     * Limpia y parsea un entero mayor o igual a 0.
     */
    fun parseCleanInteger(input: String): Int? {
        val dec = parseCleanDecimal(input) ?: return null
        if (dec < 0) return -1
        return dec.roundToInt()
    }

    private fun round2Decimals(value: Double): Double {
        return (value * 100.0).roundToInt() / 100.0
    }
}
