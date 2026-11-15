package com.mora.shield.identity

/** Curated starter catalogue; data-driven so verified profiles can be added independently. */
object IdentityProfileCatalog {
    val profiles = listOf(
        IdentityProfile("android-pixel9-chrome", IdentityPlatform.ANDROID, "Google", "Pixel 9", "Android 15", "Chrome", "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36", "Android Chrome compatibility testing"),
        IdentityProfile("android-samsung-s25-chrome", IdentityPlatform.ANDROID, "Samsung", "Galaxy S25", "Android 15", "Chrome", "Mozilla/5.0 (Linux; Android 15; SM-S931B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36", "Android Chrome compatibility testing"),
        IdentityProfile("android-xiaomi15-chrome", IdentityPlatform.ANDROID, "Xiaomi", "Xiaomi 15", "Android 15", "Chrome", "Mozilla/5.0 (Linux; Android 15; 24129PN74G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36", "Android Chrome compatibility testing"),
        IdentityProfile("android-redmi-note14pro-chrome", IdentityPlatform.ANDROID, "Xiaomi", "Redmi Note 14 Pro", "Android 14", "Chrome", "Mozilla/5.0 (Linux; Android 14; 24090RA29G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36", "Android Chrome compatibility testing"),
        IdentityProfile("android-oneplus13-chrome", IdentityPlatform.ANDROID, "OnePlus", "OnePlus 13", "Android 15", "Chrome", "Mozilla/5.0 (Linux; Android 15; CPH2653) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36", "Android Chrome compatibility testing"),
        IdentityProfile("android-oppo-findx8-chrome", IdentityPlatform.ANDROID, "OPPO", "Find X8", "Android 15", "Chrome", "Mozilla/5.0 (Linux; Android 15; CPH2651) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36", "Android Chrome compatibility testing"),
        IdentityProfile("android-vivo-x200-chrome", IdentityPlatform.ANDROID, "vivo", "X200", "Android 15", "Chrome", "Mozilla/5.0 (Linux; Android 15; V2405A) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36", "Android Chrome compatibility testing"),
        IdentityProfile("ios-iphone16-safari", IdentityPlatform.IOS, "Apple", "iPhone 16", "iOS 18", "Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1", "Safari responsive-layout testing"),
        IdentityProfile("ios-ipadpro-safari", IdentityPlatform.IOS, "Apple", "iPad Pro", "iPadOS 18", "Safari", "Mozilla/5.0 (iPad; CPU OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1", "Safari tablet-layout testing"),
        IdentityProfile("windows11-chrome", IdentityPlatform.WINDOWS, "Microsoft", "Windows 11 PC", "Windows 10/11", "Chrome", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36", "Desktop responsive-layout testing"),
        IdentityProfile("windows11-edge", IdentityPlatform.WINDOWS, "Microsoft", "Windows 11 PC", "Windows 10/11", "Edge", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0", "Edge compatibility testing"),
        IdentityProfile("windows11-firefox", IdentityPlatform.WINDOWS, "Microsoft", "Windows 11 PC", "Windows 10/11", "Firefox", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0", "Firefox compatibility testing"),
        IdentityProfile("macos-sequoia-safari", IdentityPlatform.MACOS, "Apple", "Mac", "macOS 15", "Safari", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15", "Safari desktop-layout testing"),
        IdentityProfile("macos-sequoia-chrome", IdentityPlatform.MACOS, "Apple", "Mac", "macOS 15", "Chrome", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36", "Chrome desktop-layout testing"),
        IdentityProfile("linux-desktop-firefox", IdentityPlatform.LINUX, "Linux", "Linux Desktop", "Linux x86_64", "Firefox", "Mozilla/5.0 (X11; Linux x86_64; rv:132.0) Gecko/20100101 Firefox/132.0", "Linux browser compatibility testing"),
        IdentityProfile("linux-desktop-chrome", IdentityPlatform.LINUX, "Linux", "Linux Desktop", "Linux x86_64", "Chrome", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36", "Linux browser compatibility testing")
    )

    fun byId(id: String?): IdentityProfile? = profiles.firstOrNull { it.id == id }
    fun forPlatform(platform: IdentityPlatform): List<IdentityProfile> = profiles.filter { it.platform == platform }
}
