package shifa.doctor.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class, ExperimentalUnsignedTypes::class)
actual fun sha256(bytes: ByteArray): ByteArray {
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    val input = if (bytes.isEmpty()) ByteArray(1) else bytes // addressOf(0) needs an element
    input.usePinned { data ->
        digest.usePinned { out -> CC_SHA256(data.addressOf(0), bytes.size.convert(), out.addressOf(0)) }
    }
    return digest.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
actual fun secureRandomBytes(size: Int): ByteArray {
    val out = ByteArray(size)
    out.usePinned { check(SecRandomCopyBytes(kSecRandomDefault, size.convert(), it.addressOf(0)) == errSecSuccess) }
    return out
}
