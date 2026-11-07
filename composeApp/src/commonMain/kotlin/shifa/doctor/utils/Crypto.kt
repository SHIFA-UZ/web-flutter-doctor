package shifa.doctor.utils

/** SHA-256 of [bytes] (platform: `MessageDigest` / CommonCrypto). */
expect fun sha256(bytes: ByteArray): ByteArray

/** [size] bytes from the platform's secure random source (`SecureRandom` / `SecRandomCopyBytes`). */
expect fun secureRandomBytes(size: Int): ByteArray
