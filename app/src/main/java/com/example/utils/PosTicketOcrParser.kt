package com.example.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ParsedPosTicketResult(
    val transactionDateTime: String = "",
    val transactionTimestamp: Long = System.currentTimeMillis(),
    val receiptNumber: String = "",
    val referenceNumber: String = "",
    val approvalNumber: String = "",
    val maskedCardNumber: String = "",
    val operationType: String = "COMPRA",
    val totalAmount: Double = 0.0,
    val currency: String = "VES",
    val transactionStatus: String = "APROBADA",
    val bankOrTerminal: String = "",
    val rawText: String = "",
    val usedGeminiVision: Boolean = false
)

object PosTicketOcrParser {

    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    fun normalizeAsciiUpper(input: String): String {
        val noAccents = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return noAccents.uppercase(Locale.US)
    }

    fun parseFromGeminiJson(jsonText: String, rawOcrText: String): ParsedPosTicketResult? {
        return try {
            val cleaned = jsonText
                .replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*```$"), "")
                .trim()
            val startIdx = cleaned.indexOf('{')
            val endIdx = cleaned.lastIndexOf('}')
            if (startIdx < 0 || endIdx <= startIdx) return null
            val obj = jsonParser.parseToJsonElement(cleaned.substring(startIdx, endIdx + 1)).jsonObject

            val dateTime = obj["transactionDateTime"]?.jsonPrimitive?.content?.trim().orEmpty()
            val receipt = obj["receiptNumber"]?.jsonPrimitive?.content?.trim().orEmpty()
            val reference = obj["referenceNumber"]?.jsonPrimitive?.content?.trim().orEmpty()
            val approval = obj["approvalNumber"]?.jsonPrimitive?.content?.trim().orEmpty()
            val card = obj["maskedCardNumber"]?.jsonPrimitive?.content?.trim().orEmpty()
            val opType = obj["operationType"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "COMPRA" }
            val amount = obj["totalAmount"]?.jsonPrimitive?.doubleOrNull
                ?: parseNumberToken(obj["totalAmount"]?.jsonPrimitive?.content.orEmpty())
                ?: 0.0
            val currencyRaw = obj["currency"]?.jsonPrimitive?.content?.trim()?.uppercase(Locale.US).orEmpty()
            val currency = if (currencyRaw == "USD" || currencyRaw == "$") "USD" else "VES"
            val statusRaw = obj["transactionStatus"]?.jsonPrimitive?.content?.trim().orEmpty()
            val status = normalizeStatus(statusRaw)
            val bank = obj["bankOrTerminal"]?.jsonPrimitive?.content?.trim().orEmpty()

            // Complement missing fields from local parser if Gemini left any blank
            val localFallback = if (rawOcrText.isNotBlank()) parseFromRawText(rawOcrText) else null
            val finalDateTime = dateTime.ifBlank { localFallback?.transactionDateTime.orEmpty() }
            val parsedTs = parseDateTimeToMillis(finalDateTime)
                ?: localFallback?.transactionTimestamp
                ?: System.currentTimeMillis()

            ParsedPosTicketResult(
                transactionDateTime = finalDateTime.ifBlank { formatMillis(parsedTs) },
                transactionTimestamp = parsedTs,
                receiptNumber = receipt.ifBlank { localFallback?.receiptNumber.orEmpty() },
                referenceNumber = reference.ifBlank { localFallback?.referenceNumber.orEmpty() },
                approvalNumber = approval.ifBlank { localFallback?.approvalNumber.orEmpty() },
                maskedCardNumber = card.ifBlank { localFallback?.maskedCardNumber.orEmpty() },
                operationType = normalizeOperationType(opType),
                totalAmount = if (amount > 0.0) amount else (localFallback?.totalAmount ?: 0.0),
                currency = currency,
                transactionStatus = status,
                bankOrTerminal = bank.ifBlank { localFallback?.bankOrTerminal.orEmpty() },
                rawText = rawOcrText,
                usedGeminiVision = true
            )
        } catch (_: Exception) {
            null
        }
    }

    fun parseFromRawText(rawText: String): ParsedPosTicketResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val upperFull = normalizeAsciiUpper(rawText)

        val dateTimeStr = extractDateTime(lines, upperFull)
        val timestamp = parseDateTimeToMillis(dateTimeStr) ?: System.currentTimeMillis()
        val finalDateTime = dateTimeStr.ifBlank { formatMillis(timestamp) }

        val receipt = extractLabeledCode(
            lines = lines,
            labels = listOf("NRO. RECIBO", "NO. RECIBO", "NRO RECIBO", "RECIBO", "REC.", "TICKET", "VOUCHER")
        )
        val reference = extractLabeledCode(
            lines = lines,
            labels = listOf("NRO. REFERENCIA", "NO. REFERENCIA", "REFERENCIA", "REF.", "REF:", "TRACE", "STAN", "SECUENCIA", "SEQ")
        )
        val approval = extractLabeledCode(
            lines = lines,
            labels = listOf("APROBACION", "APROB.", "APROB", "AUTORIZACION", "COD. APROB", "AUT.", "AUT:", "AUTH")
        )
        val maskedCard = extractMaskedCard(lines, upperFull)
        val operationType = extractOperationType(upperFull)
        val (amount, currency) = extractTotalAmountAndCurrency(lines, upperFull)
        val status = normalizeStatusFromFullText(upperFull)
        val bankOrTerminal = extractBankOrTerminal(lines, upperFull)

        return ParsedPosTicketResult(
            transactionDateTime = finalDateTime,
            transactionTimestamp = timestamp,
            receiptNumber = receipt,
            referenceNumber = reference,
            approvalNumber = approval,
            maskedCardNumber = maskedCard,
            operationType = operationType,
            totalAmount = amount,
            currency = currency,
            transactionStatus = status,
            bankOrTerminal = bankOrTerminal,
            rawText = rawText,
            usedGeminiVision = false
        )
    }

    private fun extractDateTime(lines: List<String>, upperFull: String): String {
        // 1. Combined date + time on same line: 22/11/2026 16:30(:45)? or 22-11-26 04:30 PM
        val combinedRegex = Regex(
            """(\d{2}[/\-]\d{2}[/\-]\d{2,4}|\d{4}[/\-]\d{2}[/\-]\d{2})\s+(\d{1,2}:\d{2}(?::\d{2})?(?:\s*[AP]M)?)""",
            RegexOption.IGNORE_CASE
        )
        combinedRegex.find(upperFull)?.let { match ->
            val d = normalizeDateToken(match.groupValues[1])
            val t = normalizeTimeToken(match.groupValues[2])
            return "$d $t".trim()
        }

        // 2. Separate FECHA / HORA lines
        val dateRegex = Regex("""(\d{2}[/\-]\d{2}[/\-]\d{2,4}|\d{4}[/\-]\d{2}[/\-]\d{2})""")
        val timeRegex = Regex("""(\d{1,2}:\d{2}(?::\d{2})?(?:\s*[AP]M)?)""", RegexOption.IGNORE_CASE)

        var foundDate = ""
        var foundTime = ""
        for (line in lines) {
            val u = normalizeAsciiUpper(line)
            if (foundDate.isBlank() && (u.contains("FECHA") || dateRegex.containsMatchIn(u))) {
                foundDate = dateRegex.find(u)?.groupValues?.getOrNull(1)?.let { normalizeDateToken(it) }.orEmpty()
            }
            if (foundTime.isBlank() && (u.contains("HORA") || timeRegex.containsMatchIn(u))) {
                foundTime = timeRegex.find(u)?.groupValues?.getOrNull(1)?.let { normalizeTimeToken(it) }.orEmpty()
            }
        }
        return listOf(foundDate, foundTime).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun normalizeDateToken(raw: String): String {
        val parts = raw.replace("-", "/").split("/")
        if (parts.size != 3) return raw
        return if (parts[0].length == 4) {
            // yyyy/MM/dd -> dd/MM/yyyy
            "${parts[2].padStart(2, '0')}/${parts[1].padStart(2, '0')}/${parts[0]}"
        } else {
            val year = if (parts[2].length == 2) "20${parts[2]}" else parts[2]
            "${parts[0].padStart(2, '0')}/${parts[1].padStart(2, '0')}/$year"
        }
    }

    private fun normalizeTimeToken(raw: String): String {
        val clean = raw.trim().uppercase(Locale.US)
        val hasPm = clean.endsWith("PM")
        val hasAm = clean.endsWith("AM")
        val timePart = clean.replace("AM", "").replace("PM", "").trim()
        val segs = timePart.split(":")
        if (segs.size < 2) return raw.trim()
        var hh = segs[0].toIntOrNull() ?: return raw.trim()
        val mm = segs[1].toIntOrNull() ?: return raw.trim()
        if (hasPm && hh < 12) hh += 12
        if (hasAm && hh == 12) hh = 0
        return String.format(Locale.US, "%02d:%02d", hh.coerceIn(0, 23), mm.coerceIn(0, 59))
    }

    fun parseDateTimeToMillis(dateTimeStr: String): Long? {
        if (dateTimeStr.isBlank()) return null
        val patterns = listOf(
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm",
            "dd/MM/yy HH:mm",
            "yyyy-MM-dd HH:mm",
            "dd/MM/yyyy"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                sdf.isLenient = false
                val parsed = sdf.parse(dateTimeStr.trim())
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun formatMillis(millis: Long): String {
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(millis)
    }

    private fun extractLabeledCode(lines: List<String>, labels: List<String>): String {
        for (i in lines.indices) {
            val line = lines[i]
            val upper = normalizeAsciiUpper(line)
            for (label in labels) {
                val idx = upper.indexOf(label)
                if (idx >= 0) {
                    val afterLabel = line.substring((idx + label.length).coerceAtMost(line.length))
                        .trimStart(' ', ':', '.', '#', '-', 'N', 'o', 'O', 'º', '°')
                        .trim()
                    val codeMatch = Regex("""([A-Z0-9\-]{3,20})""", RegexOption.IGNORE_CASE).find(afterLabel)
                    if (codeMatch != null) {
                        val candidate = codeMatch.groupValues[1].trim()
                        if (candidate.any { it.isDigit() }) {
                            return candidate
                        }
                    }
                    // Check next line if code was placed below the label
                    if (i + 1 < lines.size) {
                        val nextCode = Regex("""^([A-Z0-9\-]{3,18})$""", RegexOption.IGNORE_CASE).find(lines[i + 1].trim())
                        if (nextCode != null && nextCode.groupValues[1].any { it.isDigit() }) {
                            return nextCode.groupValues[1]
                        }
                    }
                }
            }
        }
        return ""
    }

    private fun extractMaskedCard(lines: List<String>, upperFull: String): String {
        // Patterns: 4512********8901, 4512-****-****-8901, **** **** **** 8901, **** 8901, XXXX1234
        val maskedRegexes = listOf(
            Regex("""\b(\d{4,6}[\*Xx\-\s]{3,12}\d{4})\b"""),
            Regex("""([\*Xx]{3,12}[\-\s]*\d{4})\b""")
        )
        for (line in lines) {
            val u = normalizeAsciiUpper(line)
            for (rx in maskedRegexes) {
                val match = rx.find(u)
                if (match != null) {
                    val rawCard = match.groupValues[1].replace(Regex("\\s+"), " ").trim()
                    if (rawCard.length >= 6) return rawCard
                }
            }
        }
        // Fallback: line explicitly containing TARJETA or CARD with 4 trailing digits
        for (line in lines) {
            val u = normalizeAsciiUpper(line)
            if (u.contains("TARJETA") || u.contains("CARD") || u.contains("CUENTA")) {
                val last4 = Regex("""(\d{4})\b""").findAll(u).lastOrNull()?.groupValues?.getOrNull(1)
                if (!last4.isNullOrBlank()) {
                    return "**** $last4"
                }
            }
        }
        val anyMasked = maskedRegexes.firstNotNullOfOrNull { it.find(upperFull)?.groupValues?.getOrNull(1) }
        return anyMasked?.trim().orEmpty()
    }

    private fun extractOperationType(upperFull: String): String {
        return when {
            upperFull.contains("ANULACION") || upperFull.contains("VOID") -> "ANULACIÓN"
            upperFull.contains("DEVOLUCION") || upperFull.contains("REFUND") -> "DEVOLUCIÓN"
            upperFull.contains("EXTRAFINANCIAMIENTO") -> "EXTRAFINANCIAMIENTO"
            upperFull.contains("PREAUTORIZACION") -> "PREAUTORIZACIÓN"
            upperFull.contains("CIERRE") && !upperFull.contains("COMPRA") -> "CIERRE"
            upperFull.contains("DEBITO") || upperFull.contains("TDD") || upperFull.contains("MAESTRO") -> "COMPRA DÉBITO"
            upperFull.contains("CREDITO") || upperFull.contains("TDC") || upperFull.contains("VISA") || upperFull.contains("MASTERCARD") -> "COMPRA CRÉDITO"
            else -> "COMPRA"
        }
    }

    private fun normalizeOperationType(op: String): String {
        val u = normalizeAsciiUpper(op)
        return when {
            u.contains("ANUL") || u.contains("VOID") -> "ANULACIÓN"
            u.contains("DEVOL") || u.contains("REFUND") -> "DEVOLUCIÓN"
            u.contains("DEBIT") || u.contains("TDD") -> "COMPRA DÉBITO"
            u.contains("CREDIT") || u.contains("TDC") -> "COMPRA CRÉDITO"
            u.contains("CIERRE") -> "CIERRE"
            u.contains("PREAUT") -> "PREAUTORIZACIÓN"
            else -> op.trim().ifBlank { "COMPRA" }
        }
    }

    private fun extractTotalAmountAndCurrency(lines: List<String>, upperFull: String): Pair<Double, String> {
        val isUsd = upperFull.contains("USD") ||
            upperFull.contains("DOLAR") ||
            Regex("""\$\s*\d""").containsMatchIn(upperFull)
        val currency = if (isUsd && !upperFull.contains("BS")) "USD" else "VES"

        val priorityLabels = listOf("MONTO TOTAL", "TOTAL BS", "TOTAL USD", "TOTAL", "IMPORTE", "MONTO")
        for (label in priorityLabels) {
            for (i in lines.indices) {
                val u = normalizeAsciiUpper(lines[i])
                if (u.contains(label) && !u.contains("SUBTOTAL")) {
                    val numHere = extractLastMonetaryAmount(lines[i])
                    if (numHere != null && numHere > 0.0) {
                        val lineCurrency = detectLineCurrency(u, currency)
                        return Pair(numHere, lineCurrency)
                    }
                    if (i + 1 < lines.size) {
                        val numNext = extractLastMonetaryAmount(lines[i + 1])
                        if (numNext != null && numNext > 0.0) {
                            val nextCurrency = detectLineCurrency(normalizeAsciiUpper(lines[i + 1]), currency)
                            return Pair(numNext, nextCurrency)
                        }
                    }
                }
            }
        }

        // Fallback: largest decimal amount in document
        var bestAmount = 0.0
        var bestCurrency = currency
        for (line in lines) {
            val u = normalizeAsciiUpper(line)
            if (u.contains("RECIBO") || u.contains("REF") || u.contains("APROB") || u.contains("TERMINAL") || u.contains("RIF")) {
                continue
            }
            val parsed = extractLastMonetaryAmount(line)
            if (parsed != null && parsed > bestAmount) {
                bestAmount = parsed
                bestCurrency = detectLineCurrency(u, currency)
            }
        }
        return Pair(bestAmount, bestCurrency)
    }

    private fun detectLineCurrency(upperLine: String, defaultCurrency: String): String {
        return when {
            upperLine.contains("BS") || upperLine.contains("VES") -> "VES"
            upperLine.contains("USD") || upperLine.contains("$") -> "USD"
            else -> defaultCurrency
        }
    }

    private fun extractLastMonetaryAmount(line: String): Double? {
        val regex = Regex("""(\d{1,3}(?:[.,]\d{3})*[.,]\d{2}|\d+[.,]\d{2})""")
        val matches = regex.findAll(line).toList()
        if (matches.isEmpty()) return null
        return parseNumberToken(matches.last().groupValues[1])
    }

    fun parseNumberToken(raw: String): Double? {
        val clean = raw.replace(Regex("[^0-9.,]"), "").trim()
        if (clean.isBlank()) return null
        val lastDot = clean.lastIndexOf('.')
        val lastComma = clean.lastIndexOf(',')
        val normalized = when {
            lastDot >= 0 && lastComma >= 0 -> {
                if (lastComma > lastDot) {
                    // Venezuelan/European: 1.234,56
                    clean.replace(".", "").replace(",", ".")
                } else {
                    // US: 1,234.56
                    clean.replace(",", "")
                }
            }
            lastComma >= 0 -> {
                val afterComma = clean.length - lastComma - 1
                if (afterComma == 2 || afterComma == 1) {
                    clean.replace(",", ".")
                } else {
                    clean.replace(",", "")
                }
            }
            else -> clean
        }
        return normalized.toDoubleOrNull()
    }

    private fun normalizeStatusFromFullText(upperFull: String): String {
        return when {
            upperFull.contains("RECHAZAD") ||
                upperFull.contains("DECLINAD") ||
                upperFull.contains("DENEGAD") ||
                upperFull.contains("FONDOS INSUFICIENTES") ||
                upperFull.contains("TARJETA INVALIDA") ||
                upperFull.contains("ERROR") -> "RECHAZADA"
            upperFull.contains("ANULAD") || upperFull.contains("VOID") -> "ANULADA"
            upperFull.contains("PENDIENTE") -> "PENDIENTE"
            upperFull.contains("APROBAD") ||
                upperFull.contains("APPROVED") ||
                upperFull.contains("EXITOS") ||
                upperFull.contains("TRANSACCION COMPLETA") -> "APROBADA"
            else -> "APROBADA"
        }
    }

    fun normalizeStatus(raw: String): String {
        val u = normalizeAsciiUpper(raw)
        return when {
            u.contains("RECHAZ") || u.contains("DECLIN") || u.contains("DENEG") -> "RECHAZADA"
            u.contains("ANUL") || u.contains("VOID") -> "ANULADA"
            u.contains("PEND") -> "PENDIENTE"
            else -> "APROBADA"
        }
    }

    private fun extractBankOrTerminal(lines: List<String>, upperFull: String): String {
        val knownBanks = listOf(
            "BANESCO", "MERCANTIL", "PROVINCIAL", "BBVA", "BNC", "BANCAMIGA",
            "BANCO DE VENEZUELA", "VENEZUELA", "BDV", "TESORO", "BICENTENARIO",
            "BANCARIBE", "SOFITASA", "CARONI", "EXTERIOR", "ACTIVO", "PLAZA",
            "PLATCO", "CREDICARD", "CONSORTIUM", "BANPLUS", "100% BANCO"
        )
        val bankFound = knownBanks.firstOrNull { upperFull.contains(it) }
        val terminalCode = extractLabeledCode(lines, listOf("TERMINAL", "TID", "TERM.", "PUNTO"))
        return listOfNotNull(
            bankFound?.let {
                it.lowercase(Locale.getDefault()).replaceFirstChar { ch -> ch.uppercase() }
            },
            terminalCode.takeIf { it.isNotBlank() }?.let { "Term: $it" }
        ).joinToString(" • ")
    }
}
