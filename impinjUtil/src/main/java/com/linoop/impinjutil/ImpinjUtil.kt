package com.linoop.impinjutil

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.impinj.octane.*
import com.linoop.impinjutil.databinding.LayoutConfigBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Exception

class ImpinjUtil(
    private val context: Context,
    private val readerListener: ReaderListener,
    private val gson: Gson
) {
    private lateinit var deviceConfigDialog: LayoutConfigBinding
    private lateinit var configDialog: AlertDialog
    private lateinit var reader: ImpinjReader
    private val sharedPrefManager =
        ImpinjPrefManager(context.getSharedPreferences(IMPINJ_PREF, Context.MODE_PRIVATE))

    //var readerIPAddress = ""
    var isConnected = false
    var impinjConfig: ImpinjConfig

    init {
        var antennaConfigString = sharedPrefManager.getAntennaConfiguration()
        if (antennaConfigString == "") {
            val antennaConfigList = ArrayList<ImpinjConfig.AntennaConfig>()
            for (i in 1..4) {
                val antConfig = ImpinjConfig.AntennaConfig(i, 30.0, -70.0, true)
                antennaConfigList.add(antConfig)
            }
            impinjConfig = ImpinjConfig("", antennaConfigList)
            antennaConfigString = gson.toJson(impinjConfig)
            sharedPrefManager.saveAntennaConfiguration(antennaConfigString)
        }
        impinjConfig = gson.fromJson(antennaConfigString, ImpinjConfig::class.java)
    }

    fun deviceConfigDialog() {
        var antennaPort = 0

        val dialogBuilder = MaterialAlertDialogBuilder(context)
        val view = LayoutInflater.from(context).inflate(R.layout.layout_config, null)
        deviceConfigDialog = LayoutConfigBinding.bind(view)
        dialogBuilder.setView(deviceConfigDialog.root)
        dialogBuilder.background = ColorDrawable(Color.TRANSPARENT)
        dialogBuilder.setCancelable(false)

        configDialog = dialogBuilder.create()

        val antennaPortList = context.resources.getStringArray(R.array.antenna_port_list)
        val readerStartDelayValueList =
            context.resources.getStringArray(R.array.reader_start_delay_value_list)
        val readingDurationValueList =
            context.resources.getStringArray(R.array.reading_duration_value_list)
        val txPowerList = ArrayList<Double>()
        val rxSensitivityList = ArrayList<Double>()
        for (rx in -80..-30) rxSensitivityList.add(rx.toDouble())
        var tx = 10.0
        while (tx <= 30) {
            txPowerList.add(tx)
            tx += 0.25
        }
        deviceConfigDialog.enableAntenna.setOnCheckedChangeListener { _, isChecked ->
            deviceConfigDialog.enableAntenna.text = if (isChecked) "Enabled" else "Disabled"
        }
        deviceConfigDialog.antennaPortNumber.adapter = ArrayAdapter(
            context, android.R.layout.simple_list_item_1,
            antennaPortList
        )

        deviceConfigDialog.readerStartDelay.adapter = ArrayAdapter(
            context, android.R.layout.simple_list_item_1,
            readerStartDelayValueList
        )

        deviceConfigDialog.readingDuration.adapter = ArrayAdapter(
            context, android.R.layout.simple_list_item_1,
            readingDurationValueList
        )

        deviceConfigDialog.readerStartDelay.setSelection(
            readerStartDelayValueList.indexOf(
                sharedPrefManager.getReaderStartDelay()
            )
        )
        deviceConfigDialog.readingDuration.setSelection(
            readingDurationValueList.indexOf(
                sharedPrefManager.getReadingDuration()
            )
        )

        deviceConfigDialog.antennaPortNumber.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener,
                AdapterView.OnItemClickListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?,
                    p1: View?,
                    position: Int,
                    p3: Long
                ) {
                    antennaPort = position + 1
                    impinjConfig.antennaConfig?.forEach {
                        if (it.antennaPort == antennaPort) {
                            deviceConfigDialog.txPower.setSelection(
                                txPowerList.indexOf(
                                    it.txPower.toString().toDouble()
                                )
                            )
                            deviceConfigDialog.rxSensitivity.setSelection(
                                rxSensitivityList.indexOf(
                                    it.rxSensitivity.toString().toDouble()
                                )
                            )
                            deviceConfigDialog.enableAntenna.isChecked = it.isEnabled
                        }
                    }
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}

                override fun onItemClick(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {}
            }

        deviceConfigDialog.txPower.adapter = ArrayAdapter(
            context, android.R.layout.simple_list_item_1,
            txPowerList
        )
        deviceConfigDialog.rxSensitivity.adapter = ArrayAdapter(
            context, android.R.layout.simple_list_item_1,
            rxSensitivityList
        )
        deviceConfigDialog.buttonCancel.setOnClickListener {
            configDialog.dismiss()
            connectReader()
        }
        deviceConfigDialog.buttonSave.setOnClickListener {
            val readerIp = deviceConfigDialog.readerIPAddress.text.toString().trim()
            if (readerIp.isEmpty()) {
                deviceConfigDialog.readerIPAddress.error =
                    context.getString(R.string.read_ip_empty_error)
                return@setOnClickListener
            } else {
                deviceConfigDialog.readerIPAddress.error = null
                sharedPrefManager.setReaderIpAddress(readerIp)
            }

            sharedPrefManager.saveAntennaConfiguration(
                deviceConfigDialog.antennaPortNumber.selectedItem.toString()
            )
            impinjConfig.readerAddress = readerIp
            impinjConfig.antennaConfig?.forEach {
                if (it.antennaPort == antennaPort) {
                    it.txPower = deviceConfigDialog.txPower.selectedItem.toString().toDouble()
                    it.rxSensitivity =
                        deviceConfigDialog.rxSensitivity.selectedItem.toString().toDouble()
                    it.isEnabled = deviceConfigDialog.enableAntenna.isChecked
                }
            }
            sharedPrefManager.saveAntennaConfiguration(gson.toJson(impinjConfig))
            sharedPrefManager.setReaderStartDelay(deviceConfigDialog.readerStartDelay.selectedItem.toString())
            sharedPrefManager.setReadingDuration(deviceConfigDialog.readingDuration.selectedItem.toString())
            Toast.makeText(context, "Antenna configuration saved successfully", Toast.LENGTH_SHORT)
                .show()
        }
        deviceConfigDialog.readerIPAddress.setText(impinjConfig.readerAddress)
        configDialog.show()
    }

    fun disconnectReader() {
        CoroutineScope(Dispatchers.IO).launch {
            if (isConnected) {
                reader.disconnect()
                isConnected = false
                updateReaderStatus("Disconnected", true)
            }
        }
    }

    private val inventoryList = ArrayList<String>()
    fun connectReader(): Boolean {
        updateReaderStatus("Connecting to reader", false)
        if (impinjConfig.readerAddress.isBlank()) {
            updateReaderStatus("Reader IP address empty!", true)
            return false
        }
        CoroutineScope(Dispatchers.IO).launch {
            if (isConnected) {
                reader.disconnect()
                isConnected = false
                updateReaderStatus("Disconnected", true)
                Thread.sleep(1000)
            }
            try {
                reader = ImpinjReader()
                // Connect
                Log.d(TAG, "Connecting to ${impinjConfig.readerAddress}")
                updateReaderStatus("Connecting to ${impinjConfig.readerAddress}", false)
                reader.connect(impinjConfig.readerAddress)
                val features = reader.queryFeatureSet()


                // Get the default settings
                val settings = reader.queryDefaultSettings()

                // send a tag report for every tag read
                settings.report.mode = ReportMode.Individual
                settings.report.includeAntennaPortNumber = true
                // disable all antennas, then enable our special set
                val ac = settings.antennas
                //ac.enableAll()
                ac.disableAll()
                // is it an xarray
                if (reader.isXArray) {

                    // in xarray, you can enable by sector or ring
                    // System.out.println("enabling ring 4 and 7");
                    // ac.enableById(AntennaUtilities.GetAntennaIdsByRing(Arrays.asList(4, 7), ReaderModel.XArray));
                    // System.out.println("enabling sector 3,4 and 5");
                    // ac.enableById(AntennaUtilities.GetAntennaIdsBySector(Arrays.asList(3, 4, 5), ReaderModel.XArray));

                    // in xarray, you can specify an optimized antenna list
                    println("enabling optimized antenna list")
                    val listAntennaConfig = ac.antennaConfigs
                    listAntennaConfig.clear()
                    for (antenna in AntennaUtilities.GetOptimizedAntennaList(ReaderModel.XArray)) {
                        listAntennaConfig.add(AntennaConfig(antenna))
                    }
                } else {
                    val max = features.antennaCount
                    Log.d(TAG, "enabling antennas (Max $max)")
                    impinjConfig.antennaConfig?.forEach {
                        if (it.antennaPort <= max) {
                            ac.getAntenna(it.antennaPort).isEnabled = it.isEnabled
                            ac.getAntenna(it.antennaPort).isMaxTxPower = false
                            ac.getAntenna(it.antennaPort).isMaxRxSensitivity = false
                            ac.getAntenna(it.antennaPort).txPowerinDbm = it.txPower
                            ac.getAntenna(it.antennaPort).rxSensitivityinDbm = it.rxSensitivity
                            Log.d(
                                TAG,
                                "antenna ${it.antennaPort} isEnabled ${it.isEnabled} txPower ${it.txPower} rxSen ${it.rxSensitivity}"
                            )
                        }
                    }
                    /*for (i in 1 until max) {
                        ac.getAntenna(i.toShort()).isEnabled = true

                    }*/
                    //ac.getAntenna(max.toShort()).isEnabled = true
                }
                // set all to max power
                /*ac.isMaxTxPower = true
                ac.isMaxRxSensitivity = true

                // or set them to a specific power
                val power = 30//System.getProperty(SampleProperties.powerDbm)//30
                val pwr = power.toDouble()
                ac.isMaxTxPower = false
                ac.txPowerinDbm = pwr
                val rxSens = -70//System.getProperty(SampleProperties.sensitivityDbm)//-70
                val rx = rxSens.toDouble()
                ac.isMaxRxSensitivity = false
                ac.rxSensitivityinDbm = rx*/

                reader.setGpiChangeListener { impinjReader, gpiEvent ->
                    readerListener.readerInput(gpiEvent.portNumber, gpiEvent.isState)
                }

                // enable this GPI and set some debounce
                settings.gpis[0].isEnabled = true
                settings.gpis[0].portNumber = 1
                settings.gpis[0].debounceInMs = 50

                settings.gpis[1].isEnabled = true
                settings.gpis[1].portNumber = 2
                settings.gpis[1].debounceInMs = 50
                // set autostart to go on GPI level

                // set autostart to go on GPI level
                settings.autoStart.gpiPortNumber = 1
                settings.autoStart.gpiPortNumber = 2
                settings.autoStart.mode = AutoStartMode.GpiTrigger
                settings.autoStart.gpiLevel = true

                // if you set start, you have to set stop

                // if you set start, you have to set stop
                settings.autoStop.mode = AutoStopMode.GpiTrigger
                settings.autoStop.gpiPortNumber = 1
                settings.autoStop.gpiPortNumber = 2
                settings.autoStop.gpiLevel = false
                settings.autoStop.timeout = 60000


                // Apply the new settings
                reader.applySettings(settings)

                // connect a listener
                reader.tagReportListener = TagReportListener { _, report ->
                    val tags: List<Tag> = report.tags
                    tags.forEach {
                        if (!inventoryList.contains(it.epc.toString())) {
                            inventoryList.add(it.epc.toString())
                            val epc = it.epc.toString().replace(" ", "")
                            readerListener.onTagRead(epc, inventoryList.size)
                            Log.d(TAG, "epc $epc antenna ${it.antennaPortNumber}")
                        }
                        /*if (it.isAntennaPortNumberPresent) {
                            Log.d(TAG, " antenna: " + it.antennaPortNumber)
                        }*/
                    }
                }
                isConnected = true
                updateReaderStatus("Connected to ${impinjConfig.readerAddress}", false)

            } catch (ex: OctaneSdkException) {
                println(ex.message)
                isConnected = false
                updateReaderStatus("" + ex.message, true)
            } catch (ex: Exception) {
                println(ex.message)
                isConnected = false
                ex.printStackTrace(System.out)
                updateReaderStatus("" + ex.message, true)
            }
        }
        return isConnected
    }

    private var readerStarted = false
    fun startReader(rescan: Boolean) {
        if (readerStarted) return
        if (!isConnected) {
            updateReaderStatus("Reader not connected", true)
            return
        }
        try {
            Log.d(TAG, "Reader Starting")
            if (!rescan) inventoryList.clear()
            reader.start()
            readerStarted = true
        } catch (ex: OctaneSdkException) {
            updateReaderStatus("" + ex.message, true)
        }
    }

    fun stopReader() {
        if (!readerStarted) return
        try {
            reader.stop()
            readerStarted = false
        } catch (e: OctaneSdkException) {
            e.printStackTrace()
        }
    }

    private fun updateReaderStatus(message: String, error: Boolean) {
        readerListener.readerStatus(message, error)
    }

    interface ReaderListener {
        fun readerStatus(status: String, error: Boolean)
        fun readerInput(portNumber: Short, state: Boolean)
        fun onTagRead(epc: String, totalInventory: Int)
    }

    companion object {
        const val IMPINJ_PREF = "impinj_pref"
        const val READER_IP_ADDRESS = "reader_ip_address"
        const val ANTENNA_CONFIG = "antenna_config"
        const val READER_START_DELAY = "reader_start_delay"
        const val READING_DURATION = "reading_duration"
        const val TAG = "Impinj Util Log"
    }
}