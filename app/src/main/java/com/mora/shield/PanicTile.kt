package com.mora.shield

import android.app.AlertDialog
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class PanicTile : TileService() {
    override fun onStartListening() {
        qsTile?.let {
            it.state = Tile.STATE_INACTIVE
            it.label = "PANIC"
            it.updateTile()
        }
    }

    override fun onClick() {
        val d = AlertDialog.Builder(this)
            .setTitle("Jalankan PANIC?")
            .setMessage("Mode pesawat, Bluetooth dan NFC mati, cache dibersihkan, layar dikunci.")
            .setPositiveButton("Ya") { _, _ ->
                Thread { Ps.run("panic") }.start()
            }
            .setNegativeButton("Batal", null)
            .create()
        showDialog(d)
    }
}
