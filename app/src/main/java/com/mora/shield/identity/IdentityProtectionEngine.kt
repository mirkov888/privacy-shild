package com.mora.shield.identity

import android.content.Context
import androidx.webkit.UserAgentMetadata
import java.security.SecureRandom

data class IdentityProtectionSettings(
    val canvasProtection: Boolean = true,
    val webGlProtection: Boolean = true,
    val syncClientHints: Boolean = true,
    val randomizePerSession: Boolean = true,
    val webRtcProtection: Boolean = true
)

class IdentityProtectionStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("mora_identity_protection", Context.MODE_PRIVATE)

    fun load(): IdentityProtectionSettings = IdentityProtectionSettings(
        canvasProtection = preferences.getBoolean(KEY_CANVAS, true),
        webGlProtection = preferences.getBoolean(KEY_WEBGL, true),
        syncClientHints = preferences.getBoolean(KEY_CLIENT_HINTS, true),
        randomizePerSession = preferences.getBoolean(KEY_SESSION_RANDOM, true),
        webRtcProtection = preferences.getBoolean(KEY_WEBRTC, true)
    )

    fun save(settings: IdentityProtectionSettings) {
        preferences.edit()
            .putBoolean(KEY_CANVAS, settings.canvasProtection)
            .putBoolean(KEY_WEBGL, settings.webGlProtection)
            .putBoolean(KEY_CLIENT_HINTS, settings.syncClientHints)
            .putBoolean(KEY_SESSION_RANDOM, settings.randomizePerSession)
            .putBoolean(KEY_WEBRTC, settings.webRtcProtection)
            .apply()
    }

    private companion object {
        const val KEY_CANVAS = "canvas_protection"
        const val KEY_WEBGL = "webgl_protection"
        const val KEY_CLIENT_HINTS = "sync_client_hints"
        const val KEY_SESSION_RANDOM = "randomize_per_session"
        const val KEY_WEBRTC = "webrtc_protection"
    }
}

