package com.example.brama

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var openNum: EditText
    private lateinit var closeNum: EditText
    private lateinit var openTimeBtn: Button
    private lateinit var closeTimeBtn: Button
    private lateinit var autoSwitch: SwitchCompat
    private lateinit var status: TextView
    private var pending: String? = null

    private val prefs by lazy { getSharedPreferences(Scheduler.PREFS, Context.MODE_PRIVATE) }

    private fun lp(top: Int) =
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = top }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        openNum = EditText(this).apply {
            hint = "Numer do OTWARCIA"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(prefs.getString("open", ""))
        }
        closeNum = EditText(this).apply {
            hint = "Numer do ZAMKNIĘCIA"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(prefs.getString("close", ""))
        }
        val btnOpen = Button(this).apply {
            text = "OTWÓRZ BRAMĘ"
            textSize = 22f
            setOnClickListener { dial(openNum.text.toString()) }
        }
        val btnClose = Button(this).apply {
            text = "ZAMKNIJ BRAMĘ"
            textSize = 22f
            setOnClickListener { dial(closeNum.text.toString()) }
        }

        val autoTitle = TextView(this).apply {
            text = "AUTOMAT"
            textSize = 18f
        }
        openTimeBtn = Button(this).apply { setOnClickListener { pickTime("open") } }
        closeTimeBtn = Button(this).apply { setOnClickListener { pickTime("close") } }
        autoSwitch = SwitchCompat(this).apply {
            text = "Automatyczne wybieranie numeru"
            isChecked = prefs.getBoolean("auto", false)
        }
        status = TextView(this).apply { textSize = 14f }
        val batteryBtn = Button(this).apply {
            text = "Ustawienia baterii (wyłącz oszczędzanie)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        autoSwitch.setOnCheckedChangeListener { _, on ->
            saveNumbers()
            if (on) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this, arrayOf(Manifest.permission.CALL_PHONE), 2
                    )
                }
                if (Build.VERSION.SDK_INT >= 31 && !Scheduler.canExact(this)) {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
                if (openNum.text.isBlank() && closeNum.text.isBlank()) {
                    Toast.makeText(this, "Wpisz numery telefonów", Toast.LENGTH_LONG).show()
                }
            }
            prefs.edit().putBoolean("auto", on).apply()
            Scheduler.scheduleAll(this)
            refresh()
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 48, 48, 48)
            addView(openNum, lp(0))
            addView(closeNum, lp(0))
            addView(btnOpen, LinearLayout.LayoutParams(-1, 260).apply { topMargin = 32 })
            addView(btnClose, LinearLayout.LayoutParams(-1, 260).apply { topMargin = 32 })
            addView(autoTitle, lp(64))
            addView(openTimeBtn, lp(16))
            addView(closeTimeBtn, lp(8))
            addView(autoSwitch, lp(16))
            addView(status, lp(16))
            addView(batteryBtn, lp(16))
        }
        setContentView(ScrollView(this).apply { addView(layout) })
        refresh()
    }

    private fun hm(h: Int, m: Int) = "%02d:%02d".format(h, m)

    private fun refresh() {
        val oh = prefs.getInt("open_h", 5)
        val om = prefs.getInt("open_m", 0)
        val ch = prefs.getInt("close_h", 23)
        val cm = prefs.getInt("close_m", 0)
        openTimeBtn.text = "Otwórz o: ${hm(oh, om)}"
        closeTimeBtn.text = "Zamknij o: ${hm(ch, cm)}"
        status.text = if (prefs.getBoolean("auto", false))
            "Automat WŁĄCZONY: otwarcie ${hm(oh, om)}, zamknięcie ${hm(ch, cm)}"
        else
            "Automat wyłączony"
    }

    private fun pickTime(which: String) {
        val defH = if (which == "open") 5 else 23
        val h = prefs.getInt("${which}_h", defH)
        val m = prefs.getInt("${which}_m", 0)
        TimePickerDialog(this, { _, nh, nm ->
            prefs.edit().putInt("${which}_h", nh).putInt("${which}_m", nm).apply()
            Scheduler.scheduleAll(this)
            refresh()
        }, h, m, true).show()
    }

    private fun saveNumbers() {
        prefs.edit()
            .putString("open", openNum.text.toString().trim())
            .putString("close", closeNum.text.toString().trim())
            .apply()
    }

    override fun onPause() {
        super.onPause()
        saveNumbers()
        Scheduler.scheduleAll(this)
    }

    private fun dial(number: String) {
        if (number.isBlank()) {
            Toast.makeText(this, "Wpisz numer", Toast.LENGTH_SHORT).show()
            return
        }
        saveNumbers()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number.trim(), null)))
        } else {
            pending = number
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 1 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            pending?.let { dial(it) }
        }
        pending = null
    }
}
