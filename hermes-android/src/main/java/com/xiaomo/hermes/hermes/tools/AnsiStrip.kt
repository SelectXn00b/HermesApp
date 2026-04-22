package com.xiaomo.hermes.hermes.tools

/**
 * Strip ANSI escape sequences from subprocess output.
 * Ported from ansi_strip.py
 *
 * Covers the full ECMA-48 spec: CSI (including private-mode `?` prefix,
 * colon-separated params, intermediate bytes), OSC (BEL and ST terminators),
 * DCS/SOS/PM/APC string sequences, nF multi-byte escapes, Fp/Fe/Fs
 * single-byte escapes, and 8-bit C1 control characters.
 */

val _ANSI_ESCAPE_RE = Regex(
    "\\x1b" +
    "(?:" +
        "\\[[\\x30-\\x3f]*[\\x20-\\x2f]*[\\x40-\\x7e]" +     // CSI sequence
        "|\\][\\s\\S]*?(?:\\x07|\\x1b\\\\)" +                   // OSC (BEL or ST terminator)
        "|[PX^_][\\s\\S]*?(?:\\x1b\\\\)" +                      // DCS/SOS/PM/APC strings
        "|[\\x20-\\x2f]+[\\x30-\\x7e]" +                        // nF escape sequences
        "|[\\x30-\\x7e]" +                                       // Fp/Fe/Fs single-byte
    ")" +
    "|\\x9b[\\x30-\\x3f]*[\\x20-\\x2f]*[\\x40-\\x7e]" +        // 8-bit CSI
    "|\\x9d[\\s\\S]*?(?:\\x07|\\x9c)" +                          // 8-bit OSC
    "|[\\x80-\\x9f]",                                             // Other 8-bit C1 controls
    RegexOption.DOT_MATCHES_ALL,
)

val _HAS_ESCAPE = Regex("[\\x1b\\x80-\\x9f]")

fun stripAnsi(text: String): String {
    if (text.isEmpty() || !_HAS_ESCAPE.containsMatchIn(text)) return text
    return _ANSI_ESCAPE_RE.replace(text, "")
}
