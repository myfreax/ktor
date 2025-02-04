/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket

import java.nio.*
import java.util.concurrent.atomic.*

@Suppress("KDocMissingDocumentation", "UsePropertyAccessSyntax")
public class FrameParser {
    private val state = AtomicReference(State.HEADER0)

    public var fin: Boolean = false
        private set

    public var rsv1: Boolean = false
        private set

    public var rsv2: Boolean = false
        private set

    public var rsv3: Boolean = false
        private set

    public var mask: Boolean = false
        private set

    private var opcode = 0
    private var lastOpcode = 0

    private var lengthLength = 0

    public var length: Long = 0L
        private set

    public var maskKey: Int? = null
        private set

    public val frameType: FrameType
        get() = FrameType[opcode] ?: throw IllegalStateException("Unsupported opcode ${Integer.toHexString(opcode)}")

    public enum class State {
        HEADER0,
        LENGTH,
        MASK_KEY,
        BODY
    }

    public val bodyReady: Boolean
        get() = state.get() == State.BODY

    public fun bodyComplete() {
        if (!state.compareAndSet(State.BODY, State.HEADER0)) {
            throw IllegalStateException("It should be state BODY but it is ${state.get()}")
        }

        // lastOpcode should be never reset!
        opcode = 0
        length = 0L
        lengthLength = 0
        maskKey = null
    }

    public fun frame(bb: ByteBuffer) {
        require(bb.order() == ByteOrder.BIG_ENDIAN) { "Buffer order should be BIG_ENDIAN but it is ${bb.order()}" }

        while (handleStep(bb)) {
        }
    }

    private fun handleStep(bb: ByteBuffer) = when (state.get()!!) {
        State.HEADER0 -> parseHeader1(bb)
        State.LENGTH -> parseLength(bb)
        State.MASK_KEY -> parseMaskKey(bb)
        State.BODY -> false
    }

    private fun parseHeader1(bb: ByteBuffer): Boolean {
        if (bb.remaining() < 2) {
            return false
        }

        val flagsAndOpcode = bb.get().toInt()
        val maskAndLength1 = bb.get().toInt()

        fin = flagsAndOpcode and 0x80 != 0
        rsv1 = flagsAndOpcode and 0x40 != 0
        rsv2 = flagsAndOpcode and 0x20 != 0
        rsv3 = flagsAndOpcode and 0x10 != 0

        opcode = (flagsAndOpcode and 0x0f).let { new -> if (new == 0) lastOpcode else new }
        if (!frameType.controlFrame) {
            lastOpcode = opcode
        }
        mask = maskAndLength1 and 0x80 != 0
        val length1 = maskAndLength1 and 0x7f

        lengthLength = when (length1) {
            126 -> 2
            127 -> 8
            else -> 0
        }

        length = if (lengthLength == 0) length1.toLong() else 0
        when {
            lengthLength > 0 -> state.set(State.LENGTH)
            mask -> state.set(State.MASK_KEY)
            else -> state.set(State.BODY)
        }

        return true
    }

    private fun parseLength(bb: ByteBuffer): Boolean {
        if (bb.remaining() < lengthLength) {
            return false
        }

        length = when (lengthLength) {
            2 -> bb.getShort().toLong() and 0xffff
            8 -> bb.getLong()
            else -> throw IllegalStateException()
        }

        val mask = if (mask) State.MASK_KEY else State.BODY
        state.set(mask)
        return true
    }

    private fun parseMaskKey(bb: ByteBuffer): Boolean {
        if (bb.remaining() < 4) {
            return false
        }

        maskKey = bb.getInt()
        state.set(State.BODY)
        return true
    }
}
