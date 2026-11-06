package com.mora.shield.security

import com.mora.shield.Root

/** Operasi hanya untuk paket pilihan pengguna; tidak menghapus data aplikasi. */
object SensitiveAppActions {
    private val protected = setOf(
        "com.android.systemui", "com.android.settings", "com.android.phone", "com.android.launcher3",
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.android.providers.settings", "com.google.android.permissioncontroller",
        "com.android.permissioncontroller", "com.mora.shield"
    )

    fun canToggle(packageName: String): Boolean = packageName.isNotBlank() && packageName !in protected &&
        !packageName.startsWith("android.")

    fun previewDisable(packageName: String): String? =
        if (canToggle(packageName)) "pm disable-user --user 0 ${quote(packageName)}" else null

    fun disable(packageName: String): Root.Result {
        val command = previewDisable(packageName) ?: return Root.Result(2, "Paket sistem penting tidak boleh dinonaktifkan")
        return Root.sh(command)
    }

    fun enable(packageName: String): Root.Result {
        if (!canToggle(packageName)) return Root.Result(2, "Paket sistem penting tidak boleh diubah")
        return Root.sh("pm enable ${quote(packageName)}")
    }

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
