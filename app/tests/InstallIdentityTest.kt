package local.daybook.ui

import local.daybook.BuildConfig
import org.junit.Assert.*
import org.junit.Test

class InstallIdentityTest {
    @Test fun pinnedSignerMatchesOnlyExactCertificate() {
        val identity = InstallIdentity("local.daybook", listOf(BuildConfig.SIGNING_CERTIFICATE_SHA256), false)
        assertTrue(identity.matchesPinnedCertificate())
        assertFalse(InstallIdentity("local.daybook", listOf("0".repeat(64)), false).matchesPinnedCertificate())
    }

    @Test fun failedReadOrAdditionalSignerIsNotTrusted() {
        val fingerprint = BuildConfig.SIGNING_CERTIFICATE_SHA256
        assertFalse(InstallIdentity("local.daybook", listOf(fingerprint), true).matchesPinnedCertificate())
        assertFalse(InstallIdentity("local.daybook", emptyList(), false).matchesPinnedCertificate())
        assertFalse(InstallIdentity("local.daybook", listOf(fingerprint, "0".repeat(64)), false).matchesPinnedCertificate())
    }

    @Test fun diagnosticIncludesVersionAndPublicIdentity() {
        val identity = InstallIdentity("local.daybook.debug", listOf("a".repeat(64)), false)
        val text = identity.summary()
        assertTrue(text.contains("local.daybook.debug"))
        assertTrue(text.contains(BuildConfig.VERSION_CODE.toString()))
        assertTrue(text.contains("a".repeat(64)))
    }
}
