package local.daybook.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import local.daybook.BuildConfig

/** 只读取当前应用的公开证书，不申请读取其他应用或业务数据的权限。 */
data class InstallIdentity(val packageName: String, val certificateDigests: List<String>, val readFailed: Boolean) {
    fun matchesPinnedCertificate(): Boolean = !readFailed &&
        certificateDigests == listOf(BuildConfig.SIGNING_CERTIFICATE_SHA256)

    fun summary(): String = buildString {
        appendLine("包名：$packageName")
        appendLine("版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("构建类型：${BuildConfig.BUILD_TYPE}")
        appendLine("固定证书匹配：${if (matchesPinnedCertificate()) "是" else "否"}")
        append("证书 SHA-256：${certificateDigests.joinToString("\n").ifEmpty { "无法读取" }}")
    }
}

@Suppress("DEPRECATION")
fun readInstallIdentity(context: Context): InstallIdentity {
    val packageName = context.packageName
    return try {
        val manager = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            manager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            manager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures?.toList().orEmpty()
        }
        val digests = signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }.sorted()
        InstallIdentity(packageName, digests, digests.isEmpty())
    } catch (_: PackageManager.NameNotFoundException) {
        InstallIdentity(packageName, emptyList(), true)
    } catch (_: SecurityException) {
        InstallIdentity(packageName, emptyList(), true)
    }
}
