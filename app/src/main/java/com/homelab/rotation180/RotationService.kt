package com.homelab.rotation180

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import android.view.View
import android.view.WindowManager
import kotlin.math.acos
import kotlin.math.hypot

/**
 * Surveille l'accéléromètre et, uniquement quand le téléphone est tenu tête en bas,
 * pose une surcouche invisible qui impose le portrait inversé (180°).
 * Le reste du temps, rien n'est posé : la rotation d'origine d'Android est intacte.
 */
class RotationService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "Rotation180"
        private const val CHANNEL = "service"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "com.homelab.rotation180.STOP"

        /** En dessous de cette accélération dans le plan de l'écran (m/s²), le téléphone est « à plat ». */
        private const val FLAT_THRESHOLD = 4.0f
        /** Marge ajoutée à la tolérance pour quitter le 180° (évite les allers-retours à la limite). */
        private const val EXIT_MARGIN_DEG = 15
        private const val SAMPLING_US = 100_000

        @Volatile
        var running = false
            private set

        /** Démarre ou arrête le service selon le réglage et l'autorisation de surcouche. */
        fun sync(ctx: Context) {
            val app = ctx.applicationContext
            val intent = Intent(app, RotationService::class.java)
            if (Prefs.isEnabled(app) && Settings.canDrawOverlays(app)) {
                try {
                    app.startForegroundService(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Démarrage du service refusé", e)
                }
            } else {
                app.stopService(intent)
            }
            TileService.requestListeningState(app, ComponentName(app, ToggleTile::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var sensorRegistered = false

    private var overlay: View? = null
    private var filtered = FloatArray(3)
    private var hasSample = false
    /** Vrai juste après l'allumage de l'écran : la première mesure s'applique sans délai. */
    private var immediate = true
    private var pendingSince = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> startSensor()
                Intent.ACTION_SCREEN_OFF -> stopSensor()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        sensorManager = getSystemService(SensorManager::class.java)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (intent?.action == ACTION_STOP) {
            Prefs.setEnabled(this, false)
            stopSelf()
            TileService.requestListeningState(this, ComponentName(this, ToggleTile::class.java))
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this) || accelerometer == null) {
            Log.w(TAG, "Autorisation de surcouche ou accéléromètre manquant, arrêt")
            stopSelf()
            return START_NOT_STICKY
        }
        if (getSystemService(PowerManager::class.java).isInteractive) startSensor()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        unregisterReceiver(screenReceiver)
        stopSensor()
        setFlipped(false)
        super.onDestroy()
    }

    // --- Capteur ---

    private fun startSensor() {
        if (sensorRegistered) return
        val sensor = accelerometer ?: return
        hasSample = false
        immediate = true
        pendingSince = 0L
        sensorRegistered = sensorManager.registerListener(this, sensor, SAMPLING_US)
    }

    private fun stopSensor() {
        if (!sensorRegistered) return
        sensorManager.unregisterListener(this)
        sensorRegistered = false
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        if (hasSample) {
            for (i in 0..2) filtered[i] += 0.3f * (event.values[i] - filtered[i])
        } else {
            for (i in 0..2) filtered[i] = event.values[i]
            hasSample = true
        }
        val x = filtered[0]
        val y = filtered[1]
        val planar = hypot(x, y)
        val flipped = overlay != null
        val first = immediate
        immediate = false

        // À plat : l'orientation est indéterminée, on garde l'état en cours.
        if (planar < FLAT_THRESHOLD) {
            pendingSince = 0L
            return
        }

        // Écart en degrés par rapport à « tête en bas » (gravité vers le haut de l'appareil : y = -g).
        val deviation = Math.toDegrees(acos((-y / planar).coerceIn(-1f, 1f)).toDouble())
        val tolerance = Prefs.tolerance(this)
        val wanted = if (flipped) {
            deviation < (tolerance + EXIT_MARGIN_DEG).coerceAtMost(85)
        } else {
            deviation <= tolerance
        }
        if (wanted == flipped) {
            pendingSince = 0L
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (pendingSince == 0L) pendingSince = now
        if (first || now - pendingSince >= Prefs.delayMs(this)) {
            pendingSince = 0L
            setFlipped(wanted)
        }
    }

    // --- Surcouche ---

    private fun setFlipped(flip: Boolean) {
        if (flip == (overlay != null)) return
        if (flip) {
            val params = WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                screenOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                title = "Rotation180"
            }
            val view = View(this)
            try {
                windowManager.addView(view, params)
                overlay = view
            } catch (e: Exception) {
                Log.w(TAG, "Impossible de poser la surcouche", e)
            }
        } else {
            try {
                windowManager.removeViewImmediate(overlay)
            } catch (e: Exception) {
                Log.w(TAG, "Impossible de retirer la surcouche", e)
            }
            overlay = null
        }
    }

    // --- Notification ---

    private fun goForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Service de rotation", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, RotationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Rotation 180° active")
            .setContentText("L'écran se retourne quand le téléphone est tête en bas")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Arrêter", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }
}
