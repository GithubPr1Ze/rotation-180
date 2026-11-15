package com.homelab.rotation180

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** Écran unique : marche/arrêt, tolérance, délai et raccourcis vers les autorisations. */
class MainActivity : Activity() {

    private lateinit var enableSwitch: Switch
    private lateinit var overlayButton: Button
    private lateinit var batteryButton: Button
    private var updatingSwitch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(column, MATCH_PARENT, WRAP_CONTENT)
        })

        column.addView(text("Rotation 180°", 26f))
        column.addView(
            text(
                "Retourne l'écran uniquement quand le téléphone est tenu tête en bas. " +
                    "Le reste du temps, la rotation d'origine d'Android n'est pas modifiée.",
                15f
            ),
            spaced(8)
        )

        enableSwitch = Switch(this).apply {
            text = "Rotation 180° active"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setOnCheckedChangeListener { _, checked -> if (!updatingSwitch) onToggle(checked) }
        }
        column.addView(enableSwitch, spaced(24))

        // Tolérance d'angle
        val toleranceLabel = text("", 16f)
        column.addView(toleranceLabel, spaced(28))
        column.addView(
            slider(
                Prefs.TOLERANCE_MIN, Prefs.TOLERANCE_MAX, 5, Prefs.tolerance(this),
                { toleranceLabel.text = "Tolérance : ±$it° autour de « tête en bas »" },
                { Prefs.setTolerance(this, it) }
            ),
            spaced(4)
        )
        column.addView(
            text("Plus la valeur est grande, plus l'écran se retourne tôt quand tu inclines le téléphone.", 13f)
        )

        // Délai
        val delayLabel = text("", 16f)
        column.addView(delayLabel, spaced(24))
        column.addView(
            slider(
                Prefs.DELAY_MIN, Prefs.DELAY_MAX, 100, Prefs.delayMs(this),
                { delayLabel.text = "Délai avant bascule : $it ms" },
                { Prefs.setDelayMs(this, it) }
            ),
            spaced(4)
        )
        column.addView(text("Temps pendant lequel la position doit rester stable avant de retourner l'écran.", 13f))

        // Autorisations
        column.addView(text("Autorisations", 20f), spaced(32))
        overlayButton = button("Autoriser l'affichage par-dessus les applis") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        column.addView(overlayButton, spaced(8))
        batteryButton = button("Désactiver l'optimisation de batterie") {
            @Suppress("BatteryLife")
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        column.addView(batteryButton, spaced(8))
        column.addView(
            button("Ouvrir la fiche de l'appli (activité en arrière-plan)") {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            },
            spaced(8)
        )
        column.addView(
            text(
                "Sur OnePlus : Utilisation de la batterie → « Autoriser l'activité en arrière-plan », " +
                    "pour que le service reparte après un redémarrage du téléphone.",
                13f
            ),
            spaced(4)
        )
    }

    override fun onResume() {
        super.onResume()
        refresh()
        // Autorisation accordée entre-temps alors que le service était demandé : on le démarre.
        RotationService.sync(this)
    }

    private fun onToggle(checked: Boolean) {
        if (checked && !Settings.canDrawOverlays(this)) {
            setSwitch(false)
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        Prefs.setEnabled(this, checked)
        if (checked &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        RotationService.sync(this)
    }

    private fun refresh() {
        setSwitch(Prefs.isEnabled(this) && Settings.canDrawOverlays(this))
        overlayButton.visibility = if (Settings.canDrawOverlays(this)) View.GONE else View.VISIBLE
        val ignoring = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        batteryButton.visibility = if (ignoring) View.GONE else View.VISIBLE
    }

    private fun setSwitch(checked: Boolean) {
        updatingSwitch = true
        enableSwitch.isChecked = checked
        updatingSwitch = false
    }

    // --- Petits constructeurs de vues ---

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun spaced(topDp: Int) =
        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(topDp) }

    private fun text(content: String, sizeSp: Float) = TextView(this).apply {
        text = content
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun slider(
        min: Int, max: Int, step: Int, value: Int,
        onShow: (Int) -> Unit, onSave: (Int) -> Unit
    ) = SeekBar(this).apply {
        this.max = (max - min) / step
        progress = (value - min) / step
        onShow(min + progress * step)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, p: Int, fromUser: Boolean) {
                val v = min + p * step
                onShow(v)
                if (fromUser) onSave(v)
            }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })
    }
}
