package com.homelab.rotation180

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Relance le service après un redémarrage du téléphone ou une mise à jour de l'appli. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                if (Prefs.isEnabled(context)) RotationService.sync(context)
        }
    }
}
