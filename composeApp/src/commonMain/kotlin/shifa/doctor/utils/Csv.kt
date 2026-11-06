package shifa.doctor.utils

/**
 * RFC 4180 CSV (FIN-07): a field with a comma, quote or line break is quoted and its quotes doubled; CRLF line ends.
 * UTF-8 with a BOM so spreadsheet apps read Cyrillic and Uzbek letters right.
 */
fun csv(rows: List<List<String>>): ByteArray =
    BOM + rows.joinToString("\r\n", postfix = "\r\n") { row -> row.joinToString(",") { quote(it) } }.encodeToByteArray()

// The UTF-8 byte order mark as bytes (a "\uFEFF" literal doesn't survive every tool that touches the source).
private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

private fun quote(field: String): String =
    if (field.any {
            it == ',' || it == '"' || it == '\n' || it == '\r'
        }
    ) {
        "\"" + field.replace("\"", "\"\"") + "\""
    } else {
        field
    }
