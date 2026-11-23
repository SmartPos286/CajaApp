package com.example.utils

import com.example.data.local.Product
import com.example.data.local.Supplier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

enum class InvoiceCurrency {
    USD,
    VES
}

data class IvaDetectionResult(
    val hasIva: Boolean = false,
    val ivaPercent: Double = 16.0,
    val ivaAmount: Double = 0.0,
    val subtotalAmount: Double = 0.0
)

data class ExtractedInvoiceItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Double,
    val unitCostUsd: Double,
    val lineTotalUsd: Double,
    val invoiceUnitCost: Double = unitCostUsd,
    val invoiceLineTotal: Double = lineTotalUsd,
    val isTaxExempt: Boolean = false,
    val salePriceUsd: Double = 0.0,
    val matchedProductId: Int? = null,
    val matchedProductName: String? = null,
    val existingStock: Double = 0.0,
    val existingCostUsd: Double = 0.0,
    val existingPriceUsd: Double = 0.0
) {
    val isExistingProduct: Boolean
        get() = matchedProductId != null

    val resolvedCostUsd: Double
        get() = PurchaseInvoiceOcrParser.computeUpdatedCost(
            existingCostUsd = existingCostUsd,
            incomingUnitCostUsd = unitCostUsd,
            isExisting = isExistingProduct
        )

    val willRaiseCost: Boolean
        get() = isExistingProduct && unitCostUsd > existingCostUsd + 0.0001

    val costKeptBecauseLowerOrEqual: Boolean
        get() = isExistingProduct && unitCostUsd > 0.0 && unitCostUsd <= existingCostUsd + 0.0001

    fun effectiveUnitCostUsd(
        applyIva: Boolean,
        ivaPercent: Double,
        includeIvaInCost: Boolean
    ): Double {
        val base = unitCostUsd.coerceAtLeast(0.0)
        return if (applyIva && includeIvaInCost && !isTaxExempt && ivaPercent > 0.0) {
            ((base * (1.0 + ivaPercent / 100.0)) * 100.0).roundToInt() / 100.0
        } else {
            base
        }
    }
}

data class ParsedInvoiceOcrResult(
    val supplierName: String,
    val invoiceNumber: String,
    val totalAmountUsd: Double,
    val items: List<ExtractedInvoiceItem>,
    val rawText: String,
    val usedGeminiVision: Boolean = false,
    val detectedCurrency: InvoiceCurrency = InvoiceCurrency.USD,
    val hasDetectedIva: Boolean = false,
    val detectedIvaPercent: Double = 16.0,
    val detectedIvaAmount: Double = 0.0,
    val detectedSubtotalAmount: Double = 0.0
)

object PurchaseInvoiceOcrParser {

