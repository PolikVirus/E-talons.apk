package com.example.etalons

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.MifareClassic
import android.nfc.tech.NfcA
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.IOException

class MainActivity: FlutterActivity() {

    private val CHANNEL = "com.etalons.nfc"
    private val TAG = "MainActivity" // Fixed TAG
    private val nfcAdapter: NfcAdapter by lazy {
        NfcAdapter.getDefaultAdapter(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (NfcAdapter.getDefaultAdapter(this) == null) {
            AlertDialog.Builder(this)
                .setMessage("This device does not support NFC.")
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        MethodChannel(
            flutterEngine!!.dartExecutor.binaryMessenger,
            CHANNEL
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "scanNfc" -> {
                    if (NfcAdapter.getDefaultAdapter(this) != null) {
                        startNfcScan()
                        result.success("Scanning for NFC Type A...")
                    } else {
                        result.error("NO_NFC_SUPPORT", "This device does not support NFC.", null)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun startNfcScan() {
        val pendingIntent = createPendingIntent()
        val filters = arrayOf(IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED))
        val techLists = arrayOf(arrayOf(MifareClassic::class.java.name))
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, filters, techLists)
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            action = NfcAdapter.ACTION_TAG_DISCOVERED
        }
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val tag: Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return
        val nfcData: HashMap<String, Any> = HashMap()
        val nfcDatas = readNfcData(tag)

        if (nfcDatas != null) {
            nfcData["cardId"] = getCardId(nfcDatas) ?: ""
            nfcData["firstBusNumber"] = getFirstBusNumber(nfcDatas) ?: ""
            nfcData["secondBusNumber"] = getSecondBusNumber(nfcDatas) ?: ""
            nfcData["firstBusType"] = getFirstBusType(nfcDatas)
            nfcData["secondBusType"] = getSecondBusType(nfcDatas)
            nfcData["firstRideTime"] = firstRideTime(nfcDatas) ?: ""
            nfcData["secondRideTime"] = secondRideTime(nfcDatas) ?: ""
            nfcData["getTotalTrips"] = getRidesFromBytes(nfcDatas)
            nfcData["getRemainingTrips"] = getRemainingRides(nfcDatas)

            // Send scanned data to Flutter
            MethodChannel(
                flutterEngine!!.dartExecutor.binaryMessenger,
                CHANNEL
            ).invokeMethod("onNfcScanned", nfcData)
        }
    }

    private fun readNfcData(tag: Tag): ByteArray? {
        val nfcA = NfcA.get(tag)
        try {
            if (nfcA == null) {
                Log.e(TAG, "Tag does not support NFC-A technology")
                return null
            }
            nfcA.connect()
            val data = ByteArray(16 * 4) // 16 pages * 4 bytes per page
            for (page in 0..15) {
                val offset = page * 4
                val chunk = nfcA.transceive(byteArrayOf(0x30.toByte(), page.toByte()))
                System.arraycopy(chunk, 0, data, offset, 4)
            }
            Log.d(TAG, "Data: ${bytesToHex(data)}")
            return data
        } catch (e: IOException) {
            Log.e(TAG, "Error reading NFC tag", e)
            return null
        } catch (e: TagLostException) {
            Log.e(TAG, "Tag lost", e)
            return null
        } finally {
            try {
                nfcA?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing NFC tag", e)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter.disableForegroundDispatch(this)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL
        ).setMethodCallHandler { call, result ->
            result.notImplemented()
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return buildString(bytes.size * 2) {
            bytes.forEach {
                append(HEX_CHARS[(it.toInt() and 0xF0) ushr 4])
                append(HEX_CHARS[it.toInt() and 0x0F])
            }
        }
    }

    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    private fun getPage(data: ByteArray, pageNumber: Int): ByteArray =
        data.copyOfRange(pageNumber * 4, (pageNumber + 1) * 4)

    private fun getCardId(data: ByteArray): String? {
        val page1Uid = bytesToHex(byteArrayOf(data[7], data[6], data[5], data[4]))
        val page0Uid = bytesToHex(byteArrayOf(data[1], data[2]))
        val page0UidReversed = page0Uid.substring(2, 4) + page0Uid.substring(0, 2)
        val reversedUid = page1Uid + page0UidReversed
        val cardIdHex = reversedUid.substring(0, 12)
        Log.d(TAG, cardIdHex)
        val cardIdDec = java.lang.Long.parseLong(cardIdHex, 16)
        return "1-$cardIdDec"
    }

    private fun getBusNumber(page: ByteArray): String {
        val busNumber = ((page[1].toInt() and 0xFF) shl 8) or (page[0].toInt() and 0xFF)
        var busNumberStr = busNumber.toString().trimStart('0').takeLast(2)
        if (busNumberStr.startsWith("0")) busNumberStr = busNumberStr.substring(1)
        return busNumberStr
    }

    private fun getFirstBusNumber(data: ByteArray): String? {
        val page13 = getPage(data, 13)
        return if ((page13[0] == 0xE0.toByte()) && (page13[1] == 0xB3.toByte())) null else getBusNumber(page13)
    }

    private fun getSecondBusNumber(data: ByteArray): String? {
        val page9 = getPage(data, 9)
        return if ((page9[0] == 0xE0.toByte()) && (page9[1] == 0xB3.toByte())) null else getBusNumber(page9)
    }

    private fun getFirstByteFromPage(data: ByteArray, page: Int): Byte =
        getPage(data, page)[0]

    private fun getFirstBusType(data: ByteArray): String =
        String.format("%02X", getFirstByteFromPage(data, 12))

    private fun getSecondBusType(data: ByteArray): String =
        String.format("%02X", getFirstByteFromPage(data, 8))

    fun firstRideTime(data: ByteArray): String {
        val page12 = getPage(data, 12)
        val hoursByte = page12[2].toInt() and 0xFF
        val minutesByte = page12[1].toInt() and 0xFF
        val minutesToAdd = ((minutesByte / 8) * 1) + ((minutesByte % 8) * (1 / 8.0)).toInt()
        val minutes = hoursByte * 32 + minutesToAdd
        val resetMinutes = minutes % ((34 * 60) + 8)
        val hours = resetMinutes / 60
        val minutesMod = resetMinutes % 60
        return String.format("%02d:%02d", hours, minutesMod)
    }

    fun secondRideTime(data: ByteArray): String {
        val page8 = getPage(data, 8)
        val hoursByte = page8[2].toInt() and 0xFF
        val minutesByte = page8[1].toInt() and 0xFF
        val minutesToAdd = ((minutesByte / 8) * 1) + ((minutesByte % 8) * (1 / 8.0)).toInt()
        val minutes = hoursByte * 32 + minutesToAdd
        val resetMinutes = minutes % ((34 * 60) + 8)
        val hours = resetMinutes / 60
        val minutesMod = resetMinutes % 60
        return String.format("%02d:%02d", hours, minutesMod)
    }

    private fun getRidesFromBytes(data: ByteArray): Int {
        val page6 = getPage(data, 6)
        val byte1 = page6[2]
        val serviceCodes = mapOf(
            0xAD.toByte() to 1,
            0xAE.toByte() to 1,
            0xCD.toByte() to 2,
            0xCE.toByte() to 2,
            0x0D.toByte() to 4,
            0x0E.toByte() to 4,
            0x2D.toByte() to 5,
            0x2E.toByte() to 5,
            0x4D.toByte() to 10,
            0x4E.toByte() to 10,
            0x6D.toByte() to 20,
            0x6E.toByte() to 20
        )
        var closestServiceCode: Byte? = null
        for (serviceCodeByte in serviceCodes.keys) {
            if (closestServiceCode == null || Math.abs(serviceCodeByte.toInt() - byte1.toInt()) < Math.abs(closestServiceCode.toInt() - byte1.toInt())) {
                closestServiceCode = serviceCodeByte
            }
        }
        return serviceCodes[closestServiceCode] ?: 0
    }

    private fun getRemainingRides(data: ByteArray): Int {
        val page7 = getPage(data, 7)
        val page3 = getPage(data, 3)
        val numRides = getRidesFromBytes(data)
        return when {
            numRides == 20 -> page3[0].toInt() and 0xFF
            numRides in 1..10 -> page7[0].toInt() and 0xFF
            else -> 0
        }
    }
}