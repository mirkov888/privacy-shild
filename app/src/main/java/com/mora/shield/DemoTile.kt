package com.mora.shield

import android.app.AlertDialog
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class DemoTile : TileService() {
    override fun onStartListening() {
        qsTile?.let {
            it.state = Tile.STATE_INACTIVE
            it.label = "Demo"
            it.updateTile()
        }
    }

    override fun onClick() {
        val d = AlertDialog.Builder(this)
            .setTitle("Terapkan profil Demo/Protes?")
            .setMessage("Biometrik mati dalam 1 jam, USB data terkunci, NFC/Bluetooth/ADB mati. Mode uji tidak berubah.")
            .setPositiveButton("Ya") { _, _ ->
                Thread { Ps.run("sprofile demo") }.start()
            }
            .setNegativeButton("Batal", null)
            .create()
        showDialog(d)
    }
}
