package com.projecttrack.app.data.backup

import java.security.MessageDigest

/** SHA-256 计算与十六进制表示工具。 */
internal object BackupHashing {
    private const val HEX_CHARS = "0123456789abcdef"

    fun newSha256(): MessageDigest = MessageDigest.getInstance("SHA-256")

    fun hex(digest: MessageDigest): String = toHex(digest.digest())

    fun toHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xFF
            append(HEX_CHARS[value ushr 4])
            append(HEX_CHARS[value and 0x0F])
        }
    }

    fun isSha256Hex(value: String): Boolean =
        value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}
