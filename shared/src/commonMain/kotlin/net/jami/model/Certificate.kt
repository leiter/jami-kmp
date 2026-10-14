/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.jami.model

/**
 * Trust status of a pinned certificate, as the daemon names it
 * (`libjami::Certificate::Status`, `security_const.h`). These strings cross the bridge verbatim,
 * so they are constants rather than an enum.
 */
object CertificateStatus {
    /** Pinned but not judged: verified the ordinary way. */
    const val UNDEFINED = "UNDEFINED"

    /** Accepted even where normal verification would reject it. */
    const val ALLOWED = "ALLOWED"

    /** Refused: connections presenting this certificate are dropped. */
    const val BANNED = "BANNED"

    val all = listOf(UNDEFINED, ALLOWED, BANNED)
}

/** Keys of the detail map returned by `getCertificateDetails` (`Certificate::DetailsNames`). */
object CertificateDetail {
    const val COMMON_NAME = "CN"
    const val ORGANISATION = "O"
    const val ISSUER_COMMON_NAME = "ISSUER_CN"
    const val ISSUER_ORGANISATION = "ISSUER_O"
    const val EXPIRATION_DATE = "EXPIRATION_DATE"
    const val ACTIVATION_DATE = "ACTIVATION_DATE"
    const val SHA1_FINGERPRINT = "SHA1_FINGERPRINT"
    const val SERIAL_NUMBER = "SERIAL_NUMBER"
    const val SIGNATURE_ALGORITHM = "SIGNATURE_ALGORITHM"
    const val IS_CA = "IS_CA"
    const val PUBLIC_KEY_ID = "PUBLIC_KEY_ID"
}

/** Keys of the check map returned by `validateCertificate` (`Certificate::ChecksNames`). */
object CertificateCheck {
    /**
     * **The name is libjami's and it is a misnomer.** The check behind it is
     * `TlsValidator::notExpired()` (`tlsvalidator.cpp:768-776`), which returns PASSED when the
     * certificate is *within* its validity and FAILED when it has expired. Read it as
     * "not expired", never as "is expired".
     */
    const val EXPIRED = "EXPIRED"
    const val NOT_REVOKED = "NOT_REVOKED"
    const val KNOWN_AUTHORITY = "KNOWN_AUTHORITY"
    const val VALID_AUTHORITY = "VALID_AUTHORITY"
    const val NOT_SELF_SIGNED = "NOT_SELF_SIGNED"
    const val STRONG_SIGNING = "STRONG_SIGNING"
    const val NOT_ACTIVATED = "NOT_ACTIVATED"
    const val HAS_PRIVATE_KEY = "HAS_PRIVATE_KEY"

    /** Values a check can take. */
    const val PASSED = "PASSED"
    const val FAILED = "FAILED"
    const val UNSUPPORTED = "UNSUPPORTED"
}

/**
 * A certificate pinned on an account, as shown in certificate management.
 *
 * [details] and [checks] are kept as the daemon returned them so nothing is lost in translation:
 * the named properties are the handful the UI shows, and the full maps back the detail screen.
 */
data class Certificate(
    /**
     * The id to act on. The daemon's store holds each certificate under several ids (see [ids]);
     * this is the longest, because that is the one `unpinCertificate` can actually delete — the
     * stored file is named after it (`certstore.cpp:384`).
     */
    val id: String,
    /**
     * Every id the daemon's store returns for this one certificate. `CertificateStore` emplaces
     * both the short and the long id, and walks the issuer chain doing the same
     * (`certstore.cpp:299-302`, `:366-380`), so `getPinnedCertificates()` lists two or more ids
     * per certificate. They must be collapsed into one row, and a trust change has to be applied
     * to all of them or the ids disagree about the certificate's status.
     */
    val ids: List<String> = listOf(id),
    val status: String = CertificateStatus.UNDEFINED,
    val details: Map<String, String> = emptyMap(),
    val checks: Map<String, String> = emptyMap(),
    /** Set when this certificate was pinned from a file, which is the only way to unpin it. */
    val pinnedFromPath: String? = null,
) {
    /** Subject common name, falling back to the id so a row is never blank. */
    val commonName: String
        get() = details[CertificateDetail.COMMON_NAME]?.takeIf { it.isNotBlank() }
            ?: details[CertificateDetail.ORGANISATION]?.takeIf { it.isNotBlank() }
            ?: id

    val issuer: String?
        get() = details[CertificateDetail.ISSUER_COMMON_NAME]

    val expirationDate: String?
        get() = details[CertificateDetail.EXPIRATION_DATE]

    val isCertificateAuthority: Boolean
        get() = details[CertificateDetail.IS_CA]?.lowercase().let { it == "true" || it == "1" }

    /**
     * Whether the daemon's own checks found a problem. A check the daemon reports as UNSUPPORTED
     * is not a failure — it could not run it.
     */
    val hasFailedChecks: Boolean
        get() = checks.any { (_, value) -> value == CertificateCheck.FAILED }

    /**
     * Whether the certificate is past its validity.
     *
     * The daemon's check is `notExpired` despite being keyed EXPIRED, so a **FAILED** value is
     * what means expired. Reading PASSED as expired — which this did until 2026-10-14 — labelled
     * every valid certificate "Expired".
     */
    val isExpired: Boolean
        get() = checks[CertificateCheck.EXPIRED] == CertificateCheck.FAILED

    /**
     * Stable identity of the certificate itself, used to collapse the store's several ids into
     * one row. The fingerprint is preferred; the public key id is the fallback, and the id itself
     * the last resort (which groups nothing, leaving the duplicate visible rather than merging
     * two certificates that might not be the same).
     */
    val identity: String
        get() = details[CertificateDetail.SHA1_FINGERPRINT]?.takeIf { it.isNotBlank() }
            ?: details[CertificateDetail.PUBLIC_KEY_ID]?.takeIf { it.isNotBlank() }
            ?: id

    /** Names of the checks that failed, for the detail screen. */
    val failedChecks: List<String>
        get() = checks.filterValues { it == CertificateCheck.FAILED }.keys.sorted()
}
