package com.bokor.fuelapp.domain

/**
 * Parses a number typed on any keyboard. Hungarian and Romanian layouts produce a decimal
 * comma, which [String.toDoubleOrNull] rejects outright.
 */
fun String.toAmountOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()
