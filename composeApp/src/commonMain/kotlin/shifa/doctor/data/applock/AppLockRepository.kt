package shifa.doctor.data.applock

import shifa.doctor.data.storage.AppLockStorage

/**
 * The app lock (01 §7.1): what screens may know about it. Storage failures read as "no PIN", the lock's documented
 * fallback (flow 01/12 §6). LOCK-01 grows this.
 */
class AppLockRepository(
    private val storage: () -> AppLockStorage = { AppLockStorage() },
) {
    val hasPin: Boolean get() = runCatching { storage().hasPin }.getOrDefault(false)
}
