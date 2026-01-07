package com.example.test3

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.NfcF
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.test3.databinding.ActivityMainBinding
import java.io.IOException
import kotlin.concurrent.thread

@OptIn(ExperimentalStdlibApi::class)
class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {
    private lateinit var binding: ActivityMainBinding
    private lateinit var statusTextView: TextView
    private var nfcAdapter: NfcAdapter? = null
    private var nfcF: NfcF? = null

    private val myIDm = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88.toByte())
    private val myPMm = byteArrayOf(
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte()
    )
    private val mySys = byteArrayOf(0xAB.toByte(), 0xCD.toByte())

    val block0 = ByteArray(16)
    val block1 = ByteArray(16)
    val block2 = ByteArray(16)
    val block3 = ByteArray(16)

    companion object {
        private const val TAG = "FeliCaEmulatorPoC"
    }

    var flag = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        statusTextView = binding.statusTextView

        statusTextView.text = "ロード中"

        thread {
            nfcAdapter = NfcAdapter.getDefaultAdapter(this)

            if (nfcAdapter == null) {
                runOnUiThread {
                    statusTextView.text = "このデバイスはNFCに対応していません"
                }
                return@thread
            }

            if (!nfcAdapter!!.isEnabled) {
                runOnUiThread {
                    statusTextView.text = "NFC機能が無効です"
                }
                return@thread
            }

            runOnUiThread {
                statusTextView.text = "FeliCaをかざしてください"
            }

            disableReaderMode()
            enableReaderMode()
        }
    }

    override fun onResume() {
        super.onResume()
        enableReaderMode()
    }

    override fun onPause() {
        super.onPause()
        disableReaderMode()
    }

    private fun enableReaderMode() {
        // リーダーモードの有効化
        val flags = NfcAdapter.FLAG_READER_NFC_F or // FeliCaを読み取る
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or // NDEFチェックをスキップ
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS // システム音を無効化

        val options = Bundle()
        // 読み取り時の音を抑制したい場合は以下を追加（任意）
        // options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 5000)

        nfcAdapter?.enableReaderMode(
            this,
            this,
            flags,
            options
        )
    }

    private fun disableReaderMode() {
        // リーダーモードの無効化
        nfcAdapter?.disableReaderMode(this)
    }

    // カードを検知した際のコールバック（別スレッドで動作）
    override fun onTagDiscovered(tag: Tag) {
        nfcF = NfcF.get(tag) ?: return

        try {
            nfcF?.connect()

            runOnUiThread {
                statusTextView.text = "スマートフォンをリーダにかざしてください"
            }

            // Authentication1コマンドでFeliCaを黙らす（ポーリングに応答させない）
            val resultAuth1 = auth1(tag)

            if (!resultAuth1) {
                Log.w(TAG, "Auth1 failed")
            }

            val resultEmulate = emulate()

            if (!resultEmulate) {
                Log.w(TAG, "Emulate failed")
            }

            runOnUiThread {
                if (resultEmulate) {
                    statusTextView.text = "エミュレートが完了しました"
                } else {
                    statusTextView.text = "エミュレートが失敗しました"
                }
                statusTextView.append("\n")
                statusTextView.append("リーダからスマートフォンを離してください")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                statusTextView.text = "エラーが発生しました"
                statusTextView.append("\n")
                statusTextView.append(e.stackTraceToString())
            }
        } finally {
            nfcF?.close()
            nfcF = null
        }

        disableReaderMode()
        enableReaderMode()
    }

    private fun auth1(tag: Tag): Boolean {
        if (tag.id.size != 8) {
            return false
        }
        if (tag.techList[0] != "android.nfc.tech.NfcF") {
            return false
        }

        var commandAuth1 = byteArrayOf(24, 0x10)
        commandAuth1 += tag.id
        commandAuth1 += byteArrayOf(1, 0x00, 0x00, 1, 0x00, 0x00)
        commandAuth1 += ByteArray(8)

        nfcF?.timeout = 500

        try {
            val responseAuth1 = nfcF?.transceive(commandAuth1) ?: return false

            Log.d(TAG, "responseAuth1: ${responseAuth1.toHexString()}")
            return responseAuth1[1] == 0x11.toByte()
        } catch (e: TagLostException) {
            return false
        }
    }

    private fun emulate(): Boolean {
        var responsePolling0 = byteArrayOf(18, 0x01)
        responsePolling0 += myIDm
        responsePolling0 += myPMm

        var responsePolling1 = responsePolling0.clone()
        responsePolling1[0] = 20
        responsePolling1 += mySys

        var responsePolling2 = responsePolling0.clone()
        responsePolling2[0] = 20
        responsePolling2 += byteArrayOf(0x00, 0x01)

        var responseRR = byteArrayOf(11, 0x05)
        responseRR += myIDm
        responseRR += byteArrayOf(0x00)

        var responseRWE0 = byteArrayOf(0, 0x07)
        responseRWE0 += myIDm
        responseRWE0 += byteArrayOf(0x00, 0x00)

        var responseRWE1 = responseRWE0.clone()
        responseRWE1[0] = 29
        responseRWE1 += byteArrayOf(1)
        responseRWE1 += block0

        var responseRWE3 = responseRWE0.clone()
        responseRWE3[0] = 61
        responseRWE3 += byteArrayOf(3)
        responseRWE3 += block0
        responseRWE3 += block1
        responseRWE3 += block2

        var responseSSC = byteArrayOf(13, 0x0D)
        responseSSC += myIDm
        responseSSC += byteArrayOf(1) + mySys

        nfcF?.timeout = 60000

        var command: ByteArray
        try {
            // ダミーコマンドを送りレスポンスをコマンドとして受け取る
            command = nfcF?.transceive(byteArrayOf(1)) ?: return false
        } catch (e: TagLostException) {
            return false
        } catch (e: IOException) {
            return false
        }

        flag = true
        while (flag) {
            Log.d(TAG, "command: ${command.toHexString()}")
            runOnUiThread {
                statusTextView.text = "エミュレート中"
            }

            val commandCode = command[1].toInt()

            if (commandCode % 2 != 0) {
                return false
            }

            if (commandCode != 0x00) {
                if (!command.slice(2..9).toByteArray().contentEquals(myIDm)) {
                    return false
                }
            }

            if (commandCode == 0x00) {
                val isSystemCodeValid = (command[2] == mySys[0] || command[2] == 0xFF.toByte()) &&
                        (command[3] == mySys[1] || command[3] == 0xFF.toByte())
                if (!isSystemCodeValid) {
                    return false
                }
            }

            val response: ByteArray = when (commandCode) {
                0x00 -> when (command[4].toInt()) {
                    0x00 -> responsePolling0
                    0x01 -> responsePolling1
                    0x02 -> responsePolling2
                    else -> return false
                }

                0x04 -> responseRR
                0x06 -> when (command[13].toInt()) {
                    1 -> responseRWE1
                    3 -> responseRWE3
                    else -> return false
                }

                0x0C -> responseSSC
                else -> {
                    Log.w(TAG, "Unknown Command")
                    runOnUiThread {
                        Toast.makeText(this, "不明なコマンド", Toast.LENGTH_LONG)
                            .show()
                    }
                    return false
                }
            }

            Log.d(TAG, "response: ${response.toHexString()}")

            try {
                command = nfcF?.transceive(response) ?: return false
            } catch (e: TagLostException) {
                return true
            } catch (e: IllegalStateException) {
                return false
            }
        }

        return true
    }
}