package de.l0rz.m110

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var connStatus: TextView
    private lateinit var deviceSpinner: Spinner
    private lateinit var labelSpinner: Spinner
    private lateinit var textInput: EditText
    private lateinit var textSizeSpinner: Spinner
    private lateinit var textAlignSpinner: Spinner
    private lateinit var codeInput: EditText
    private lateinit var aiKeyInput: EditText
    private lateinit var aiPromptInput: EditText
    private lateinit var imagePreview: ImageView
    private lateinit var chkDither: CheckBox
    private lateinit var rotateSpinner: Spinner
    private lateinit var offXSeek: SeekBar
    private lateinit var offYSeek: SeekBar
    private lateinit var offXLabel: TextView
    private lateinit var offYLabel: TextView

    private var devices: List<BluetoothDevice> = emptyList()

    /** Aktuelles Druckbild (genau das, was der DRUCKEN-Button ausgibt). */
    private var currentLabel: Bitmap? = null
    /** Reproduziert die aktuelle Vorschau — für Re-Render bei Label-Größen-Wechsel. */
    private var lastBuilder: (() -> Bitmap)? = null

    private val prefs by lazy { getSharedPreferences("m110", Context.MODE_PRIVATE) }

    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { loadDevices() }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> if (uri != null) onImagePicked(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        connStatus = findViewById(R.id.connStatus)
        deviceSpinner = findViewById(R.id.deviceSpinner)
        labelSpinner = findViewById(R.id.labelSpinner)
        textInput = findViewById(R.id.textInput)
        textSizeSpinner = findViewById(R.id.textSizeSpinner)
        textAlignSpinner = findViewById(R.id.textAlignSpinner)
        codeInput = findViewById(R.id.codeInput)
        aiKeyInput = findViewById(R.id.aiKeyInput)
        aiPromptInput = findViewById(R.id.aiPromptInput)
        imagePreview = findViewById(R.id.imagePreview)
        chkDither = findViewById(R.id.chkDither)
        rotateSpinner = findViewById(R.id.rotateSpinner)
        offXSeek = findViewById(R.id.offXSeek)
        offYSeek = findViewById(R.id.offYSeek)
        offXLabel = findViewById(R.id.offXLabel)
        offYLabel = findViewById(R.id.offYLabel)

        setupSpinners()
        setupAlignment()

        findViewById<Button>(R.id.btnConnect).setOnClickListener { connect(devices.getOrNull(deviceSpinner.selectedItemPosition)) }
        findViewById<Button>(R.id.btnPrint).setOnClickListener { doPrint() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { build { PhomemoPrinter.renderTestLabel() } }
        findViewById<Button>(R.id.btnBuildText).setOnClickListener { buildText() }
        findViewById<Button>(R.id.btnPickImage).setOnClickListener { pickImage.launch("image/*") }
        findViewById<Button>(R.id.btnBuildQr).setOnClickListener { buildCode(qr = true) }
        findViewById<Button>(R.id.btnBuildBarcode).setOnClickListener { buildCode(qr = false) }
        findViewById<Button>(R.id.btnAiGenerate).setOnClickListener { aiGenerate() }
        findViewById<Button>(R.id.btnCenterReset).setOnClickListener {
            rotateSpinner.setSelection(0)
            offXSeek.progress = PhomemoPrinter.DEFAULT_OFFSET_X + 80
            offYSeek.progress = PhomemoPrinter.DEFAULT_OFFSET_Y + 80
        }
        chkDither.setOnCheckedChangeListener { _, _ -> rebuild() }

        aiKeyInput.setText(prefs.getString("openai_key", ""))
        updateConnStatus()
        ensurePerms()
    }

    private fun setupSpinners() {
        labelSpinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, PhomemoPrinter.LABEL_SIZES.map { it.name })
        labelSpinner.setSelection(prefs.getInt("label_idx", 0).coerceIn(0, PhomemoPrinter.LABEL_SIZES.size - 1))
        PhomemoPrinter.labelSize = PhomemoPrinter.LABEL_SIZES[labelSpinner.selectedItemPosition]
        labelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                PhomemoPrinter.labelSize = PhomemoPrinter.LABEL_SIZES[pos]
                prefs.edit().putInt("label_idx", pos).apply()
                rebuild()  // Vorschau in neuer Größe neu erzeugen
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        textSizeSpinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, listOf("Auto", "Klein", "Mittel", "Groß"))
        textAlignSpinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, listOf("Zentriert", "Links", "Rechts"))
    }

    private val rotations = listOf(0, 90, 180, 270)

    private fun setupAlignment() {
        rotateSpinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, rotations.map { "$it°" })
        rotateSpinner.setSelection(prefs.getInt("rot_idx", 0).coerceIn(0, rotations.size - 1))
        PhomemoPrinter.rotationDeg = rotations[rotateSpinner.selectedItemPosition]
        rotateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                PhomemoPrinter.rotationDeg = rotations[pos]
                prefs.edit().putInt("rot_idx", pos).apply(); rebuild()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Seekbar 0..160 -> Offset -80..+80 dot (Default = Kopf-Korrektur)
        offXSeek.progress = (prefs.getInt("off_x", PhomemoPrinter.DEFAULT_OFFSET_X) + 80).coerceIn(0, 160)
        offYSeek.progress = (prefs.getInt("off_y", PhomemoPrinter.DEFAULT_OFFSET_Y) + 80).coerceIn(0, 160)
        applyOffsets(save = false)
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { applyOffsets(save = false) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { applyOffsets(save = true); rebuild() }
        }
        offXSeek.setOnSeekBarChangeListener(listener)
        offYSeek.setOnSeekBarChangeListener(listener)
    }

    private fun applyOffsets(save: Boolean) {
        val ox = offXSeek.progress - 80
        val oy = offYSeek.progress - 80
        PhomemoPrinter.offsetX = ox
        PhomemoPrinter.offsetY = oy
        offXLabel.text = "Versatz ⟷ horizontal: $ox dot"
        offYLabel.text = "Versatz ↕ vertikal: $oy dot"
        if (save) prefs.edit().putInt("off_x", ox).putInt("off_y", oy).apply()
    }

    // ---------------------------------------------------------------- Vorschau-Modell

    /** Baut die Vorschau aus einem Builder, merkt ihn für Re-Render und zeigt das Ergebnis. */
    private fun build(builder: () -> Bitmap) {
        lastBuilder = builder
        status.text = "Erzeuge Vorschau…"
        Thread {
            try {
                val bmp = PhomemoPrinter.orient(builder())
                runOnUiThread {
                    currentLabel = bmp
                    imagePreview.setImageBitmap(bmp)
                    status.text = "Vorschau bereit (${bmp.width}×${bmp.height} px) — DRUCKEN drücken"
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "❌ Vorschau-Fehler: ${e.message}" }
            }
        }.start()
    }

    /** Erneuert die Vorschau mit dem zuletzt genutzten Builder (z.B. nach Label-/Dither-Wechsel). */
    private fun rebuild() { lastBuilder?.let { build(it) } }

    private fun doPrint() {
        val bmp = currentLabel ?: run { toast("Erst eine Vorschau erzeugen"); return }
        if (!PhomemoPrinter.isConnected) { toast("Erst verbinden"); return }
        status.text = "Drucke…"
        Thread {
            try { PhomemoPrinter.printBitmap(PhomemoPrinter.applyPrintOffset(bmp)); runOnUiThread { status.text = "✅ Gedruckt" } }
            catch (e: Exception) { runOnUiThread { status.text = "❌ Druckfehler: ${e.message}" } }
        }.start()
    }

    // ---------------------------------------------------------------- Builder pro Inhalt

    private fun buildText() {
        val t = textInput.text.toString().trim()
        if (t.isEmpty()) { toast("Bitte Text eingeben"); return }
        val size = when (textSizeSpinner.selectedItemPosition) {
            1 -> 34f; 2 -> 52f; 3 -> 76f; else -> 0f
        }
        val align = when (textAlignSpinner.selectedItemPosition) {
            1 -> PhomemoPrinter.Align.LEFT; 2 -> PhomemoPrinter.Align.RIGHT
            else -> PhomemoPrinter.Align.CENTER
        }
        build { PhomemoPrinter.renderText(t, size, align) }
    }

    private fun buildCode(qr: Boolean) {
        val c = codeInput.text.toString().trim()
        if (c.isEmpty()) { toast("Bitte Inhalt eingeben"); return }
        build { if (qr) PhomemoPrinter.renderQr(c) else PhomemoPrinter.renderBarcode(c) }
    }

    private fun onImagePicked(uri: Uri) {
        try {
            val raw = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                ?: run { toast("Bild nicht lesbar"); return }
            build { PhomemoPrinter.renderImage(raw, chkDither.isChecked) }
        } catch (e: Exception) { toast("Fehler: ${e.message}") }
    }

    private fun aiGenerate() {
        val key = aiKeyInput.text.toString().trim()
        val prompt = aiPromptInput.text.toString().trim()
        if (key.isEmpty()) { toast("OpenAI API-Key eintragen"); return }
        if (prompt.isEmpty()) { toast("Prompt eingeben"); return }
        prefs.edit().putString("openai_key", key).apply()
        status.text = "🎨 Generiere Piktogramm… (~20 s)"
        Thread {
            try {
                val raw = OpenAiImage.generatePictogram(key, prompt)
                runOnUiThread { build { PhomemoPrinter.renderImage(raw, chkDither.isChecked) } }
            } catch (e: Exception) {
                runOnUiThread { status.text = "❌ KI-Fehler: ${e.message}" }
            }
        }.start()
    }

    // ---------------------------------------------------------------- BT

    private fun neededPerms(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)

    private fun hasPerms() = neededPerms().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensurePerms() {
        if (hasPerms()) loadDevices() else reqPerms.launch(neededPerms())
    }

    private fun loadDevices() {
        if (!hasPerms()) { connStatus.text = "🔴 Bluetooth-Berechtigung fehlt"; return }
        val mgr = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = mgr.adapter
        if (adapter == null || !adapter.isEnabled) { connStatus.text = "🔴 Bluetooth ist aus"; return }
        try {
            devices = adapter.bondedDevices.toList()
        } catch (e: SecurityException) { connStatus.text = "🔴 Keine BT-Berechtigung"; return }
        val names = devices.map { d ->
            try { "${d.name ?: "?"} (${d.address})" } catch (e: SecurityException) { d.address }
        }
        deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            if (names.isEmpty()) listOf("— keine gekoppelten Geräte —") else names)

        // Letztes Gerät bevorzugt, sonst M110/Phomemo automatisch
        val lastAddr = prefs.getString("last_device", null)
        val idx = when {
            lastAddr != null -> devices.indexOfFirst { safeAddr(it) == lastAddr }
            else -> -1
        }.let { if (it >= 0) it else names.indexOfFirst { n -> n.contains("M110", true) || n.contains("Phomemo", true) } }
        if (idx >= 0) deviceSpinner.setSelection(idx)

        // Auto-Connect ans zuletzt genutzte Gerät
        if (lastAddr != null && !PhomemoPrinter.isConnected) {
            devices.firstOrNull { safeAddr(it) == lastAddr }?.let {
                connStatus.text = "🟡 Verbinde automatisch…"
                connect(it)
            }
        }
    }

    private fun connect(d: BluetoothDevice?) {
        if (d == null) { toast("Erst M110 in den Android-BT-Einstellungen koppeln"); return }
        connStatus.text = "🟡 Verbinde mit ${safeName(d)}…"
        Thread {
            try {
                PhomemoPrinter.connect(d)
                prefs.edit().putString("last_device", safeAddr(d)).apply()
                runOnUiThread { updateConnStatus() }
            } catch (e: Exception) {
                runOnUiThread { connStatus.text = "🔴 Verbindung fehlgeschlagen: ${e.message}" }
            }
        }.start()
    }

    private fun updateConnStatus() {
        connStatus.text = if (PhomemoPrinter.isConnected)
            "🟢 Verbunden mit ${PhomemoPrinter.connectedName ?: "Drucker"}"
        else "🔴 Nicht verbunden"
    }

    private fun safeName(d: BluetoothDevice) =
        try { d.name ?: d.address } catch (e: SecurityException) { d.address }
    private fun safeAddr(d: BluetoothDevice) = d.address

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onDestroy() { super.onDestroy(); PhomemoPrinter.disconnect() }
}
