/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * One ONNX model of the depth build, run on the CPU with ONNX Runtime (docs/specs/0013 §10.4):
 * one float input [1, 3, side, side], one float output with a value per input pixel.
 *
 * [lean] trades speed for memory: without ORT's arena and memory pattern ToonOut peaks at about
 * 3.2 GB instead of 6.4 GB, and a pass takes about 30 s instead of 21 s on onyx.
 */
internal class OnnxModel(file: File, lean: Boolean) : Closeable {
    private val env = OrtEnvironment.getEnvironment().also { it.setTelemetry(false) }
    private val session: OrtSession
    private val input: String
    private val buffer: FloatBuffer
    val side: Int

    init {
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(THREADS)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            if (lean) {
                setCPUArenaAllocator(false)
                setMemoryPatternOptimization(false)
            }
        }
        session = env.createSession(file.path, options)
        options.close()
        val info = session.inputInfo.values.first()
        input = info.name
        side = ((info.info as ai.onnxruntime.TensorInfo).shape.last()).toInt()
        buffer = ByteBuffer.allocateDirect(3 * side * side * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    }

    /** Runs the model on [planes] (3 x side² floats, already normalized); returns side² values. */
    fun run(planes: Array<FloatArray>): FloatArray {
        buffer.clear()
        for (p in planes) buffer.put(p)
        buffer.rewind()
        OnnxTensor.createTensor(env, buffer, longArrayOf(1, 3, side.toLong(), side.toLong())).use { tensor ->
            session.run(mapOf(input to tensor)).use { result ->
                val out = (result.get(0) as OnnxTensor).floatBuffer
                return FloatArray(side * side).also { out.get(it) }
            }
        }
    }

    override fun close() = session.close()

    companion object {
        /** Six of onyx's eight cores: the best time per pass in M0, and the phone stays usable. */
        private const val THREADS = 6
    }
}
