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

@OptIn(ExperimentalStdlibApi::class)
class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {
    private lateinit var binding: ActivityMainBinding
    private lateinit var statusTextView: TextView
    private var nfcAdapter: NfcAdapter? = null

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

    companion object {
        private const val TAG = "NFC"
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

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            statusTextView.text = "このデバイスはNFCに対応していません"
            return
        }

        if (!nfcAdapter!!.isEnabled) {
            statusTextView.text = "NFC機能が無効です"
            return
        }

        statusTextView.text = "FeliCaをかざしてください"
    }

    override fun onResume() {
        super.onResume()

        // リーダーモードの有効化
        val flags = NfcAdapter.FLAG_READER_NFC_F or // FeliCaを読み取る
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK // NDEFチェックをスキップ
        // or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS // システム音を無効化

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

    override fun onPause() {
        super.onPause()
        // リーダーモードの無効化
        nfcAdapter?.disableReaderMode(this)
    }

    // カードを検知した際のコールバック（別スレッドで動作）
    override fun onTagDiscovered(tag: Tag) {
        val nfcF = NfcF.get(tag) ?: return

        try {
            nfcF.connect()

            runOnUiThread {
                statusTextView.text = "スマートフォンをリーダにかざしてください"
            }

            // Authentication1コマンドでFeliCaを黙らす（ポーリングに応答させない）
            val resultAuth1 = auth1(nfcF, tag)

            if (!resultAuth1) {
                Log.w(TAG, "Auth1 failed")
            }

            val resultEmulate = emulate(nfcF)

            if (!resultEmulate) {
                Log.w(TAG, "Emulate failed")
            }

            runOnUiThread {
                if (resultEmulate) {
                    statusTextView.text = "エミュレートが完了しました"
                } else {
                    statusTextView.text = "エミュレートが失敗しました"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                statusTextView.text = "エラーが発生しました\n${e.stackTraceToString()}"
            }
        } finally {
            nfcF.close()
        }
    }

    private fun auth1(nfcF: NfcF, tag: Tag): Boolean {
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

        nfcF.timeout = 500

        val responseAuth1 = nfcF.transceive(commandAuth1)

        Log.d(TAG, "responseAuth1: ${responseAuth1.toHexString()}")

        return responseAuth1[1] == 0x11.toByte()
    }

    private fun emulate(nfcF: NfcF): Boolean {
        var responsePolling = byteArrayOf(18, 0x01)
        responsePolling += myIDm
        responsePolling += myPMm

        var responseSSC = byteArrayOf(13, 0x0D)
        responseSSC += myIDm
        responseSSC += byteArrayOf(1, 0xAB.toByte(), 0xCD.toByte())

        nfcF.timeout = 60000

        var command: ByteArray
        try {
            // ダミーコマンドを送りレスポンスをコマンドとして受け取る
            command = nfcF.transceive(byteArrayOf(1))
        } catch (e: TagLostException) {
            return false
        }

        flag = true
        while (flag) {
            Log.d(TAG, "command: ${command.toHexString()}")
            runOnUiThread {
                statusTextView.text = "エミュレート中"
            }

            try {
                val response: ByteArray = when (command[1].toInt()) {
                    0x00 -> responsePolling
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

                command = nfcF.transceive(response)
            } catch (e: TagLostException) {
                return true
            }
        }

        return true
    }
}