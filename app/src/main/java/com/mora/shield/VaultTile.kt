package com.mora.shield

import android.app.AlertDialog
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class VaultTile : TileService() {
    override fun onStartListening() {
        qsTile?.let {
            it.state = Tile.STATE_INACTIVE
            it.label = "Brankas"
            it.updateTile()
        }
    }

    override fun onClick() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Kontrol brankas")
            .setMessage("Aktifkan pembatasan aplikasi atau pulihkan aplikasi yang disembunyikan?")
            .setPositiveButton("Aktifkan") { _, _ -> Thread { Ps.run("vault on") }.start() }
            .setNeutralButton("Buka brankas") { _, _ -> Thread { Ps.run("vault off") }.start() }
            .setNegativeButton("Batal", null)
            .create()
        showDialog(dialog)
    }
}
