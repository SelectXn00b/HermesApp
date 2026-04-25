package com.operit.apkreverse.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ElfSupport {
    private ElfSupport() {
    }

    static List<PrintableString> extractPrintableStrings(byte[] bytes, int minLength) {
        List<PrintableString> results = new ArrayList<>();
        int start = -1;
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < bytes.length; index += 1) {
            int value = unsignedByte(bytes[index]);
            if (value >= 32 && value <= 126) {
                if (start < 0) start = index;
                builder.append((char) value);
                continue;
            }
            if (start >= 0 && builder.length() >= minLength) {
                results.add(new PrintableString(start, builder.toString()));
            }
            start = -1;
            builder.setLength(0);
        }
        if (start >= 0 && builder.length() >= minLength) {
            results.add(new PrintableString(start, builder.toString()));
        }
        return results;
    }

    static byte[] parseHexPattern(String query) {
        String cleaned = query.trim().replaceAll("(?i)0x", "").replaceAll("[^0-9a-fA-F]", "");
        if (cleaned.isEmpty() || cleaned.length() % 2 != 0) {
            throw new IllegalArgumentException("hex_bytes query must contain an even number of hex digits");
        }
        byte[] output = new byte[cleaned.length() / 2];
        for (int index = 0; index < cleaned.length(); index += 2) {
            output[index / 2] = (byte) Integer.parseInt(cleaned.substring(index, index + 2), 16);
        }
        return output;
    }

    static List<Integer> findHexPatternOffsets(byte[] bytes, byte[] pattern, int maxResults) {
        List<Integer> matches = new ArrayList<>();
        if (pattern.length == 0) {
            return matches;
        }
        int limit = bytes.length - pattern.length + 1;
        for (int index = 0; index < limit && matches.size() < maxResults; index += 1) {
            boolean matched = true;
            for (int patternIndex = 0; patternIndex < pattern.length; patternIndex += 1) {
                if (unsignedByte(bytes[index + patternIndex]) != unsignedByte(pattern[patternIndex])) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                matches.add(index);
            }
        }
        return matches;
    }

    static List<String> buildPseudoDisassemblyAround(byte[] bytes, Long offset, int before, int after) {
        if (offset == null || offset < 0 || offset >= bytes.length) {
            return new ArrayList<>();
        }
        int start = (int) Math.max(0L, offset - before);
        int end = (int) Math.min(bytes.length, offset + after);
        byte[] window = slice(bytes, start, end);
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < window.length; index += 4) {
            int chunkLength = Math.min(4, window.length - index);
            lines.add("0x" + toHex(start + index, 8) + ": db " + hexBytes(window, index, chunkLength));
        }
        return lines;
    }

    static String windowHex(byte[] bytes, long offset, int width) {
        int start = (int) Math.max(0L, offset - 16L);
        int end = (int) Math.min(bytes.length, offset + width);
        byte[] window = slice(bytes, start, end);
        return hexBytes(window, 0, window.length);
    }

    static String hexBytes(byte[] bytes, int start, int length) {
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(bytes.length, start + length);
        for (int index = start; index < limit; index += 1) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(toHex(unsignedByte(bytes[index]), 2));
        }
        return builder.toString();
    }

    static String toHex(long value, int width) {
        String hex = Long.toHexString(Math.max(0L, value));
        if (hex.length() >= width) {
            return hex;
        }
        StringBuilder builder = new StringBuilder(width);
        for (int index = hex.length(); index < width; index += 1) {
            builder.append('0');
        }
        builder.append(hex);
        return builder.toString();
    }

    static Long parseLongOffset(String query) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return normalized.startsWith("0x") ? Long.parseLong(normalized.substring(2), 16) : Long.parseLong(normalized, 10);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static ElfMetadata parseElfMetadata(byte[] bytes) {
        if (bytes.length < 64 || unsignedByte(bytes[0]) != 0x7f || unsignedByte(bytes[1]) != 0x45
                || unsignedByte(bytes[2]) != 0x4c || unsignedByte(bytes[3]) != 0x46) {
            return null;
        }
        boolean is64 = unsignedByte(bytes[4]) == 2;
        boolean littleEndian = unsignedByte(bytes[5]) == 1;
        if (!littleEndian) {
            return null;
        }

        long sectionOffset = is64 ? readU64(bytes, 40) : readU32(bytes, 32);
        int sectionEntrySize = (int) (is64 ? readU16(bytes, 58) : readU16(bytes, 46));
        int sectionCount = (int) (is64 ? readU16(bytes, 60) : readU16(bytes, 48));
        int sectionNameIndex = (int) (is64 ? readU16(bytes, 62) : readU16(bytes, 50));
        if (sectionOffset <= 0 || sectionEntrySize <= 0 || sectionCount <= 0) {
            return null;
        }

        List<ElfSection> sections = new ArrayList<>();
        for (int index = 0; index < sectionCount; index += 1) {
            long offset = sectionOffset + (long) index * sectionEntrySize;
            if (offset + sectionEntrySize > bytes.length) {
                break;
            }
            sections.add(is64
                    ? new ElfSection(readU32(bytes, offset), readU32(bytes, offset + 4), readU64(bytes, offset + 8), readU64(bytes, offset + 16), readU64(bytes, offset + 24), readU64(bytes, offset + 32), readU32(bytes, offset + 40), readU32(bytes, offset + 44), readU64(bytes, offset + 48), readU64(bytes, offset + 56))
                    : new ElfSection(readU32(bytes, offset), readU32(bytes, offset + 4), readU32(bytes, offset + 8), readU32(bytes, offset + 12), readU32(bytes, offset + 16), readU32(bytes, offset + 20), readU32(bytes, offset + 24), readU32(bytes, offset + 28), readU32(bytes, offset + 32), readU32(bytes, offset + 36)));
        }
        if (sectionNameIndex < 0 || sectionNameIndex >= sections.size()) {
            return null;
        }

        long namesOffset = sections.get(sectionNameIndex).fileOffset;
        for (ElfSection section : sections) {
            section.name = readString(bytes, namesOffset, section.nameIndex);
        }

        List<ElfSymbol> symbols = new ArrayList<>();
        for (ElfSection section : sections) {
            if (!((section.type == 2 || section.type == 11) && section.entrySize > 0)) {
                continue;
            }
            if (section.link < 0 || section.link >= sections.size()) {
                continue;
            }
            ElfSection stringSection = sections.get((int) section.link);
            long count = section.size / section.entrySize;
            for (int entryIndex = 0; entryIndex < count; entryIndex += 1) {
                long entryOffset = section.fileOffset + (long) entryIndex * section.entrySize;
                if (entryOffset + section.entrySize > bytes.length) {
                    break;
                }
                String name = readString(bytes, stringSection.fileOffset, readU32(bytes, entryOffset));
                if (name == null || name.isEmpty()) {
                    continue;
                }
                long value = is64 ? readU64(bytes, entryOffset + 8) : readU32(bytes, entryOffset + 4);
                symbols.add(new ElfSymbol(name, value));
            }
        }
        return new ElfMetadata(is64, sections, symbols);
    }

    private static int unsignedByte(byte value) {
        return value & 0xff;
    }

    private static byte[] slice(byte[] bytes, int start, int end) {
        int safeStart = Math.max(0, start);
        int safeEnd = Math.max(safeStart, Math.min(bytes.length, end));
        byte[] output = new byte[safeEnd - safeStart];
        System.arraycopy(bytes, safeStart, output, 0, output.length);
        return output;
    }

    private static long readU16(byte[] bytes, long offset) {
        int base = (int) offset;
        return unsignedByte(bytes[base]) | (unsignedByte(bytes[base + 1]) << 8);
    }

    private static long readU32(byte[] bytes, long offset) {
        int base = (int) offset;
        return ((long) unsignedByte(bytes[base]))
                | ((long) unsignedByte(bytes[base + 1]) << 8)
                | ((long) unsignedByte(bytes[base + 2]) << 16)
                | ((long) unsignedByte(bytes[base + 3]) << 24);
    }

    private static long readU64(byte[] bytes, long offset) {
        long low = readU32(bytes, offset);
        long high = readU32(bytes, offset + 4);
        return (high << 32) | (low & 0xffffffffL);
    }

    private static String readString(byte[] bytes, long tableOffset, long index) {
        long start = tableOffset + index;
        if (start < 0 || start >= bytes.length) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (long cursor = start; cursor < bytes.length; cursor += 1) {
            int value = unsignedByte(bytes[(int) cursor]);
            if (value == 0) {
                break;
            }
            builder.append((char) value);
        }
        return builder.toString();
    }

    static final class PrintableString {
        final int offset;
        final String text;

        PrintableString(int offset, String text) {
            this.offset = offset;
            this.text = text;
        }
    }

    static final class ElfSection {
        final long nameIndex;
        final long type;
        final long flags;
        final long address;
        final long fileOffset;
        final long size;
        final long link;
        final long info;
        final long align;
        final long entrySize;
        String name;

        ElfSection(long nameIndex, long type, long flags, long address, long fileOffset, long size, long link, long info, long align, long entrySize) {
            this.nameIndex = nameIndex;
            this.type = type;
            this.flags = flags;
            this.address = address;
            this.fileOffset = fileOffset;
            this.size = size;
            this.link = link;
            this.info = info;
            this.align = align;
            this.entrySize = entrySize;
            this.name = "";
        }
    }

    static final class ElfSymbol {
        final String name;
        final long address;

        ElfSymbol(String name, long address) {
            this.name = name;
            this.address = address;
        }
    }

    static final class ElfMetadata {
        final boolean is64;
        final List<ElfSection> sections;
        final List<ElfSymbol> symbols;

        ElfMetadata(boolean is64, List<ElfSection> sections, List<ElfSymbol> symbols) {
            this.is64 = is64;
            this.sections = sections;
            this.symbols = symbols;
        }

        Long mapAddressToOffset(long address) {
            for (ElfSection section : sections) {
                if (section.size <= 0) {
                    continue;
                }
                if (address >= section.address && address < section.address + section.size) {
                    return section.fileOffset + (address - section.address);
                }
            }
            return null;
        }
    }
}
