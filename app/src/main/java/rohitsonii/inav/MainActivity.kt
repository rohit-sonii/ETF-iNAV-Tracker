package rohitsonii.inav

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import android.content.Intent
import android.os.Build
import android.widget.Button
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.inputmethod.EditorInfo
class MainActivity : AppCompatActivity() {

    private lateinit var spinnerETF: Spinner
    private lateinit var tvINAV: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var etCustomSymbol: EditText

    private lateinit var etFrequency: EditText
    private lateinit var btnTrack: Button
    private var isTracking = false
    private var currentSymbol = ""
    private val NOTIFICATION_PERMISSION_CODE = 100


    private val etfList = arrayOf("NIFTYBEES", "GOLDBEES", "GOLDCASE", "SILVERCASE", "Others")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinnerETF = findViewById(R.id.spinnerETF)
        tvINAV = findViewById(R.id.tvINAV)
        progressBar = findViewById(R.id.progressBar)
        etCustomSymbol = findViewById(R.id.etCustomSymbol)
        etFrequency = findViewById(R.id.etFrequency)
        btnTrack = findViewById(R.id.btnTrack)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            etfList
        )
        spinnerETF.adapter = adapter

        spinnerETF.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = etfList[position]

                if (selected == "Others") {
                    etCustomSymbol.visibility = View.VISIBLE
                    tvINAV.text = ""
                } else {
                    etCustomSymbol.visibility = View.GONE
                    etCustomSymbol.text.clear()
                    fetchINAV(selected)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        etCustomSymbol.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                val symbol = etCustomSymbol.text.toString().trim()
                if (symbol.isNotEmpty()) {
                    fetchINAV(symbol)
                }
                true
            } else {
                false
            }
        }
        btnTrack.setOnClickListener {
            if (isTracking) {
                stopTracking()
            } else {
                startTracking()
            }
        }

    }

    private fun fetchINAV(symbol: String) {
        currentSymbol = symbol
        progressBar.visibility = View.VISIBLE
        tvINAV.text = ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://www.nseindia.com/api/NextApi/apiClient/GetQuoteApi?" +
                        "functionName=getSymbolData&marketType=N&series=EQ&symbol=$symbol"

                val connection = URL(url).openConnection()
                connection.setRequestProperty("Host", "www.nseindia.com")
                connection.setRequestProperty("User-Agent", "")
                connection.setRequestProperty("Cookie", "ext_name=aa")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val response = connection.getInputStream().bufferedReader().readText()
                val inav = JSONObject(response)
                    .getJSONArray("equityResponse")
                    .getJSONObject(0)
                    .getJSONObject("priceInfo")
                    .getString("inav")

                withContext(Dispatchers.Main) {
                    tvINAV.text = inav
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvINAV.text = "Error"
                    progressBar.visibility = View.GONE
                }
            }
        }
    }
    private fun checkNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
                return false
            }
        }
        return true
    }
    private fun startTracking() {
        if (!checkNotificationPermission()) {
            Toast.makeText(this, "Grant notification permission", Toast.LENGTH_SHORT).show()
            return
        }
        val frequency = etFrequency.text.toString().toLongOrNull()
        if (frequency == null || frequency <= 0) {
            Toast.makeText(this, "Enter valid frequency", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentSymbol.isEmpty()) {
            Toast.makeText(this, "Select a scrip first", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, TrackingService::class.java).apply {
            putExtra("SYMBOL", currentSymbol)
            putExtra("FREQUENCY", frequency)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        isTracking = true
        btnTrack.text = "Stop Tracking"
        Toast.makeText(this, "Tracking started", Toast.LENGTH_SHORT).show()
    }

    private fun stopTracking() {
        stopService(Intent(this, TrackingService::class.java))
        isTracking = false
        btnTrack.text = "Keep Tracking"
        Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show()
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}