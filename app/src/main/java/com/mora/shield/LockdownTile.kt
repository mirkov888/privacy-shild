package com.mora.shield

import android.app.AlertDialog
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class LockdownTile : TileService() {
    override fun onStartListening() {
        qsTile?.let {
            it.state = Tile.STATE_INACTIVE
            it.label = "Lockdown"
            it.updateTile()
        }
    }

    override fun onClick() {
        val d = AlertDialog.Builder(this)
            .setTitle("Lockdown sekarang?")
            .setMessage("Sidik jari/wajah dinonaktifkan dan layar dikunci. Kamu harus memakai PIN untuk membuka.")
            .setPositiveButton("Lockdown") { _, _ ->
                Thread { Ps.run("lockdown") }.start()
            }
            .setNegativeButton("Batal", null)
            .create()
        showDialog(d)
    }
}
