package com.linoop.impinjutil

import android.content.SharedPreferences
import androidx.core.content.edit
import com.linoop.impinjutil.ImpinjUtil.Companion.ANTENNA_CONFIG
import com.linoop.impinjutil.ImpinjUtil.Companion.READER_IP_ADDRESS
import com.linoop.impinjutil.ImpinjUtil.Companion.READER_START_DELAY
import com.linoop.impinjutil.ImpinjUtil.Companion.READING_DURATION

class ImpinjPrefManager (private val sharedPreferences: SharedPreferences) {

    fun setReaderIpAddress(readerIpAddress: String) =
        sharedPreferences.edit { putString(READER_IP_ADDRESS, readerIpAddress) }

    fun getReaderIpAddress() = sharedPreferences.getString(READER_IP_ADDRESS, "")

    fun saveAntennaConfiguration(antennaConfig: String) = sharedPreferences.edit {
        putString(ANTENNA_CONFIG, antennaConfig)
    }

    fun getAntennaConfiguration() = sharedPreferences.getString(ANTENNA_CONFIG, "")


    fun setReaderStartDelay(delay: String) = sharedPreferences.edit { putString(READER_START_DELAY, delay) }
    fun getReaderStartDelay() = sharedPreferences.getString(READER_START_DELAY, "3500")

    fun setReadingDuration(duration: String) = sharedPreferences.edit { putString(READING_DURATION, duration) }
    fun getReadingDuration() = sharedPreferences.getString(READING_DURATION, "5500")

}