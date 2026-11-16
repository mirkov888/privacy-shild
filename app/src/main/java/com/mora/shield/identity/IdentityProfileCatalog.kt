package com.mora.shield.identity

import com.mora.shield.DeviceIdentityPresetCatalog

/** Curated starter catalogue; data-driven so verified profiles can be added independently. */
object IdentityProfileCatalog {
    private val bundledProfiles = listOf(
        IdentityProfile("android-pixel9-chrome", IdentityPlatform.ANDROID, "Google", "Pixel 9", "Android 15", "Chrome", "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36", "Android Chrome compatibility testing"),
        IdentityProfile("android-samsung-s25-chrome", IdentityPlatform.ANDROID, "Samsung", "Galaxy S25", "Android 15", "Chrome", "Mozilla/5.0 (Linux; Android 15; SM-S931B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36", "Android Chrome compatibility testing"),
        IdentityProfile("android-xiaomi15-chrome", IdentityPlatform.ANDROID, "Xiaomi", "Xiaomi 15", "Android 15", "Chrome", "Mozilla/5.0 (Linux; Android 15; 24129PN74G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36", "Android Chrome compatibility testing"),
        IdentityProfile("android-redmi-note14pro-chrome", IdentityPlatform.ANDROID, "Xiaomi", "Redmi Note 14 Pro", "Android 14", "Chrome", "Mozilla/5.0 (Linux; Android 14; 24090RA29G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36", "Android Chrome compatibility testing"),
        IdentityProfile("android-oneplus13-chrome", IdentityPlatform.ANDROID, "OnePlus", "OnePlus 13", "Android 15", "Chrome", "Mozilla/5.0 (Linux; Android 15; CPH2653) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36", "Android Chrome compatibility testing"),
        IdentityProfile("android-oppo-findx8-chrome", IdentityPlatform.ANDROID, "OPPO", "Find X8", "Android 15", "Chrome", "Mozilla/5.0 (Linux; Android 15; CPH2651) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36", "Android Chrome compatibility testing"),
        IdentityProfile("android-vivo-x200-chrome", IdentityPlatform.ANDROID, "vivo", "X200", "Android 15", "Chrome", "Mozilla/5.0 (Linux; Android 15; V2405A) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36", "Android Chrome compatibility testing"),
        IdentityProfile("ios-iphone16-safari", IdentityPlatform.IOS, "Apple", "iPhone 16", "iOS 18", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-iphone16-pro-max-safari", IdentityPlatform.IOS, "Apple", "iPhone 16 Pro Max", "iOS 18", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-iphone16-pro-safari", IdentityPlatform.IOS, "Apple", "iPhone 16 Pro", "iOS 18", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-iphone16-plus-safari", IdentityPlatform.IOS, "Apple", "iPhone 16 Plus", "iOS 18", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-iphone16e-safari", IdentityPlatform.IOS, "Apple", "iPhone 16e", "iOS 18", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-iphone15-pro-max-safari", IdentityPlatform.IOS, "Apple", "iPhone 15 Pro Max", "iOS 18", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-iphone15-pro-safari", IdentityPlatform.IOS, "Apple", "iPhone 15 Pro", "iOS 18", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-iphone15-plus-safari", IdentityPlatform.IOS, "Apple", "iPhone 15 Plus", "iOS 18", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-iphone15-safari", IdentityPlatform.IOS, "Apple", "iPhone 15", "iOS 18", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-iphone14-pro-max-safari", IdentityPlatform.IOS, "Apple", "iPhone 14 Pro Max", "iOS 17", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-iphone14-pro-safari", IdentityPlatform.IOS, "Apple", "iPhone 14 Pro", "iOS 17", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-iphone14-plus-safari", IdentityPlatform.IOS, "Apple", "iPhone 14 Plus", "iOS 17", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-iphone14-safari", IdentityPlatform.IOS, "Apple", "iPhone 14", "iOS 17", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-iphone13-pro-max-safari", IdentityPlatform.IOS, "Apple", "iPhone 13 Pro Max", "iOS 16", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-iphone13-pro-safari", IdentityPlatform.IOS, "Apple", "iPhone 13 Pro", "iOS 16", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-iphone13-mini-safari", IdentityPlatform.IOS, "Apple", "iPhone 13 mini", "iOS 16", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-iphone13-safari", IdentityPlatform.IOS, "Apple", "iPhone 13", "iOS 16", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-iphone-se3-safari", IdentityPlatform.IOS, "Apple", "iPhone SE (3rd generation)", "iOS 17", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-ipadpro-safari", IdentityPlatform.IOS, "Apple", "iPad Pro", "iPadOS 18", "Safari", "Mozilla/5.0 (iPad; CPU OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1", "Safari tablet-layout testing"),
        IdentityProfile("windows11-chrome", IdentityPlatform.WINDOWS, "Microsoft", "Windows 11 PC", "Windows 10/11", "Chrome", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36", "Desktop responsive-layout testing"),
        IdentityProfile("windows11-edge", IdentityPlatform.WINDOWS, "Microsoft", "Windows 11 PC", "Windows 10/11", "Edge", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0", "Edge compatibility testing"),
        IdentityProfile("windows11-firefox", IdentityPlatform.WINDOWS, "Microsoft", "Windows 11 PC", "Windows 10/11", "Firefox", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0", "Firefox compatibility testing"),
        IdentityProfile("windows10-laptop-chrome", IdentityPlatform.WINDOWS, "Microsoft", "Windows 10 Laptop", "Windows 10", "Chrome", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36", "Windows laptop responsive-layout testing"),
        IdentityProfile("windows11-laptop-edge", IdentityPlatform.WINDOWS, "Microsoft", "Windows 11 Laptop", "Windows 11", "Edge", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0", "Windows laptop Edge compatibility testing"),
        IdentityProfile("macos-sequoia-safari", IdentityPlatform.MACOS, "Apple", "Mac", "macOS 15", "Safari", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15", "Safari desktop-layout testing"),
        IdentityProfile("macos-sequoia-chrome", IdentityPlatform.MACOS, "Apple", "Mac", "macOS 15", "Chrome", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36", "Chrome desktop-layout testing"),
        IdentityProfile("macos-macbook-air-safari", IdentityPlatform.MACOS, "Apple", "MacBook Air", "macOS 15", "Safari", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15", "MacBook Air responsive-layout testing"),
        IdentityProfile("macos-macbook-pro-chrome", IdentityPlatform.MACOS, "Apple", "MacBook Pro", "macOS 15", "Chrome", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36", "MacBook Pro Chrome compatibility testing"),
        IdentityProfile("linux-desktop-firefox", IdentityPlatform.LINUX, "Linux", "Linux Desktop", "Linux x86_64", "Firefox", "Mozilla/5.0 (X11; Linux x86_64; rv:132.0) Gecko/20100101 Firefox/132.0", "Linux browser compatibility testing"),
        IdentityProfile("linux-desktop-chrome", IdentityPlatform.LINUX, "Linux", "Linux Desktop", "Linux x86_64", "Chrome", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36", "Linux browser compatibility testing"),
        IdentityProfile("linux-laptop-firefox", IdentityPlatform.LINUX, "Linux", "Linux Laptop", "Linux x86_64", "Firefox", "Mozilla/5.0 (X11; Linux x86_64; rv:132.0) Gecko/20100101 Firefox/132.0", "Linux laptop Firefox compatibility testing"),
        IdentityProfile("linux-laptop-chrome", IdentityPlatform.LINUX, "Linux", "Linux Laptop", "Linux x86_64", "Chrome", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36", "Linux laptop Chrome compatibility testing"),
    )

    /**
     * Reuse the curated non-security Android device catalogue in the browser picker.
     * These entries are User-Agent compatibility templates, not verified hardware emulators.
     */
    private val androidDeviceProfiles = DeviceIdentityPresetCatalog.presets.mapNotNull { preset ->
        val model = preset.model.trim().ifBlank { return@mapNotNull null }
        if (bundledProfiles.any {
                it.platform == IdentityPlatform.ANDROID &&
                    it.manufacturer.equals(preset.manufacturer, ignoreCase = true) &&
                    it.model.equals(model, ignoreCase = true)
            }
        ) return@mapNotNull null

        IdentityProfile(
            id = "android-device-${preset.id}-chrome",
            platform = IdentityPlatform.ANDROID,
            manufacturer = preset.manufacturer,
            model = model,
            osVersion = "Android ${preset.release}",
            browser = "Chrome",
            userAgent = "Mozilla/5.0 (Linux; Android ${preset.release}; ${model}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
            purpose = "Android Chrome compatibility template: ${preset.label}; not device emulation"
        )
    }

    val profiles: List<IdentityProfile> = (bundledProfiles + androidDeviceProfiles)
        .distinctBy { it.id }

    fun byId(id: String?): IdentityProfile? = profiles.firstOrNull { it.id == id }
    fun forPlatform(platform: IdentityPlatform): List<IdentityProfile> = profiles.filter { it.platform == platform }
}
