/*
 * Copyright (c) 2023 badawoll
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.badawoll

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * FRSCContainer is a container for reading and writing FRSC files.
 * It is a simple container that maps an integer to a string, and can be used to store
 * strings that are referenced by an integer ID.
 *
 * The format is as follows (little endian):
 *  - Header:
 *      - Magic value (4 bytes): 0x43535246
 *      - Number of blocks (4 bytes): number of blocks in the file. Each block contains a contiguous range of strings.
 *      - String start offset (4 bytes): offset of the first string pointer in the file.
 *  - Block information (12 bytes * number of blocks):
 *      - First number in block (2 bytes): the first number in the block.
 *      - Number of entries in block (2 bytes): the number of entries in the block.
 *      - Block sequence (2 bytes): the sequence of the block.
 *  - String pointers (4 bytes * number of strings): the offset of the string in the file.
 *  - Strings (variable): the strings in the file, each preceded by a 2-byte length.
 */
class FRSCContainer : Iterable<Map.Entry<Int, String>> {
    private val data: MutableMap<Int, String>
    private val charset: Charset

    public constructor(byteData: ByteArray, charset: Charset = Charsets.UTF_8) {
        this.charset = charset

        if (byteData.size < 12) {
            throw IllegalArgumentException("Invalid data size")
        }

        val buffer = ByteBuffer.wrap(byteData)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.getInt(0).let {
            if (it != MAGIC_VALUE) {
                throw IllegalArgumentException("Invalid magic value")
            }
        }

        val numBlocks = buffer.getInt(4)
        if (numBlocks < 0) {
            throw IllegalArgumentException("Number of blocks is negative")
        }

        val stringStartOffset = buffer.getInt(8)
        if (stringStartOffset < 12) {
            throw IllegalArgumentException("Invalid string start offset")
        }

        this.data = mutableMapOf()

        for (i in 0 until numBlocks) {
            val ptr = i * BLOCK_ENTRY_SIZE + HEADER_SIZE

            val firstNumberInBlock = buffer.getShort(ptr)
            val numEntriesInBlock = buffer.getShort(ptr + 2)
            val blockSeq = buffer.getShort(ptr + 4)

            println("Block $i: startOffset=$firstNumberInBlock, numEntries=$numEntriesInBlock, seq=$blockSeq")

            for (j in 0 until numEntriesInBlock) {
                val stringEntryPointer = buffer.getInt(stringStartOffset + (blockSeq + j) * 4)
                val stringEntryLength = buffer.getShort(stringEntryPointer)
                val stringOffset = stringEntryPointer + 2

                val string = String(byteData, stringOffset, stringEntryLength.toInt(), charset)
                this.data[firstNumberInBlock + j] = string
            }
        }
    }

    public constructor(data: Map<Int, String> = mapOf(), charset: Charset = Charsets.UTF_8) {
        this.data = data.toMutableMap()
        this.charset = charset
    }

    public operator fun get(id: Int): String? {
        return data[id]
    }

    public val size: Int
        get() = data.size

    public val isEmpty: Boolean
        get() = data.isEmpty()

    public operator fun set(id: Int, str: String) {
        data[id] = str
    }

    public operator fun contains(id: Int): Boolean {
        return data.containsKey(id)
    }

    override fun iterator(): Iterator<Map.Entry<Int, String>> {
        return data.iterator()
    }

    public fun dump(): ByteArray {
        val sortedMap = data.toSortedMap().mapValues {
            it.value.toByteArray(charset)
        }.toSortedMap()

        val blocks = getBlocks(sortedMap.keys)

        val outBuffer = ByteBuffer.allocate(HEADER_SIZE +
                blocks.size * BLOCK_ENTRY_SIZE +
                sortedMap.size * 4 +
                sortedMap.values.sumOf { it.size + 2 })
        outBuffer.order(ByteOrder.LITTLE_ENDIAN)

        // Populates header
        outBuffer.putInt(MAGIC_VALUE)
        outBuffer.putInt(blocks.size)
        outBuffer.putInt(HEADER_SIZE + blocks.size * BLOCK_ENTRY_SIZE)

        // Populates block information
        blocks.entries.fold(0) { blockSeq, block ->
            outBuffer.putShort(block.key.toShort())
            outBuffer.putShort(block.value.toShort())
            outBuffer.putShort(blockSeq.toShort())

            blockSeq + block.value
        }

        // Populate string pointers
        sortedMap.entries.fold(0) { acc, entry ->
            outBuffer.putInt(HEADER_SIZE + blocks.size * BLOCK_ENTRY_SIZE + sortedMap.size * 4 + acc)
            acc + entry.value.size + 2
        }
        // Populate strings
        sortedMap.forEach { entry ->
            outBuffer.putShort(entry.value.size.toShort())
            outBuffer.put(entry.value)
        }

        return outBuffer.array()
    }

    private fun getBlocks(map: Iterable<Int>): Map<Int, Int> {
        val blocks = mutableMapOf<Int, Int>()

        var prev: Int? = null
        var blockStart: Int = -1
        for (elem in map) {
            if (prev == null || elem != prev + 1) {
                blockStart = elem
                blocks[blockStart] = 1
            } else {
                blocks[blockStart] = blocks[blockStart]!! + 1
            }
            prev = elem
        }

        return blocks
    }

    companion object {
        private const val MAGIC_VALUE = 0x43535246
        private const val BLOCK_ENTRY_SIZE = 6
        private const val HEADER_SIZE = 12
    }
}

