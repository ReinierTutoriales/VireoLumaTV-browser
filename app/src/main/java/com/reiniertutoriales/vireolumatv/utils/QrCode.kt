package com.reiniertutoriales.vireolumatv.utils

import kotlin.math.abs

/**
 * Pure-Kotlin QR code generator (ISO/IEC 18004, byte mode, error correction level M, versions 1–6).
 * Produces crisp boolean matrices for Compose [androidx.compose.foundation.Canvas] rendering without
 * third-party dependencies.
 */
internal class QrCode private constructor(val size: Int, private val modules: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]

    companion object {
        private data class VersionSpec(
            val version: Int,
            val totalCodewords: Int,
            val ecPerBlock: Int,
            val g1Blocks: Int,
            val g1Data: Int,
            val g2Blocks: Int = 0,
            val g2Data: Int = 0,
            val alignCenters: IntArray = intArrayOf(),
        ) {
            val dataCodewords: Int get() = g1Blocks * g1Data + g2Blocks * g2Data
        }

        // Error correction level M specs for versions 1–6 (ISO/IEC 18004 Table 9).
        private val VERSIONS = listOf(
            VersionSpec(1, 26, 10, 1, 16),
            VersionSpec(2, 44, 16, 1, 28, alignCenters = intArrayOf(6, 18)),
            VersionSpec(3, 70, 26, 1, 44, alignCenters = intArrayOf(6, 22)),
            VersionSpec(4, 100, 18, 2, 32, alignCenters = intArrayOf(6, 26)),
            VersionSpec(5, 134, 24, 2, 43, alignCenters = intArrayOf(6, 30)),
            VersionSpec(6, 172, 16, 4, 27, alignCenters = intArrayOf(6, 34)),
        )

        private val GF_EXP = IntArray(512)
        private val GF_LOG = IntArray(256)

        init {
            var x = 1
            for (i in 0 until 255) {
                GF_EXP[i] = x
                GF_LOG[x] = i
                x = x shl 1
                if (x and 0x100 != 0) x = x xor 0x11D
            }
            for (i in 255 until 512) GF_EXP[i] = GF_EXP[i - 255]
        }

        fun encode(text: String): QrCode {
            val payload = text.toByteArray(Charsets.UTF_8)
            require(payload.isNotEmpty()) { "QR payload cannot be empty" }
            val spec = VERSIONS.firstOrNull {
                val headerBits = if (it.version >= 10) 20 else 12
                payload.size * 8 + headerBits <= it.dataCodewords * 8
            } ?: throw IllegalArgumentException("Payload too long for QR v1–v10 (${payload.size} bytes)")

            val data = encodeData(payload, spec)
            val codewords = interleaveWithEc(data, spec)
            val size = spec.version * 4 + 17
            val base = BooleanArray(size * size)
            val reserved = BooleanArray(size * size)
            placeFunctionPatterns(size, spec, base, reserved)
            placeDataBits(size, codewords, base, reserved)

            var bestPenalty = Int.MAX_VALUE
            var bestModules = base
            for (mask in 0 until 8) {
                val candidate = applyMaskAndFormat(size, base, reserved, mask)
                val penalty = penaltyScore(size, candidate)
                if (penalty < bestPenalty) {
                    bestPenalty = penalty
                    bestModules = candidate
                }
            }
            return QrCode(size, bestModules)
        }

        private fun encodeData(payload: ByteArray, spec: VersionSpec): IntArray {
            val bits = ArrayList<Int>(spec.dataCodewords * 8)
            fun appendBits(value: Int, count: Int) {
                for (i in count - 1 downTo 0) bits.add((value ushr i) and 1)
            }
            appendBits(0b0100, 4) // Byte mode
            appendBits(payload.size, if (spec.version >= 10) 16 else 8)
            for (b in payload) appendBits(b.toInt() and 0xFF, 8)
            val totalBits = spec.dataCodewords * 8
            val terminator = minOf(4, totalBits - bits.size)
            appendBits(0, terminator)
            while (bits.size % 8 != 0) bits.add(0)
            val padBytes = intArrayOf(0xEC, 0x11)
            var padIndex = 0
            while (bits.size < totalBits) {
                appendBits(padBytes[padIndex and 1], 8)
                padIndex++
            }
            return IntArray(spec.dataCodewords) { idx ->
                var v = 0
                for (b in 0 until 8) v = (v shl 1) or bits[idx * 8 + b]
                v
            }
        }

        private fun interleaveWithEc(data: IntArray, spec: VersionSpec): IntArray {
            val gen = rsGenerator(spec.ecPerBlock)
            val totalBlocks = spec.g1Blocks + spec.g2Blocks
            val dataBlocks = ArrayList<IntArray>(totalBlocks)
            val ecBlocks = ArrayList<IntArray>(totalBlocks)
            var offset = 0
            for (i in 0 until totalBlocks) {
                val len = if (i < spec.g1Blocks) spec.g1Data else spec.g2Data
                val block = data.copyOfRange(offset, offset + len)
                offset += len
                dataBlocks.add(block)
                ecBlocks.add(rsRemainder(block, gen, spec.ecPerBlock))
            }
            val result = IntArray(spec.totalCodewords)
            var out = 0
            val maxDataLen = maxOf(spec.g1Data, spec.g2Data)
            for (i in 0 until maxDataLen) {
                for (block in dataBlocks) {
                    if (i < block.size) result[out++] = block[i]
                }
            }
            for (i in 0 until spec.ecPerBlock) {
                for (ec in ecBlocks) result[out++] = ec[i]
            }
            return result
        }

        private fun rsGenerator(degree: Int): IntArray {
            var poly = intArrayOf(1)
            for (i in 0 until degree) {
                val next = IntArray(poly.size + 1)
                val factor = GF_EXP[i]
                for (j in poly.indices) {
                    next[j] = next[j] xor poly[j]
                    next[j + 1] = next[j + 1] xor gfMul(poly[j], factor)
                }
                poly = next
            }
            return poly
        }

        private fun rsRemainder(data: IntArray, gen: IntArray, ecCount: Int): IntArray {
            val buf = IntArray(data.size + ecCount)
            data.copyInto(buf)
            for (i in data.indices) {
                val coef = buf[i]
                if (coef != 0) {
                    for (j in gen.indices) {
                        buf[i + j] = buf[i + j] xor gfMul(gen[j], coef)
                    }
                }
            }
            return buf.copyOfRange(data.size, buf.size)
        }

        private fun gfMul(a: Int, b: Int): Int =
            if (a == 0 || b == 0) 0 else GF_EXP[GF_LOG[a] + GF_LOG[b]]

        private fun placeFunctionPatterns(size: Int, spec: VersionSpec, grid: BooleanArray, reserved: BooleanArray) {
            fun setModule(x: Int, y: Int, black: Boolean) {
                val idx = y * size + x
                grid[idx] = black
                reserved[idx] = true
            }
            fun placeFinder(ox: Int, oy: Int) {
                for (dy in -1..7) {
                    for (dx in -1..7) {
                        val x = ox + dx
                        val y = oy + dy
                        if (x !in 0 until size || y !in 0 until size) continue
                        val inOuter = dx in 0..6 && dy in 0..6
                        val onBorder = dx == 0 || dx == 6 || dy == 0 || dy == 6
                        val inCenter = dx in 2..4 && dy in 2..4
                        setModule(x, y, inOuter && (onBorder || inCenter))
                    }
                }
            }
            placeFinder(0, 0)
            placeFinder(size - 7, 0)
            placeFinder(0, size - 7)

            // Alignment patterns
            val centers = spec.alignCenters
            for (cy in centers) {
                for (cx in centers) {
                    if (reserved[cy * size + cx]) continue
                    for (dy in -2..2) {
                        for (dx in -2..2) {
                            val black = abs(dx) == 2 || abs(dy) == 2 || (dx == 0 && dy == 0)
                            setModule(cx + dx, cy + dy, black)
                        }
                    }
                }
            }

            // Timing patterns
            for (i in 8 until size - 8) {
                val black = (i and 1) == 0
                if (!reserved[6 * size + i]) setModule(i, 6, black)
                if (!reserved[i * size + 6]) setModule(6, i, black)
            }

            // Dark module
            setModule(8, size - 8, true)

            // Reserve format info areas
            for (i in 0..8) {
                if (i != 6) {
                    reserved[8 * size + i] = true
                    reserved[i * size + 8] = true
                }
            }
            for (i in 0 until 8) reserved[8 * size + (size - 1 - i)] = true
            for (i in 0 until 7) reserved[(size - 1 - i) * size + 8] = true
        }

        private fun placeDataBits(size: Int, codewords: IntArray, grid: BooleanArray, reserved: BooleanArray) {
            var bitIndex = 0
            val totalBits = codewords.size * 8
            var upward = true
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right--
                for (step in 0 until size) {
                    val y = if (upward) size - 1 - step else step
                    for (dx in 0..1) {
                        val x = right - dx
                        val idx = y * size + x
                        if (!reserved[idx]) {
                            val bit = if (bitIndex < totalBits) {
                                ((codewords[bitIndex ushr 3] ushr (7 - (bitIndex and 7))) and 1) != 0
                            } else {
                                false
                            }
                            grid[idx] = bit
                            bitIndex++
                        }
                    }
                }
                upward = !upward
                right -= 2
            }
        }

        private fun applyMaskAndFormat(size: Int, base: BooleanArray, reserved: BooleanArray, mask: Int): BooleanArray {
            val out = base.copyOf()
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val idx = y * size + x
                    if (!reserved[idx] && maskBit(mask, x, y)) {
                        out[idx] = !out[idx]
                    }
                }
            }
            // Format bits: EC level M = 00, mask = 3 bits -> 5-bit data, BCH(15, 5), XOR 0x5412.
            val data = mask // level M is 0b00
            var rem = data shl 10
            for (i in 4 downTo 0) {
                if ((rem ushr (i + 10)) and 1 != 0) rem = rem xor (0x537 shl i)
            }
            val format = ((data shl 10) or rem) xor 0x5412
            for (i in 0..5) out[8 * size + i] = ((format ushr (14 - i)) and 1) != 0
            out[8 * size + 7] = ((format ushr 8) and 1) != 0
            out[8 * size + 8] = ((format ushr 7) and 1) != 0
            out[7 * size + 8] = ((format ushr 6) and 1) != 0
            for (i in 9..14) out[(14 - i) * size + 8] = ((format ushr (14 - i)) and 1) != 0

            for (i in 0..6) out[(size - 1 - i) * size + 8] = ((format ushr (14 - i)) and 1) != 0
            for (i in 7..14) out[8 * size + (size - 15 + i)] = ((format ushr (14 - i)) and 1) != 0
            return out
        }

        private fun maskBit(mask: Int, x: Int, y: Int): Boolean = when (mask) {
            0 -> ((x + y) and 1) == 0
            1 -> (y and 1) == 0
            2 -> (x % 3) == 0
            3 -> ((x + y) % 3) == 0
            4 -> (((y / 2) + (x / 3)) and 1) == 0
            5 -> ((x * y) % 2 + (x * y) % 3) == 0
            6 -> ((((x * y) % 2) + ((x * y) % 3)) and 1) == 0
            else -> ((((x + y) % 2) + ((x * y) % 3)) and 1) == 0
        }

        private fun penaltyScore(size: Int, grid: BooleanArray): Int {
            var score = 0
            // Rule 1: 5+ consecutive same-color modules in rows/columns
            for (y in 0 until size) {
                var run = 1
                for (x in 1 until size) {
                    if (grid[y * size + x] == grid[y * size + x - 1]) {
                        run++
                        if (run == 5) score += 3 else if (run > 5) score++
                    } else run = 1
                }
            }
            for (x in 0 until size) {
                var run = 1
                for (y in 1 until size) {
                    if (grid[y * size + x] == grid[(y - 1) * size + x]) {
                        run++
                        if (run == 5) score += 3 else if (run > 5) score++
                    } else run = 1
                }
            }
            // Rule 2: 2x2 blocks
            for (y in 0 until size - 1) {
                for (x in 0 until size - 1) {
                    val c = grid[y * size + x]
                    if (grid[y * size + x + 1] == c && grid[(y + 1) * size + x] == c && grid[(y + 1) * size + x + 1] == c) {
                        score += 3
                    }
                }
            }
            // Rule 3: 1:1:3:1:1 finder-like pattern with 4 light modules
            for (y in 0 until size) {
                for (x in 0..size - 11) {
                    if (matchesFinderPenaltyRow(grid, y * size + x, 1) || matchesFinderPenaltyRevRow(grid, y * size + x, 1)) {
                        score += 40
                    }
                }
            }
            for (x in 0 until size) {
                for (y in 0..size - 11) {
                    if (matchesFinderPenaltyRow(grid, y * size + x, size) || matchesFinderPenaltyRevRow(grid, y * size + x, size)) {
                        score += 40
                    }
                }
            }
            // Rule 4: dark/light balance
            var dark = 0
            for (m in grid) if (m) dark++
            val percent = (dark * 100) / grid.size
            val k = abs(percent - 50) / 5
            score += k * 10
            return score
        }

        private fun matchesFinderPenaltyRow(grid: BooleanArray, start: Int, stride: Int): Boolean =
            grid[start] && !grid[start + stride] && grid[start + 2 * stride] &&
                grid[start + 3 * stride] && grid[start + 4 * stride] && !grid[start + 5 * stride] &&
                grid[start + 6 * stride] && !grid[start + 7 * stride] && !grid[start + 8 * stride] &&
                !grid[start + 9 * stride] && !grid[start + 10 * stride]

        private fun matchesFinderPenaltyRevRow(grid: BooleanArray, start: Int, stride: Int): Boolean =
            !grid[start] && !grid[start + stride] && !grid[start + 2 * stride] &&
                !grid[start + 3 * stride] && grid[start + 4 * stride] && !grid[start + 5 * stride] &&
                grid[start + 6 * stride] && grid[start + 7 * stride] && grid[start + 8 * stride] &&
                !grid[start + 9 * stride] && grid[start + 10 * stride]
    }
}