    fun normalizeText(input: String): String {
        val noAccents = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return noAccents
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Regla estricta de costo:
     * Si el producto ya existe, SOLO modificar el precio de costo si el nuevo costo es MAYOR al actual.
     * Nunca disminuir el costo existente si el costo entrante es menor o igual.
     */
    fun computeUpdatedCost(
        existingCostUsd: Double,
        incomingUnitCostUsd: Double,
        isExisting: Boolean
    ): Double {
        val safeIncoming = incomingUnitCostUsd.coerceAtLeast(0.0)
        if (!isExisting) return safeIncoming
        return if (safeIncoming > existingCostUsd) safeIncoming else existingCostUsd
    }

    fun findBestMatchingProduct(name: String, existingProducts: List<Product>): Product? {
        val targetNorm = normalizeText(name)
        if (targetNorm.isBlank()) return null

        // 1. Coincidencia exacta normalizada
        existingProducts.firstOrNull { normalizeText(it.name) == targetNorm }?.let { return it }

        // 2. Coincidencia por código SKU si viene incluido en el nombre extraído
        existingProducts.firstOrNull { prod ->
            val codeNorm = normalizeText(prod.code)
            codeNorm.length >= 3 && (targetNorm == codeNorm || targetNorm.split(" ").contains(codeNorm))
        }?.let { return it }

        // 3. Coincidencia por mismos tokens (tolerando orden distinto o plural simple)
        val targetTokens = canonicalTokens(targetNorm)
        if (targetTokens.isEmpty()) return null

        existingProducts.firstOrNull { prod ->
            val prodTokens = canonicalTokens(normalizeText(prod.name))
            prodTokens.isNotEmpty() && prodTokens == targetTokens
        }?.let { return it }

        return null
    }

    private fun canonicalTokens(normalized: String): Set<String> {
        return normalized.split(" ")
            .map { token ->
                if (token.length > 4 && token.endsWith("s") && !token.endsWith("ss")) {
                    token.dropLast(1)
                } else {
                    token
                }
            }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun linkWithExistingInventory(
        items: List<ExtractedInvoiceItem>,
        existingProducts: List<Product>
    ): List<ExtractedInvoiceItem> {
        return items.map { item ->
            val matched = findBestMatchingProduct(item.name, existingProducts)
            if (matched != null) {
                item.copy(
                    matchedProductId = matched.id,
                    matchedProductName = matched.name,
                    existingStock = matched.stock,
                    existingCostUsd = matched.costPriceUsd,
                    existingPriceUsd = matched.priceUsd,
                    isTaxExempt = item.isTaxExempt || matched.isTaxExempt,
                    salePriceUsd = if (item.salePriceUsd > 0.0) item.salePriceUsd else matched.priceUsd
                )
            } else {
                item.copy(
                    matchedProductId = null,
                    matchedProductName = null,
                    existingStock = 0.0,
                    existingCostUsd = 0.0,
                    existingPriceUsd = 0.0
                )
            }
        }
    }

    fun parseFromGeminiJson(
        jsonText: String,
        rawOcrText: String,
        existingProducts: List<Product>,
        existingSuppliers: List<Supplier>
    ): ParsedInvoiceOcrResult? {
        return try {
            val cleaned = jsonText
                .trim()
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val startIdx = cleaned.indexOf('{')
            val endIdx = cleaned.lastIndexOf('}')
            if (startIdx == -1 || endIdx <= startIdx) return null
            val obj = Json.parseToJsonElement(cleaned.substring(startIdx, endIdx + 1)).jsonObject

            val supplierRaw = obj["supplierName"]?.jsonPrimitive?.content?.trim().orEmpty()
            val resolvedSupplier = matchSupplierName(supplierRaw, existingSuppliers)
                .ifBlank { extractSupplierName(rawOcrText, existingSuppliers) }

            val invoiceNumber = obj["invoiceNumber"]?.jsonPrimitive?.content?.trim().orEmpty()
                .ifBlank { extractInvoiceNumber(rawOcrText) }

            val currencyStr = obj["currency"]?.jsonPrimitive?.content?.trim().orEmpty().uppercase(Locale.US)
            val detectedCurrency = when {
                currencyStr.contains("VES") || currencyStr.contains("BS") || currencyStr.contains("BOLIVAR") -> InvoiceCurrency.VES
                currencyStr.contains("USD") || currencyStr == "$" -> InvoiceCurrency.USD
                else -> detectCurrency(rawOcrText)
            }

            val ocrIva = extractIvaInfo(rawOcrText)
            val hasIva = obj["hasIva"]?.jsonPrimitive?.booleanOrNull ?: ocrIva.hasIva
            val ivaPercent = (obj["ivaPercentage"]?.jsonPrimitive?.doubleOrNull ?: ocrIva.ivaPercent)
                .let { if (it.isNaN() || it <= 0.0) 16.0 else it }
            val ivaAmount = (obj["ivaAmount"]?.jsonPrimitive?.doubleOrNull ?: ocrIva.ivaAmount)
                .let { if (it.isNaN() || it < 0.0) 0.0 else it }
            val subtotalAmount = (obj["subtotalAmount"]?.jsonPrimitive?.doubleOrNull ?: ocrIva.subtotalAmount)
                .let { if (it.isNaN() || it < 0.0) 0.0 else it }

            val rawItems = mutableListOf<ExtractedInvoiceItem>()
            val itemsArray = obj["items"]?.jsonArray
            if (itemsArray != null) {
                for (element in itemsArray) {
                    val itemObj = runCatching { element.jsonObject }.getOrNull() ?: continue
                    val rawName = itemObj["name"]?.jsonPrimitive?.content?.trim().orEmpty()
                    if (rawName.isBlank()) continue
                    val (cleanName, markerExempt) = stripExemptMarker(rawName)
                    val isExempt = itemObj["isTaxExempt"]?.jsonPrimitive?.booleanOrNull ?: markerExempt

                    var qty = (itemObj["quantity"]?.jsonPrimitive?.doubleOrNull ?: 1.0)
                        .let { if (it.isNaN() || it <= 0.0) 1.0 else it }
                    var unitCost = (itemObj["unitCostUsd"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                        .let { if (it.isNaN() || it < 0.0) 0.0 else it }
                    var lineTotal = (itemObj["lineTotalUsd"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                        .let { if (it.isNaN() || it < 0.0) 0.0 else it }

                    if (unitCost <= 0.0 && lineTotal > 0.0) {
                        unitCost = round2(lineTotal / qty)
                    } else if (lineTotal <= 0.0 && unitCost > 0.0) {
                        lineTotal = round2(qty * unitCost)
                    } else if (qty <= 0.0 && unitCost > 0.0 && lineTotal > 0.0) {
                        qty = round2(lineTotal / unitCost)
                    }

                    rawItems.add(
                        ExtractedInvoiceItem(
                            name = cleanName,
                            quantity = qty,
                            unitCostUsd = unitCost,
                            lineTotalUsd = lineTotal,
                            invoiceUnitCost = unitCost,
                            invoiceLineTotal = lineTotal,
                            isTaxExempt = isExempt
                        )
                    )
                }
            }

            val finalItems = if (rawItems.isNotEmpty()) {
                rawItems
            } else {
                extractLineItems(rawOcrText)
            }

            val linkedItems = linkWithExistingInventory(finalItems, existingProducts)
            val jsonTotal = obj["totalAmountUsd"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val parsedTotal = if (jsonTotal.isNaN() || jsonTotal <= 0.0) {
                val ocrTotal = extractTotalAmount(rawOcrText)
                if (ocrTotal > 0.0) ocrTotal else linkedItems.sumOf { item -> item.invoiceLineTotal }
            } else {
                jsonTotal
            }

            ParsedInvoiceOcrResult(
                supplierName = resolvedSupplier,
                invoiceNumber = invoiceNumber,
                totalAmountUsd = round2(parsedTotal),
                items = linkedItems,
                rawText = rawOcrText,
                usedGeminiVision = true,
                detectedCurrency = detectedCurrency,
                hasDetectedIva = hasIva || ivaAmount > 0.0,
                detectedIvaPercent = ivaPercent,
                detectedIvaAmount = round2(ivaAmount),
                detectedSubtotalAmount = round2(subtotalAmount)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun parseFromRawText(
        rawText: String,
        existingProducts: List<Product>,
        existingSuppliers: List<Supplier>
    ): ParsedInvoiceOcrResult {
        val detectedCurrency = detectCurrency(rawText)
        val ivaInfo = extractIvaInfo(rawText)
        val supplier = extractSupplierName(rawText, existingSuppliers)
        val invoiceNumber = extractInvoiceNumber(rawText)
        val extractedItems = extractLineItems(rawText)
        val linkedItems = linkWithExistingInventory(extractedItems, existingProducts)
        val detectedTotal = extractTotalAmount(rawText)
        val itemsSum = linkedItems.sumOf { it.invoiceLineTotal }
        val finalTotal = if (detectedTotal > 0.0) detectedTotal else (itemsSum + ivaInfo.ivaAmount)

        return ParsedInvoiceOcrResult(
            supplierName = supplier,
            invoiceNumber = invoiceNumber,
            totalAmountUsd = round2(finalTotal),
            items = linkedItems,
            rawText = rawText,
            usedGeminiVision = false,
            detectedCurrency = detectedCurrency,
            hasDetectedIva = ivaInfo.hasIva,
            detectedIvaPercent = ivaInfo.ivaPercent,
            detectedIvaAmount = round2(ivaInfo.ivaAmount),
            detectedSubtotalAmount = round2(if (ivaInfo.subtotalAmount > 0.0) ivaInfo.subtotalAmount else itemsSum)
        )
    }

    fun detectCurrency(rawText: String): InvoiceCurrency {
        if (rawText.isBlank()) return InvoiceCurrency.USD
        val vesMatches = Regex("""(?:\bBs\.?S?\b|\bVES\b|\bBol[íi]var(?:es)?\b)""", RegexOption.IGNORE_CASE)
            .findAll(rawText).count()
        val usdMatches = Regex("""(?:\$|\bUSD\b|\bD[óo]lar(?:es)?\b|\bREF\b)""", RegexOption.IGNORE_CASE)
            .findAll(rawText).count()
        return if (vesMatches > usdMatches) InvoiceCurrency.VES else InvoiceCurrency.USD
    }

    fun extractIvaInfo(rawText: String): IvaDetectionResult {
        if (rawText.isBlank()) return IvaDetectionResult()
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }

        var hasIva = false
        var ivaPercent = 16.0
        var ivaAmount = 0.0
        var subtotalAmount = 0.0

        val subtotalRegex = Regex("""(?:SUB[\s\-]?TOTAL|BASE\s+IMPONIBLE)\b.*$""", RegexOption.IGNORE_CASE)
        val ivaLineRegex = Regex("""\bI\.?V\.?A\.?\b(?:\s*\(?\s*(\d{1,2}(?:[.,]\d+)?)\s*%\s*\)?)?""", RegexOption.IGNORE_CASE)

        for (line in lines) {
            if (subtotalRegex.containsMatchIn(line)) {
                val nums = extractNumbersInString(line)
                if (nums.isNotEmpty() && subtotalAmount <= 0.0) {
                    subtotalAmount = nums.last()
                }
            }
            val ivaMatch = ivaLineRegex.find(line)
            if (ivaMatch != null && !line.contains("RETENCION", ignoreCase = true)) {
                val pctStr = ivaMatch.groupValues.getOrNull(1).orEmpty()
                val parsedPct = if (pctStr.isNotBlank()) parseFlexNumber(pctStr) else null
                if (parsedPct != null && parsedPct in 1.0..35.0) {
                    ivaPercent = parsedPct
                    hasIva = true
                }
                val afterMatch = line.substring(ivaMatch.range.last + 1)
                val numsAfter = extractNumbersInString(afterMatch)
                if (numsAfter.isNotEmpty()) {
                    val amt = numsAfter.last()
                    if (amt > 0.0) {
                        ivaAmount = amt
                        hasIva = true
                    }
                }
            }
        }

        return IvaDetectionResult(
            hasIva = hasIva,
            ivaPercent = ivaPercent,
            ivaAmount = ivaAmount,
            subtotalAmount = subtotalAmount
        )
    }

    fun convertInvoiceItemsToUsd(
        items: List<ExtractedInvoiceItem>,
        currency: InvoiceCurrency,
        exchangeRateBs: Double
    ): List<ExtractedInvoiceItem> {
        val safeRate = if (exchangeRateBs > 0.001) exchangeRateBs else 1.0
        return items.map { item ->
            val unitUsd = if (currency == InvoiceCurrency.VES) round2(item.invoiceUnitCost / safeRate) else round2(item.invoiceUnitCost)
            val totalUsd = if (currency == InvoiceCurrency.VES) round2(item.invoiceLineTotal / safeRate) else round2(item.invoiceLineTotal)
            item.copy(
                unitCostUsd = unitUsd,
                lineTotalUsd = totalUsd
            )
        }
    }

    private fun stripExemptMarker(rawName: String): Pair<String, Boolean> {
        val exemptRegex = Regex("""(?:\s*\(\s*E\s*\)|\s+\bEXENTO\b)\s*$""", RegexOption.IGNORE_CASE)
        val isExempt = exemptRegex.containsMatchIn(rawName)
        val cleaned = rawName.replace(exemptRegex, "").trim()
        return cleaned to isExempt
    }

    private fun matchSupplierName(candidate: String, existingSuppliers: List<Supplier>): String {
        val norm = normalizeText(candidate)
        if (norm.isBlank()) return ""
        existingSuppliers.firstOrNull { normalizeText(it.name) == norm }?.let { return it.name }
        existingSuppliers.firstOrNull {
            val sNorm = normalizeText(it.name)
            sNorm.length >= 4 && (norm.contains(sNorm) || sNorm.contains(norm))
        }?.let { return it.name }
        return candidate
    }

    fun extractSupplierName(rawText: String, existingSuppliers: List<Supplier>): String {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val topBlobNorm = normalizeText(lines.take(10).joinToString(" "))

        // 1. Buscar coincidencia directa con proveedores ya registrados
        existingSuppliers.firstOrNull { sup ->
            val sNorm = normalizeText(sup.name)
            sNorm.length >= 4 && topBlobNorm.contains(sNorm)
        }?.let { return it.name }

        // 2. Buscar etiqueta explícita de proveedor / razón social
        val labelRegex = Regex(
            pattern = """^(?:PROVEEDOR|RAZ[ÓO]N\s+SOCIAL|EMPRESA|DISTRIBUIDOR(?:A)?|SUPLIDOR)\s*[:\-]\s*(.+)$""",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        for (line in lines.take(15)) {
            val m = labelRegex.find(line)
            if (m != null) {
                val value = m.groupValues[1].trim()
                if (value.length >= 2) return matchSupplierName(value, existingSuppliers)
            }
        }

        // 3. Tomar primera línea de cabecera representativa del comercio emisor
        val ignoreHeaderRegex = Regex(
            """^(?:FACTURA|NOTA\s+DE\s+ENTREGA|SENIAT|CONTRIBUYENTE|R\.?I\.?F\.?|NIT|FECHA|HORA|TELEFONO|TLF|DIRECCION|CLIENTE|CONTROL|NRO|N[°º]|DOCUMENTO|DESCRIPCION|CANTIDAD|PRECIO|TOTAL).*$""",
            RegexOption.IGNORE_CASE
        )
        for (line in lines.take(6)) {
            val clean = line.replace(Regex("\\s+"), " ").trim()
            if (clean.length >= 3 &&
                !ignoreHeaderRegex.matches(clean) &&
                !clean.matches(Regex("^[0-9\\s/\\-:.]+$")) &&
                clean.any { it.isLetter() }
            ) {
                return clean
            }
        }
        return ""
    }

    fun extractInvoiceNumber(rawText: String): String {
        val patterns = listOf(
            Regex("""(?:N[°º.]?\s*(?:DE\s+)?FACTURA|FACTURA\s*(?:N[°º.]?|NRO\.?|NO\.?|#)?|INV(?:OICE)?\s*#?)\s*[:#№\-]*\s*([A-Z0-9][A-Z0-9\-/]{2,20})""", RegexOption.IGNORE_CASE),
            Regex("""(?:NRO\.?\s*CONTROL|CONTROL\s*(?:N[°º.]?)?|DOCUMENTO\s*(?:N[°º.]?)?)\s*[:#№\-]*\s*([A-Z0-9][A-Z0-9\-/]{2,20})""", RegexOption.IGNORE_CASE),
            Regex("""#\s*([0-9]{3,15}|[A-Z]{1,4}-[0-9]{2,12})""", RegexOption.IGNORE_CASE)
        )
        val excludedWords = setOf("FISCAL", "FECHA", "CLIENT", "CLIENTE", "VENTA", "COMPRA", "SENIAT", "TOTAL")
        for (regex in patterns) {
            for (line in rawText.lines()) {
                val m = regex.find(line)
                if (m != null) {
                    val candidate = m.groupValues[1].trim()
                    if (candidate.uppercase(Locale.US) !in excludedWords && candidate.any { it.isDigit() }) {
                        return candidate
                    }
                }
            }
        }
        return ""
    }

    fun extractTotalAmount(rawText: String): Double {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val totalRegex = Regex("""^(?!.*SUB[\s\-]?TOTAL).*?(?:TOTAL\s+A\s+PAGAR|TOTAL\s+FACTURA|TOTAL\s+GENERAL|MONTO\s+TOTAL|GRAN\s+TOTAL|TOTAL\s+USD|TOTAL\s*\$|\bTOTAL\b).*$""", RegexOption.IGNORE_CASE)
        for (line in lines.asReversed()) {
            if (totalRegex.matches(line)) {
                val nums = extractNumbersInString(line)
                if (nums.isNotEmpty()) {
                    return nums.last()
                }
            }
        }
        return 0.0
    }

    fun extractLineItems(rawText: String): List<ExtractedInvoiceItem> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val results = mutableListOf<ExtractedInvoiceItem>()

        val skipLineRegex = Regex(
            """^(?:SUB[\s\-]?TOTAL|BASE\s+IMPONIBLE|EXENTO|I\.?V\.?A\.?|IMPUESTO|DESCUENTO|RETENCION|TOTAL|MONTO\s+TOTAL|SON\s*:|GRACIAS|R\.?I\.?F\.?|NIT|CEDULA|TELEFONO|TLF|DIRECCION|FECHA|HORA|FACTURA|CONTROL|NRO|CLIENTE|PROVEEDOR|RAZ[ÓO]N\s+SOCIAL|CANT(?:IDAD)?\s+DESC|DESCRIPCI[ÓO]N|PRODUCTO|ART[ÍI]CULO|CAJA|BANCO|PAGO|EFECTIVO|TRANSFERENCIA|OBSERVACIONES).*$""",
            RegexOption.IGNORE_CASE
        )

        var pendingNameLine: String? = null
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (skipLineRegex.matches(line)) {
                pendingNameLine = null
                i++
                continue
            }

            val parsedSingle = parseSingleLineProduct(line)
            if (parsedSingle != null) {
                results.add(parsedSingle)
                pendingNameLine = null
                i++
                continue
            }

            // Soporte para formato en 2 líneas:
            // Línea 1: "Harina PAN 1Kg"
            // Línea 2: "10 x $1.15 = $11.50" o "10  1.15  11.50"
            if (pendingNameLine != null) {
                val qtyPriceMatch = parseQuantityAndPriceLine(line, pendingNameLine)
                if (qtyPriceMatch != null) {
                    results.add(qtyPriceMatch)
                    pendingNameLine = null
                    i++
                    continue
                }
            }

            // Si la línea contiene letras y no termina en monto monetario claro, puede ser el nombre del ítem de la siguiente línea
            val hasLetters = line.count { it.isLetter() } >= 3
            val endsWithPriceTail = Regex("""(?:^|\s)\$?\s*\d+(?:[.,]\d{2})\s*$""").containsMatchIn(line)
            if (hasLetters && !endsWithPriceTail && line.length in 3..70) {
                pendingNameLine = line
            } else {
                pendingNameLine = null
            }
            i++
        }

        return results
    }

    private fun parseQuantityAndPriceLine(line: String, productName: String): ExtractedInvoiceItem? {
        val currencyPrefix = """(?:(?:Bs\.?S?|VES|USD)\s*|\$\s*)?"""
        // Caso: "10 x 1.25 = 12.50" o "10 x $1.25 $12.50" o "10 x Bs. 45,00 = Bs. 450,00"
        val multRegex = Regex(
            """^\s*(\d+(?:[.,]\d+)?)\s*[xX*]\s*$currencyPrefix(\d[\d.,]*)(?:\s*(?:=|\s)\s*$currencyPrefix(\d[\d.,]*))?.*$""",
            RegexOption.IGNORE_CASE
        )
        val m = multRegex.find(line)
        if (m != null) {
            val qty = parseFlexNumber(m.groupValues[1]) ?: return null
            val unit = parseFlexNumber(m.groupValues[2]) ?: return null
            val total = m.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.let { parseFlexNumber(it) }
                ?: round2(qty * unit)
            if (qty > 0.0 && (unit > 0.0 || total > 0.0)) {
                val resolvedUnit = round2(if (unit > 0.0) unit else total / qty)
                val resolvedTotal = round2(total)
                val (cleanName, isExempt) = stripExemptMarker(cleanProductName(productName))
                return ExtractedInvoiceItem(
                    name = cleanName,
                    quantity = qty,
                    unitCostUsd = resolvedUnit,
                    lineTotalUsd = resolvedTotal,
                    invoiceUnitCost = resolvedUnit,
                    invoiceLineTotal = resolvedTotal,
                    isTaxExempt = isExempt
                )
            }
        }

        // Caso: solo números en la línea siguiente "10  1.25  12.50" o "10  Bs. 45,00  Bs. 450,00"
        val strippedCurrencies = line.replace(Regex("""(?:Bs\.?S?|VES|USD)""", RegexOption.IGNORE_CASE), "")
        if (!strippedCurrencies.any { it.isLetter() && it !in listOf('x', 'X') }) {
            val nums = extractNumbersInString(line)
            if (nums.size >= 2) {
                return buildItemFromNameAndNumbers(productName, nums)
            }
        }
        return null
    }

    private fun parseSingleLineProduct(line: String): ExtractedInvoiceItem? {
        val currencyPrefix = """(?:(?:Bs\.?S?|VES|USD)\s*|\$\s*)?"""
        // Patrón 1: Cantidad al inicio: "10 Harina PAN 1Kg 1.20 12.00" o "10x Harina PAN Bs. 45,00 Bs. 450,00"
        val leadingQtyRegex = Regex(
            """^\s*(?:\d{4,}\s+)?(\d+(?:[.,]\d+)?)\s*(?:[xX*]|uds?|unds?|pzas?|cajas?|kg)?\s+(.+?)\s+$currencyPrefix(\d[\d.,]*)(?:\s+$currencyPrefix(\d[\d.,]*))?\s*$""",
            RegexOption.IGNORE_CASE
        )
        leadingQtyRegex.find(line)?.let { m ->
            val qty = parseFlexNumber(m.groupValues[1])
            val rawName = m.groupValues[2].trim()
            val numA = parseFlexNumber(m.groupValues[3])
            val numB = m.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }?.let { parseFlexNumber(it) }

            if (qty != null && qty > 0.0 && qty < 100000.0 && rawName.any { it.isLetter() } && numA != null) {
                val (unitCost, lineTotal) = resolveUnitAndTotal(qty, numA, numB)
                if (unitCost > 0.0 || lineTotal > 0.0) {
                    val rUnit = round2(unitCost)
                    val rTotal = round2(lineTotal)
                    val (cleanName, isExempt) = stripExemptMarker(cleanProductName(rawName))
                    return ExtractedInvoiceItem(
                        name = cleanName,
                        quantity = qty,
                        unitCostUsd = rUnit,
                        lineTotalUsd = rTotal,
                        invoiceUnitCost = rUnit,
                        invoiceLineTotal = rTotal,
                        isTaxExempt = isExempt
                    )
                }
            }
        }

        // Patrón 2: Nombre al inicio y 2 o 3 números (con o sin $, Bs., VES, USD) al final
        val trailingNumbersRegex = Regex(
            """^\s*(?:\[[A-Z0-9\-]+\]\s*|[A-Z0-9]{3,12}\s+)?([A-Za-zÁÉÍÓÚÑáéíóúñ].+?)\s{1,}($currencyPrefix\d[\d.,]*(?:\s+$currencyPrefix\d[\d.,]*){1,2})\s*$""",
            RegexOption.IGNORE_CASE
        )
        trailingNumbersRegex.find(line)?.let { m ->
            val rawName = m.groupValues[1].trim()
            val numbersTail = m.groupValues[2]
            val nums = extractNumbersInString(numbersTail)
            if (rawName.count { it.isLetter() } >= 2 && nums.size >= 2) {
                return buildItemFromNameAndNumbers(rawName, nums)
            }
        }

        // Patrón 3: Línea con prefijo explícito de moneda ($ o Bs./VES/USD)
        val explicitCurrency = """(?:Bs\.?S?\s*|VES\s*|USD\s*|\$\s*)"""
        val namePlusPricesRegex = Regex(
            """^\s*(.+?)\s+$explicitCurrency(\d[\d.,]*)(?:\s+$explicitCurrency(\d[\d.,]*))?\s*$""",
            RegexOption.IGNORE_CASE
        )
        namePlusPricesRegex.find(line)?.let { m ->
            val rawName = m.groupValues[1].trim()
            val p1 = parseFlexNumber(m.groupValues[2]) ?: return null
            val p2 = m.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.let { parseFlexNumber(it) }
            if (rawName.count { it.isLetter() } >= 3) {
                val (cleanName, isExempt) = stripExemptMarker(cleanProductName(rawName))
                if (p2 != null && p2 >= p1 && p1 > 0.0) {
                    val estQty = (p2 / p1)
                    val qty = if (abs(estQty - estQty.roundToInt()) < 0.05 && estQty >= 1.0) estQty.roundToInt().toDouble() else 1.0
                    val unitCost = if (qty > 0.0) p2 / qty else p1
                    val rUnit = round2(unitCost)
                    val rTotal = round2(p2)
                    return ExtractedInvoiceItem(
                        name = cleanName,
                        quantity = qty,
                        unitCostUsd = rUnit,
                        lineTotalUsd = rTotal,
                        invoiceUnitCost = rUnit,
                        invoiceLineTotal = rTotal,
                        isTaxExempt = isExempt
                    )
                } else {
                    val rP1 = round2(p1)
                    return ExtractedInvoiceItem(
                        name = cleanName,
                        quantity = 1.0,
                        unitCostUsd = rP1,
                        lineTotalUsd = rP1,
                        invoiceUnitCost = rP1,
                        invoiceLineTotal = rP1,
                        isTaxExempt = isExempt
                    )
                }
            }
        }

        return null
    }

    private fun buildItemFromNameAndNumbers(rawName: String, nums: List<Double>): ExtractedInvoiceItem? {
        val (cleanName, isExempt) = stripExemptMarker(cleanProductName(rawName))
        if (nums.size >= 3) {
            val last3 = nums.takeLast(3)
            val qty = last3[0]
            val unit = last3[1]
            val total = last3[2]
            if (qty > 0.0 && (unit > 0.0 || total > 0.0)) {
                val (resolvedUnit, resolvedTotal) = resolveUnitAndTotal(qty, unit, total)
                val rUnit = round2(resolvedUnit)
                val rTotal = round2(resolvedTotal)
                return ExtractedInvoiceItem(
                    name = cleanName,
                    quantity = qty,
                    unitCostUsd = rUnit,
                    lineTotalUsd = rTotal,
                    invoiceUnitCost = rUnit,
                    invoiceLineTotal = rTotal,
                    isTaxExempt = isExempt
                )
            }
        } else if (nums.size == 2) {
            val n1 = nums[0]
            val n2 = nums[1]
            if (n1 <= 0.0 && n2 <= 0.0) return null
            val n1IsInteger = abs(n1 - n1.roundToInt()) < 0.001
            return if (n1IsInteger && n1 in 1.0..9999.0 && n2 >= n1 * 0.1) {
                val qty = n1
                val rUnit = round2(n2 / qty)
                val rTotal = round2(n2)
                ExtractedInvoiceItem(
                    name = cleanName,
                    quantity = qty,
                    unitCostUsd = rUnit,
                    lineTotalUsd = rTotal,
                    invoiceUnitCost = rUnit,
                    invoiceLineTotal = rTotal,
                    isTaxExempt = isExempt
                )
            } else {
                val unit = n1
                val total = n2
                val qty = if (unit > 0.0 && total >= unit) {
                    val ratio = total / unit
                    if (abs(ratio - ratio.roundToInt()) < 0.06) ratio.roundToInt().toDouble() else round2(ratio)
                } else 1.0
                val rUnit = round2(unit)
                val rTotal = round2(total)
                ExtractedInvoiceItem(
                    name = cleanName,
                    quantity = qty,
                    unitCostUsd = rUnit,
                    lineTotalUsd = rTotal,
                    invoiceUnitCost = rUnit,
                    invoiceLineTotal = rTotal,
                    isTaxExempt = isExempt
                )
            }
        }
        return null
    }

    private fun resolveUnitAndTotal(qty: Double, numA: Double, numB: Double?): Pair<Double, Double> {
        if (numB == null) {
            // Solo vino un valor monetario: decidir si parece unitario o total
            return numA to round2(qty * numA)
        }
        val expectedTotalFromA = qty * numA
        // Si qty * numA coincide aproximadamente con numB -> numA es costo unitario y numB es total
        if (abs(expectedTotalFromA - numB) <= maxOf(0.10, numB * 0.08)) {
            return numA to numB
        }
        // Si qty > 1 y numB coincide con qty * numB? No, si numB >= numA -> numA=unit, numB=total
        return if (numB >= numA) {
            val unit = if (qty > 0.0 && abs(qty * numA - numB) > maxOf(0.25, numB * 0.15)) {
                numB / qty
            } else {
                numA
            }
            unit to numB
        } else {
            // Caso raro donde total vino antes que unitario
            numB to numA
        }
    }

    fun cleanProductName(raw: String): String {
        return raw
            .replace(Regex("""^\s*[-•*]+\s*"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun extractNumbersInString(text: String): List<Double> {
        val cleaned = text.replace(Regex("""(?:Bs\.?S?|VES|USD)""", RegexOption.IGNORE_CASE), " ")
        val tokenRegex = Regex("""\$?\s*(\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{1,4})|\d+(?:[.,]\d{1,4})?)""")
        return tokenRegex.findAll(cleaned)
            .mapNotNull { parseFlexNumber(it.groupValues[1]) }
            .toList()
    }

    fun parseFlexNumber(raw: String): Double? {
        var s = raw.trim()
            .replace("$", "")
            .replace(Regex("""(?:Bs\.?S?|VES|USD)""", RegexOption.IGNORE_CASE), "")
            .replace(" ", "")
        if (s.isBlank()) return null

        val hasComma = s.contains(',')
        val hasDot = s.contains('.')

        s = when {
            hasComma && hasDot -> {
                if (s.lastIndexOf(',') > s.lastIndexOf('.')) {
                    // Formato europeo/venezolano: 1.250,50
                    s.replace(".", "").replace(',', '.')
                } else {
                    // Formato US: 1,250.50
                    s.replace(",", "")
                }
            }
            hasComma -> {
                val parts = s.split(',')
                if (parts.size == 2 && parts[1].length == 3 && parts[0].length >= 1 && parts[0] != "0") {
                    // Podría ser miles "1,250" o 3 decimales; si es factura estándar suele ser miles si no es 0,xxx
                    s.replace(",", "")
                } else {
                    s.replace(',', '.')
                }
            }
            hasDot -> {
                val parts = s.split('.')
                if (parts.size > 2) {
                    // Múltiples puntos de miles: 1.000.000
                    s.replace(".", "")
                } else {
                    s
                }
            }
            else -> s
        }
        return s.toDoubleOrNull()
    }

    private fun round2(value: Double): Double {
        return (value * 100.0).roundToInt() / 100.0
    }
}
