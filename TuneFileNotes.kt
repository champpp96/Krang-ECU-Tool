package com.krang.ecutool

/**
 * Tune editing is intentionally not implemented in starter v0.1.0.
 *
 * Before writing a value to a BIN:
 * - validate that the XDF matches the exact binary/ECU definition;
 * - validate offset, element width, endianness, signedness, scaling and units;
 * - preserve an untouched original copy;
 * - implement and test the correct checksum where applicable;
 * - never send a write/flash command without a verified recovery process.
 */
object TuneFileNotes
