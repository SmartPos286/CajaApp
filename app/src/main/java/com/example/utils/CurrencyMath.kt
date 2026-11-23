package com.example.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

object CurrencyMath {

    /**
     * Redondeo financiero exacto a 2 decimales para evitar pérdidas o discrepancias
     */
    fun round2(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return BigDecimal.valueOf(value)
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
    }

    /**
     * Redondeo a 4 decimales para costos unitarios e IVA
     */
    fun round4(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return BigDecimal.valueOf(value)
            .setScale(4, RoundingMode.HALF_UP)
            .toDouble()
    }

    /**
     * Formatea valor numérico con 2 decimales
     */
    fun format2(value: Double): String {
        return String.format(Locale.US, "%.2f", round2(value))
    }

    /**
     * Conversión exacta de USD a Bolívares usando la tasa BCV sin pérdidas de centavos
     */
    fun convertUsdToBs(amountUsd: Double, rateBcv: Double): Double {
        if (amountUsd <= 0.0 || rateBcv <= 0.0) return 0.0
        val usd = BigDecimal.valueOf(amountUsd)
        val rate = BigDecimal.valueOf(rateBcv)
        return usd.multiply(rate).setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    /**
     * Calcula base imponible, exento e IVA a partir de los ítems con precisión bancaria
     */
    fun calculateTaxes(
        items: List<Pair<Double, Double>>, // Pair(subtotalUsd, taxRatePercent) e.g., 16.0 or 0.0
        discountUsd: Double = 0.0
    ): TaxSummary {
        var baseTaxable = BigDecimal.ZERO
        var baseExempt = BigDecimal.ZERO
        var totalIva = BigDecimal.ZERO

        for ((price, taxRate) in items) {
            val priceBD = BigDecimal.valueOf(price)
            if (taxRate > 0.001) {
                baseTaxable = baseTaxable.add(priceBD)
                val ivaBD = priceBD.multiply(BigDecimal.valueOf(taxRate / 100.0))
                totalIva = totalIva.add(ivaBD)
            } else {
                baseExempt = baseExempt.add(priceBD)
            }
        }

        val discBD = BigDecimal.valueOf(discountUsd)
        val netTaxable = baseTaxable.subtract(discBD).coerceAtLeast(BigDecimal.ZERO)
        val finalIva = totalIva.setScale(2, RoundingMode.HALF_UP)
        val totalUsd = netTaxable.add(baseExempt).add(finalIva).setScale(2, RoundingMode.HALF_UP)

        return TaxSummary(
            baseTaxableUsd = netTaxable.setScale(2, RoundingMode.HALF_UP).toDouble(),
            baseExemptUsd = baseExempt.setScale(2, RoundingMode.HALF_UP).toDouble(),
            ivaUsd = finalIva.toDouble(),
            totalUsd = totalUsd.toDouble()
        )
    }

    data class TaxSummary(
        val baseTaxableUsd: Double,
        val baseExemptUsd: Double,
        val ivaUsd: Double,
        val totalUsd: Double
    )
}
