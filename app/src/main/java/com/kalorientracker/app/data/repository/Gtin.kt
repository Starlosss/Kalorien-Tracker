package com.kalorientracker.app.data.repository

/**
 * A scanned barcode can be stored under different GTIN lengths depending on where the product
 * was labelled (a 12-digit UPC-A vs. its 13-digit EAN-13 form with a leading zero). This maps a
 * raw scan to every code worth trying, most specific (the one actually scanned) first.
 */
object Gtin {

    /** The codes to try for a scanned barcode, most specific first. */
    fun candidates(raw: String): List<String> {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 12 -> listOf(digits, "0$digits")
            digits.length == 13 && digits.startsWith("0") -> listOf(digits, digits.drop(1))
            else -> listOf(digits)
        }
    }

    /** Standard GTIN check digit test (modulo 10, weights 3 and 1 from the rightmost digit). */
    fun isValid(code: String): Boolean {
        val digits = code.filter { it.isDigit() }
        if (digits.length !in VALID_LENGTHS) return false
        val sum = digits.reversed().mapIndexed { index, c ->
            val weight = if (index % 2 == 0) 1 else 3
            (c - '0') * weight
        }.sum()
        return sum % 10 == 0
    }

    private val VALID_LENGTHS = setOf(8, 12, 13, 14)
}
