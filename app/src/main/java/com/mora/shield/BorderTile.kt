package com.mora.shield

import android.app.AlertDialog
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class BorderTile : TileService() {
    override fun onStartListening() {
        qsTile?.let {
            it.state = Tile.STATE_INACTIVE
            it.label = "Border"
            it.updateTile()
        }
    }

    override fun onClick() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Profil situasi")
            .setMessage("Border mengaktifkan pengawas jaringan dan brankas sesuai konfigurasi. Home membuka kembali brankas.")
            .setPositiveButton("Border") { _, _ -> Thread { Ps.run("sprofile border") }.start() }
            .setNeutralButton("Home") { _, _ -> Thread { Ps.run("sprofile home") }.start() }
            .setNegativeButton("Batal", null)
            .create()
        showDialog(dialog)
    }
}
