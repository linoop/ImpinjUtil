package com.linoop.impinjutil

data class ImpinjConfig(
    var readerAddress: String,
    val antennaConfig: List<AntennaConfig>? = null
) {
    data class AntennaConfig(
        val antennaPort: Int,
        var txPower: Double,
        var rxSensitivity: Double,
        var isEnabled: Boolean
    )
}