/** Builds privacy protections for Mora-owned, offline WebViews only. */
object FingerprintProtectionEngine {
    fun newSessionSeed(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun buildDocumentStartScript(
        settings: IdentityProtectionSettings,
        sessionSeed: String
    ): String {
        if (!settings.canvasProtection && !settings.webGlProtection) return ""
        val seed = sessionSeed.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .take(64).ifBlank { "mora-session" }
        return buildString {
            append("(function(){'use strict';\n")
            append("const moraSeed='").append(seed).append("';\n")
            if (settings.canvasProtection) {
                append(
                    """
                    try {
                      const proto = CanvasRenderingContext2D.prototype;
                      const nativeGetImageData = proto.getImageData;
                      const nativePutImageData = proto.putImageData;
                      const nativeToDataURL = HTMLCanvasElement.prototype.toDataURL;
                      const nativeToBlob = HTMLCanvasElement.prototype.toBlob;
                      const hash = (value) => {
                        let h = 2166136261;
                        const text = moraSeed + ':' + String(value);
                        for (let i = 0; i < text.length; i++) {
                          h ^= text.charCodeAt(i);
                          h = Math.imul(h, 16777619);
                        }
                        return h >>> 0;
                      };
                      const perturb = (image, salt) => {
                        const pixels = image && image.data;
                        if (!pixels || pixels.length < 4) return image;
                        const pixelCount = Math.max(1, Math.floor(pixels.length / 4));
                        const pixel = (hash(salt) % pixelCount) * 4;
                        const channel = pixel + 1 + (hash('channel:' + salt) % 3);
                        pixels[channel] = pixels[channel] >= 255 ? pixels[channel] - 1 : pixels[channel] + 1;
                        return image;
                      };
                      proto.getImageData = function() {
                        const image = nativeGetImageData.apply(this, arguments);
                        const salt = Array.from(arguments).slice(2, 4).join('x') + ':' + image.data.length;
                        return perturb(image, salt);
                      };
                      const protectedCopy = (source) => {
                        const copy = document.createElement('canvas');
                        copy.width = source.width;
                        copy.height = source.height;
                        const context = copy.getContext('2d');
                        if (!context || !copy.width || !copy.height) return null;
                        context.drawImage(source, 0, 0);
                        const firstPixel = nativeGetImageData.call(context, 0, 0, 1, 1);
                        perturb(firstPixel, source.width + 'x' + source.height);
                        nativePutImageData.call(context, firstPixel, 0, 0);
                        return copy;
                      };
                      if (nativeToDataURL) {
                        HTMLCanvasElement.prototype.toDataURL = function() {
                          const copy = protectedCopy(this);
                          return nativeToDataURL.apply(copy || this, arguments);
                        };
                      }
                      if (nativeToBlob) {
                        HTMLCanvasElement.prototype.toBlob = function(callback) {
                          const copy = protectedCopy(this);
                          return nativeToBlob.apply(copy || this, arguments);
                        };
                      }
                    } catch (error) {
                      console.warn('Mora Canvas protection unavailable', error);
                    }
                    """.trimIndent()
                )
            }
            if (settings.webGlProtection) {
                append(
                    """
                    try {
                      const hideDebugExtension = (name) =>
                        typeof name === 'string' && name.toLowerCase() === 'webgl_debug_renderer_info';
                      const protectWebGl = (Constructor) => {
                        if (!Constructor || !Constructor.prototype) return;
                        const proto = Constructor.prototype;
                        if (proto.__moraFingerprintGuard) return;
                        Object.defineProperty(proto, '__moraFingerprintGuard', { value: true });
                        const nativeGetParameter = proto.getParameter;
                        if (nativeGetParameter) {
                          proto.getParameter = function(parameter) {
                            if (parameter === 0x1F00 || parameter === 0x9245) return 'WebKit';
                            if (parameter === 0x1F01 || parameter === 0x9246) return 'WebGL Renderer';
                            return nativeGetParameter.call(this, parameter);
                          };
                        }
                        const nativeGetExtension = proto.getExtension;
                        if (nativeGetExtension) {
                          proto.getExtension = function(name) {
                            if (hideDebugExtension(name)) return null;
                            return nativeGetExtension.call(this, name);
                          };
                        }
                        const nativeGetSupportedExtensions = proto.getSupportedExtensions;
                        if (nativeGetSupportedExtensions) {
                          proto.getSupportedExtensions = function() {
                            const extensions = nativeGetSupportedExtensions.call(this);
                            return Array.isArray(extensions) ? extensions.filter((name) => !hideDebugExtension(name)) : extensions;
                          };
                        }
                      };
                      protectWebGl(window.WebGLRenderingContext);
                      protectWebGl(window.WebGL2RenderingContext);
                    } catch (error) {
                      console.warn('Mora WebGL protection unavailable', error);
                    }
                    """.trimIndent()
                )
            }
            if (settings.webRtcProtection) {
                append(
                    """
                    try {
                      const denyMedia = () => Promise.reject(new DOMException('Blocked by Mora preview privacy settings', 'NotAllowedError'));
                      for (const name of ['RTCPeerConnection', 'webkitRTCPeerConnection']) {
                        try { Object.defineProperty(window, name, { value: undefined, configurable: false, writable: false }); }
                        catch (_) { try { window[name] = undefined; } catch (_) {} }
                      }
                      const media = navigator.mediaDevices;
                      if (media) {
                        for (const name of ['getUserMedia', 'getDisplayMedia']) {
                          try { Object.defineProperty(media, name, { value: denyMedia, configurable: false, writable: false }); }
                          catch (_) {}
                        }
                        try { Object.defineProperty(media, 'enumerateDevices', { value: () => Promise.resolve([]), configurable: false, writable: false }); }
                        catch (_) {}
                      }
                      for (const name of ['getUserMedia', 'webkitGetUserMedia']) {
                        try { Object.defineProperty(navigator, name, { value: denyMedia, configurable: false, writable: false }); }
                        catch (_) {}
                      }
                    } catch (error) {
                      console.warn('Mora WebRTC privacy guard unavailable', error);
                    }
                    """.trimIndent()
                )
            }
            append("\n})();")
        }
    }

    /**
     * AndroidX WebKit uses this metadata for Sec-CH-UA* request headers and navigator.userAgentData.
     * Only Chromium-based profiles have User-Agent Client Hints; Safari/Firefox profiles are left alone.
     */
    fun clientHintsFor(profile: IdentityProfile): UserAgentMetadata? {
        val match = Regex("""(Edg|OPR|Chrome|CriOS)/([0-9.]+)""")
            .find(profile.userAgent) ?: return null
        if (profile.platform == IdentityPlatform.IOS) return null

        val token = match.groupValues[1]
        val fullVersion = match.groupValues[2]
        val majorVersion = fullVersion.substringBefore('.').ifBlank { return null }
        val browserBrand = when (token) {
            "Edg" -> "Microsoft Edge"
            "OPR" -> "Opera"
            else -> "Google Chrome"
        }
        val brands = listOf(
            UserAgentMetadata.BrandVersion.Builder()
                .setBrand("Chromium")
                .setMajorVersion(majorVersion)
                .setFullVersion(fullVersion)
                .build(),
            UserAgentMetadata.BrandVersion.Builder()
                .setBrand(browserBrand)
                .setMajorVersion(majorVersion)
                .setFullVersion(fullVersion)
                .build()
        )

        val platform = when (profile.platform) {
            IdentityPlatform.ANDROID -> "Android"
            IdentityPlatform.WINDOWS -> "Windows"
            IdentityPlatform.MACOS -> "macOS"
            IdentityPlatform.LINUX -> "Linux"
            IdentityPlatform.IOS -> return null
        }
        val platformVersion = when (profile.platform) {
            IdentityPlatform.ANDROID -> profile.osVersion.removePrefix("Android").trim()
            IdentityPlatform.WINDOWS -> Regex("""Windows NT ([0-9.]+)""")
                .find(profile.userAgent)?.groupValues?.get(1)
            IdentityPlatform.MACOS -> Regex("""Mac OS X ([0-9_]+)""")
                .find(profile.userAgent)?.groupValues?.get(1)?.replace('_', '.')
            IdentityPlatform.LINUX, IdentityPlatform.IOS -> null
        }

        return UserAgentMetadata.Builder()
            .setBrandVersionList(brands)
            .setFullVersion(fullVersion)
            .setPlatform(platform)
            .setPlatformVersion(platformVersion?.takeIf { it.isNotBlank() })
            .setMobile(profile.platform == IdentityPlatform.ANDROID)
            .setModel(profile.model.takeIf { profile.platform == IdentityPlatform.ANDROID })
            .build()
    }
}
