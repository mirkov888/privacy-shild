package com.mora.shield

/**
 * Device presets for app-compatibility testing.
 *
 * Fingerprints and security patch values are intentionally left blank: they are
 * build-specific and must not be fabricated or used to imitate attestation.
 */
data class DeviceIdentityPreset(
    val id: String,
    val label: String,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val release: String,
    val sdk: Int
) {
    fun toProfile(packageName: String, enabled: Boolean = true) = DeviceIdentityProfile(
        packageName = packageName,
        enabled = enabled,
        manufacturer = manufacturer,
        brand = brand,
        model = model,
        device = device,
        product = product,
        fingerprint = "",
        release = release,
        sdk = sdk,
        securityPatch = ""
    )
}

object DeviceIdentityPresetCatalog {
    // Android SDK: 14=34, 15=35, 16=36. Values are compatibility templates,
    // not certified build fingerprints or proof of a physical device.
    val presets = listOf(
        DeviceIdentityPreset("pixel-9-pro-xl", "Google Pixel 9 Pro XL", "Google", "google", "Pixel 9 Pro XL", "komodo", "komodo", "15", 35),
        DeviceIdentityPreset("pixel-9-pro", "Google Pixel 9 Pro", "Google", "google", "Pixel 9 Pro", "caiman", "caiman", "15", 35),
        DeviceIdentityPreset("pixel-9", "Google Pixel 9", "Google", "google", "Pixel 9", "tokay", "tokay", "15", 35),
        DeviceIdentityPreset("pixel-9-fold", "Google Pixel 9 Pro Fold", "Google", "google", "Pixel 9 Pro Fold", "comet", "comet", "15", 35),
        DeviceIdentityPreset("pixel-8-pro", "Google Pixel 8 Pro", "Google", "google", "Pixel 8 Pro", "husky", "husky", "15", 35),
        DeviceIdentityPreset("pixel-8", "Google Pixel 8", "Google", "google", "Pixel 8", "shiba", "shiba", "15", 35),
        DeviceIdentityPreset("pixel-8a", "Google Pixel 8a", "Google", "google", "Pixel 8a", "akita", "akita", "15", 35),
        DeviceIdentityPreset("pixel-7-pro", "Google Pixel 7 Pro", "Google", "google", "Pixel 7 Pro", "cheetah", "cheetah", "14", 34),
        DeviceIdentityPreset("pixel-7", "Google Pixel 7", "Google", "google", "Pixel 7", "panther", "panther", "14", 34),
        DeviceIdentityPreset("pixel-7a", "Google Pixel 7a", "Google", "google", "Pixel 7a", "lynx", "lynx", "14", 34),
        DeviceIdentityPreset("galaxy-s25-ultra", "Samsung Galaxy S25 Ultra", "Samsung", "samsung", "SM-S938B", "pa3q", "pa3q", "15", 35),
        DeviceIdentityPreset("galaxy-s25-plus", "Samsung Galaxy S25+", "Samsung", "samsung", "SM-S936B", "b0q", "b0q", "15", 35),
        DeviceIdentityPreset("galaxy-s25", "Samsung Galaxy S25", "Samsung", "samsung", "SM-S931B", "e3q", "e3q", "15", 35),
        DeviceIdentityPreset("galaxy-s24-ultra", "Samsung Galaxy S24 Ultra", "Samsung", "samsung", "SM-S928B", "e3q", "e3q", "14", 34),
        DeviceIdentityPreset("galaxy-s24-plus", "Samsung Galaxy S24+", "Samsung", "samsung", "SM-S926B", "e2q", "e2q", "14", 34),
        DeviceIdentityPreset("galaxy-s24", "Samsung Galaxy S24", "Samsung", "samsung", "SM-S921B", "e1q", "e1q", "14", 34),
        DeviceIdentityPreset("galaxy-s23-ultra", "Samsung Galaxy S23 Ultra", "Samsung", "samsung", "SM-S918B", "dm3q", "dm3q", "14", 34),
        DeviceIdentityPreset("galaxy-z-fold6", "Samsung Galaxy Z Fold6", "Samsung", "samsung", "SM-F956B", "q6q", "q6q", "14", 34),
        DeviceIdentityPreset("galaxy-z-flip6", "Samsung Galaxy Z Flip6", "Samsung", "samsung", "SM-F741B", "b6q", "b6q", "14", 34),
        DeviceIdentityPreset("xiaomi-15", "Xiaomi 15", "Xiaomi", "Xiaomi", "Xiaomi 15", "dada", "dada", "15", 35),
        DeviceIdentityPreset("xiaomi-15-ultra", "Xiaomi 15 Ultra", "Xiaomi", "Xiaomi", "Xiaomi 15 Ultra", "xuanyuan", "xuanyuan", "15", 35),
        DeviceIdentityPreset("xiaomi-14", "Xiaomi 14", "Xiaomi", "Xiaomi", "Xiaomi 14", "houji", "houji", "14", 34),
        DeviceIdentityPreset("xiaomi-14-ultra", "Xiaomi 14 Ultra", "Xiaomi", "Xiaomi", "Xiaomi 14 Ultra", "aurora", "aurora", "14", 34),
        DeviceIdentityPreset("redmi-note-14-pro-5g", "Redmi Note 14 Pro 5G", "Xiaomi", "Redmi", "Redmi Note 14 Pro 5G", "ruby", "ruby", "14", 34),
        DeviceIdentityPreset("poco-f6", "POCO F6", "Xiaomi", "POCO", "POCO F6", "peridot", "peridot", "14", 34),
        DeviceIdentityPreset("poco-x6-pro", "POCO X6 Pro", "Xiaomi", "POCO", "POCO X6 Pro", "duchamp", "duchamp", "14", 34),
        DeviceIdentityPreset("oneplus-13", "OnePlus 13", "OnePlus", "OnePlus", "OnePlus 13", "OnePlus 13", "OnePlus 13", "15", 35),
        DeviceIdentityPreset("oneplus-12", "OnePlus 12", "OnePlus", "OnePlus", "OnePlus 12", "waffle", "waffle", "14", 34),
        DeviceIdentityPreset("oneplus-12r", "OnePlus 12R", "OnePlus", "OnePlus", "OnePlus 12R", "aston", "aston", "14", 34),
        DeviceIdentityPreset("oppo-find-x8-pro", "OPPO Find X8 Pro", "OPPO", "OPPO", "Find X8 Pro", "OPPO Find X8 Pro", "OPPO Find X8 Pro", "15", 35),
        DeviceIdentityPreset("oppo-find-x8", "OPPO Find X8", "OPPO", "OPPO", "Find X8", "OPPO Find X8", "OPPO Find X8", "15", 35),
        DeviceIdentityPreset("vivo-x200-pro", "vivo X200 Pro", "vivo", "vivo", "Vivo X200 Pro", "Vivo X200 Pro", "Vivo X200 Pro", "15", 35),
        DeviceIdentityPreset("vivo-x200", "vivo X200", "vivo", "vivo", "Vivo X200", "Vivo X200", "Vivo X200", "15", 35),
        DeviceIdentityPreset("asus-rog-phone-9", "ASUS ROG Phone 9", "ASUS", "asus", "ROG Phone 9", "ASUS ROG Phone 9", "ASUS ROG Phone 9", "15", 35),
        DeviceIdentityPreset("asus-zenfone-11-ultra", "ASUS Zenfone 11 Ultra", "ASUS", "asus", "Zenfone 11 Ultra", "ASUS Zenfone 11 Ultra", "ASUS Zenfone 11 Ultra", "14", 34),
        DeviceIdentityPreset("sony-xperia-1-vi", "Sony Xperia 1 VI", "Sony", "Sony", "XQ-EC54", "XQ-EC54", "XQ-EC54", "14", 34),
        DeviceIdentityPreset("motorola-edge-50-ultra", "Motorola Edge 50 Ultra", "motorola", "motorola", "Edge 50 Ultra", "motorola_edge_50_ultra", "motorola_edge_50_ultra", "14", 34),
        DeviceIdentityPreset("nothing-phone-2", "Nothing Phone (2)", "Nothing", "Nothing", "A065", "Pong", "Pong", "14", 34)
    )

    fun find(id: String): DeviceIdentityPreset? = presets.firstOrNull { it.id == id }
}
