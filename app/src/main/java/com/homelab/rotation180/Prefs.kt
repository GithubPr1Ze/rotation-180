package com.homelab.rotation180

import android.content.Context
import android.content.SharedPreferences

/** Réglages persistants de l'appli. */
object Prefs {
    const val TOLERANCE_MIN = 20
    const val TOLERANCE_MAX = 70
    const val TOLERANCE_DEFAULT = 40
    const val DELAY_MIN = 100
    const val DELAY_MAX = 1500
    const val DELAY_DEFAULT = 400

    private const val KEY_ENABLED = "enabled"
    private const val KEY_TOLERANCE = "tolerance_deg"
    private const val KEY_DELAY = "delay_ms"

    fun of(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("rotation180", Context.MODE_PRIVATE)

    /** L'utilisateur veut le service actif (survit au redémarrage). */
    fun isEnabled(ctx: Context) = of(ctx).getBoolean(KEY_ENABLED, false)
    fun setEnabled(ctx: Context, v: Boolean) = of(ctx).edit().putBoolean(KEY_ENABLED, v).apply()

    /** Écart maximal, en degrés, par rapport à « tête en bas » pour retourner l'écran. */
    fun tolerance(ctx: Context) =
        of(ctx).getInt(KEY_TOLERANCE, TOLERANCE_DEFAULT).coerceIn(TOLERANCE_MIN, TOLERANCE_MAX)
    fun setTolerance(ctx: Context, v: Int) = of(ctx).edit().putInt(KEY_TOLERANCE, v).apply()

    /** Durée de stabilité exigée avant de basculer, en millisecondes. */
    fun delayMs(ctx: Context) =
        of(ctx).getInt(KEY_DELAY, DELAY_DEFAULT).coerceIn(DELAY_MIN, DELAY_MAX)
    fun setDelayMs(ctx: Context, v: Int) = of(ctx).edit().putInt(KEY_DELAY, v).apply()
}
