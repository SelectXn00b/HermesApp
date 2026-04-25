"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.safeAtobBase64 = safeAtobBase64;
exports.safeBtoaBase64 = safeBtoaBase64;
const BASE64_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
function normalizeBase64Input(value) {
    const cleaned = String(value || '')
        .replace(/\s+/g, '')
        .replace(/-/g, '+')
        .replace(/_/g, '/');
    if (!cleaned) {
        return '';
    }
    const remainder = cleaned.length % 4;
    if (remainder === 1) {
        throw new Error('Invalid base64 string');
    }
    return remainder === 0 ? cleaned : cleaned + '='.repeat(4 - remainder);
}
function decodeBase64ToBytes(value) {
    const normalized = normalizeBase64Input(value);
    const bytes = [];
    for (let i = 0; i < normalized.length; i += 4) {
        const c1 = normalized[i];
        const c2 = normalized[i + 1];
        const c3 = normalized[i + 2];
        const c4 = normalized[i + 3];
        const v1 = BASE64_ALPHABET.indexOf(c1);
        const v2 = BASE64_ALPHABET.indexOf(c2);
        const v3 = c3 === '=' ? 0 : BASE64_ALPHABET.indexOf(c3);
        const v4 = c4 === '=' ? 0 : BASE64_ALPHABET.indexOf(c4);
        if (v1 < 0 || v2 < 0 || (c3 !== '=' && v3 < 0) || (c4 !== '=' && v4 < 0)) {
            throw new Error('Invalid base64 string');
        }
        const chunk = (v1 << 18) | (v2 << 12) | (v3 << 6) | v4;
        bytes.push((chunk >> 16) & 0xff);
        if (c3 !== '=') {
            bytes.push((chunk >> 8) & 0xff);
        }
        if (c4 !== '=') {
            bytes.push(chunk & 0xff);
        }
    }
    return bytes;
}
function encodeBytesToBase64(bytes) {
    let output = '';
    for (let i = 0; i < bytes.length; i += 3) {
        const b1 = bytes[i];
        const hasB2 = i + 1 < bytes.length;
        const hasB3 = i + 2 < bytes.length;
        const b2 = hasB2 ? bytes[i + 1] : 0;
        const b3 = hasB3 ? bytes[i + 2] : 0;
        const chunk = (b1 << 16) | (b2 << 8) | b3;
        output += BASE64_ALPHABET[(chunk >> 18) & 0x3f];
        output += BASE64_ALPHABET[(chunk >> 12) & 0x3f];
        output += hasB2 ? BASE64_ALPHABET[(chunk >> 6) & 0x3f] : '=';
        output += hasB3 ? BASE64_ALPHABET[chunk & 0x3f] : '=';
    }
    return output;
}
function utf8Encode(text) {
    const bytes = [];
    const input = String(text ?? '');
    for (let i = 0; i < input.length; i++) {
        const codePoint = input.codePointAt(i);
        if (codePoint == null) {
            continue;
        }
        if (codePoint > 0xffff) {
            i += 1;
        }
        if (codePoint <= 0x7f) {
            bytes.push(codePoint);
        }
        else if (codePoint <= 0x7ff) {
            bytes.push(0xc0 | (codePoint >> 6), 0x80 | (codePoint & 0x3f));
        }
        else if (codePoint <= 0xffff) {
            bytes.push(0xe0 | (codePoint >> 12), 0x80 | ((codePoint >> 6) & 0x3f), 0x80 | (codePoint & 0x3f));
        }
        else {
            bytes.push(0xf0 | (codePoint >> 18), 0x80 | ((codePoint >> 12) & 0x3f), 0x80 | ((codePoint >> 6) & 0x3f), 0x80 | (codePoint & 0x3f));
        }
    }
    return bytes;
}
function readContinuationByte(bytes, index) {
    const value = bytes[index];
    if (value == null || (value & 0xc0) !== 0x80) {
        throw new Error('Invalid UTF-8 sequence');
    }
    return value & 0x3f;
}
function utf8Decode(bytes) {
    let output = '';
    for (let i = 0; i < bytes.length; i++) {
        const first = bytes[i];
        if (first == null) {
            break;
        }
        if (first <= 0x7f) {
            output += String.fromCodePoint(first);
            continue;
        }
        if ((first & 0xe0) === 0xc0) {
            const codePoint = ((first & 0x1f) << 6) | readContinuationByte(bytes, ++i);
            output += String.fromCodePoint(codePoint);
            continue;
        }
        if ((first & 0xf0) === 0xe0) {
            const codePoint = ((first & 0x0f) << 12) |
                (readContinuationByte(bytes, ++i) << 6) |
                readContinuationByte(bytes, ++i);
            output += String.fromCodePoint(codePoint);
            continue;
        }
        if ((first & 0xf8) === 0xf0) {
            const codePoint = ((first & 0x07) << 18) |
                (readContinuationByte(bytes, ++i) << 12) |
                (readContinuationByte(bytes, ++i) << 6) |
                readContinuationByte(bytes, ++i);
            output += String.fromCodePoint(codePoint);
            continue;
        }
        throw new Error('Invalid UTF-8 sequence');
    }
    return output;
}
function safeAtobBase64(b64) {
    return utf8Decode(decodeBase64ToBytes(b64));
}
function safeBtoaBase64(text) {
    return encodeBytesToBase64(utf8Encode(String(text ?? '')));
}
