package com.krang.ecutool

/**
 * Future protocol boundary.
 *
 * Implement this only for ECU models whose communication protocol, adapter,
 * request/response framing, and safety requirements have been verified.
 */
interface EcuProtocol {
    val profileName: String

    suspend fun connect(): Result<Unit>
    suspend fun readLiveData(): Result<Map<String, Double>>
    suspend fun readDiagnosticCodes(): Result<List<String>>
}
