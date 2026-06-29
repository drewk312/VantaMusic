package com.audiophile.musicplayer.playback

import android.Manifest.permission.MEDIA_CONTENT_CONTROL
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.os.Build
import android.os.Process
import android.util.Base64
import android.util.Log
import androidx.annotation.XmlRes
import com.audiophile.musicplayer.BuildConfig
import com.audiophile.musicplayer.R
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Validates MediaSession callers using package name + signing certificate SHA-256 fingerprints
 * defined in [R.xml.allowed_media_browser_callers].
 *
 * Adapted from the Android UAMP sample (PackageValidator).
 */
class PackageValidator(
    context: Context,
    @XmlRes xmlResId: Int = R.xml.allowed_media_browser_callers
) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val certificateAllowList: Map<String, KnownCallerInfo>
    private val platformSignature: String
    private val callerChecked = mutableMapOf<String, Pair<Int, Boolean>>()

    init {
        certificateAllowList = buildCertificateAllowList(appContext.resources.getXml(xmlResId))
        platformSignature = getSystemSignature()
    }

    fun isKnownCaller(callingPackage: String, callingUid: Int): Boolean {
        callerChecked[callingPackage]?.let { (checkedUid, checkResult) ->
            if (checkedUid == callingUid) return checkResult
        }

        val callerPackageInfo = buildCallerInfo(callingPackage) ?: run {
            callerChecked[callingPackage] = callingUid to false
            return false
        }

        if (callerPackageInfo.uid != callingUid) {
            Log.w(TAG, "UID mismatch for package=$callingPackage expected=$callingUid actual=${callerPackageInfo.uid}")
            callerChecked[callingPackage] = callingUid to false
            return false
        }

        val callerSignature = callerPackageInfo.signature
        val isPackageInAllowList = callerSignature != null &&
            certificateAllowList[callingPackage]?.signatures?.any { it.signature == callerSignature } == true

        val isCallerKnown = when {
            callingUid == Process.myUid() -> true
            isPackageInAllowList -> true
            callingUid == Process.SYSTEM_UID -> true
            callerSignature != null && callerSignature == platformSignature -> true
            callerPackageInfo.permissions.contains(MEDIA_CONTENT_CONTROL) -> true
            else -> false
        }

        if (!isCallerKnown) {
            logUnknownCaller(callerPackageInfo)
        }

        callerChecked[callingPackage] = callingUid to isCallerKnown
        return isCallerKnown
    }

    fun hasPinnedSignature(callingPackage: String, callingUid: Int): Boolean {
        if (callingPackage.isBlank()) return false
        val callerPackageInfo = buildCallerInfo(callingPackage) ?: return false
        if (callerPackageInfo.uid != callingUid) return false
        val callerSignature = callerPackageInfo.signature ?: return false
        return certificateAllowList[callingPackage]?.signatures?.any { it.signature == callerSignature } == true
    }

    fun isPlatformOrSystemCaller(callingPackage: String, callingUid: Int): Boolean {
        if (callingUid == Process.SYSTEM_UID) return true
        val callerPackageInfo = buildCallerInfo(callingPackage) ?: return false
        if (callerPackageInfo.uid != callingUid) return false
        val callerSignature = callerPackageInfo.signature ?: return false
        return callerSignature == platformSignature
    }

    private fun logUnknownCaller(callerPackageInfo: CallerPackageInfo) {
        if (!BuildConfig.DEBUG || callerPackageInfo.signature == null) return
        Log.i(
            TAG,
            "Add caller to res/xml/allowed_media_browser_callers.xml:\n" +
                "<signature name=\"${callerPackageInfo.name}\" package=\"${callerPackageInfo.packageName}\">\n" +
                "    <key release=\"false\">${callerPackageInfo.signature}</key>\n" +
                "</signature>"
        )
    }

    private fun buildCallerInfo(callingPackage: String): CallerPackageInfo? {
        val packageInfo = getPackageInfo(callingPackage) ?: return null
        val appName = packageInfo.applicationInfo?.loadLabel(packageManager)?.toString() ?: callingPackage
        val uid = packageInfo.applicationInfo?.uid ?: return null
        val signature = getSignature(packageInfo)
        val requestedPermissions = packageInfo.requestedPermissions
        val permissionFlags = packageInfo.requestedPermissionsFlags
        val activePermissions = mutableSetOf<String>()
        requestedPermissions?.forEachIndexed { index, permission ->
            if ((permissionFlags?.getOrNull(index) ?: 0).and(PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                activePermissions += permission
            }
        }
        return CallerPackageInfo(appName, callingPackage, uid, signature, activePermissions)
    }

    @SuppressLint("PackageManagerGetSignatures")
    private fun getPackageInfo(callingPackage: String): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(
                    callingPackage,
                    PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_PERMISSIONS
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(
                    callingPackage,
                    PackageManager.GET_SIGNATURES or PackageManager.GET_PERMISSIONS
                )
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    @SuppressLint("PackageManagerGetSignatures")
    private fun getSignature(packageInfo: PackageInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return null
            val signatures = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            if (signatures == null || signatures.size != 1) return null
            return getSignatureSha256(signatures[0].toByteArray())
        }

        @Suppress("DEPRECATION")
        val legacySignatures = packageInfo.signatures
        if (legacySignatures == null || legacySignatures.size != 1) return null
        return getSignatureSha256(legacySignatures[0].toByteArray())
    }

    private fun buildCertificateAllowList(parser: XmlResourceParser): Map<String, KnownCallerInfo> {
        val allowList = LinkedHashMap<String, KnownCallerInfo>()
        try {
            var eventType = parser.next()
            while (eventType != XmlResourceParser.END_DOCUMENT) {
                if (eventType == XmlResourceParser.START_TAG) {
                    val callerInfo = when (parser.name) {
                        "signing_certificate" -> parseV1Tag(parser)
                        "signature" -> parseV2Tag(parser)
                        else -> null
                    }
                    callerInfo?.let { info ->
                        val existing = allowList[info.packageName]
                        if (existing != null) {
                            existing.signatures += info.signatures
                        } else {
                            allowList[info.packageName] = info
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (xmlException: XmlPullParserException) {
            Log.e(TAG, "Could not read allowed callers from XML.", xmlException)
        } catch (ioException: IOException) {
            Log.e(TAG, "Could not read allowed callers from XML.", ioException)
        }
        return allowList
    }

    private fun parseV1Tag(parser: XmlResourceParser): KnownCallerInfo {
        val name = parser.getAttributeValue(null, "name").orEmpty()
        val packageName = parser.getAttributeValue(null, "package").orEmpty()
        val isRelease = parser.getAttributeBooleanValue(null, "release", false)
        val certificate = parser.nextText().replace(WHITESPACE_REGEX, "")
        val signature = getSignatureSha256(Base64.decode(certificate, Base64.DEFAULT))
        return KnownCallerInfo(name, packageName, mutableSetOf(KnownSignature(signature, isRelease)))
    }

    private fun parseV2Tag(parser: XmlResourceParser): KnownCallerInfo {
        val name = parser.getAttributeValue(null, "name").orEmpty()
        val packageName = parser.getAttributeValue(null, "package").orEmpty()
        val callerSignatures = mutableSetOf<KnownSignature>()
        var eventType = parser.next()
        while (eventType != XmlResourceParser.END_TAG) {
            if (eventType == XmlResourceParser.START_TAG && parser.name == "key") {
                val isRelease = parser.getAttributeBooleanValue(null, "release", false)
                val signature = parser.nextText().replace(WHITESPACE_REGEX, "").lowercase()
                callerSignatures += KnownSignature(signature, isRelease)
            }
            eventType = parser.next()
        }
        return KnownCallerInfo(name, packageName, callerSignatures)
    }

    private fun getSystemSignature(): String {
        val platformInfo = getPackageInfo(ANDROID_PLATFORM)
            ?: throw IllegalStateException("Platform package not found")
        return getSignature(platformInfo)
            ?: throw IllegalStateException("Platform signature not found")
    }

    private fun getSignatureSha256(certificate: ByteArray): String {
        val md = try {
            MessageDigest.getInstance("SHA-256")
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("Could not find SHA-256 hash algorithm", e)
        }
        md.update(certificate)
        return md.digest().joinToString(":") { String.format("%02x", it) }
    }

    private data class KnownCallerInfo(
        val name: String,
        val packageName: String,
        val signatures: MutableSet<KnownSignature>
    )

    private data class KnownSignature(
        val signature: String,
        val release: Boolean
    )

    private data class CallerPackageInfo(
        val name: String,
        val packageName: String,
        val uid: Int,
        val signature: String?,
        val permissions: Set<String>
    )

    companion object {
        private const val TAG = "VANTA_PACKAGE_VALIDATOR"
        private const val ANDROID_PLATFORM = "android"
        private val WHITESPACE_REGEX = "\\s|\\n".toRegex()
    }
}
