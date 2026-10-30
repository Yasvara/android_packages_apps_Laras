/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor

/**
 * Promptable segmentation with EdgeTAM (Apache-2.0): the image encoder runs once per picture,
 * then every tap runs only the mask decoder with a prompt from [PromptEncoder], so a tap takes
 * a fraction of a second even on a mid-range phone.
 *
 * Tensors may be float or 8-bit quantized; values are converted at the boundary either way.
 */
class SegmentationEngine(context: Context) : Closeable {
    private val options = Interpreter.Options().apply {
        setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
        setUseXNNPACK(true)
    }
    private val encoder = Interpreter(asset(context, "depth/encoder.tflite"), options)
    private val decoder = Interpreter(asset(context, "depth/decoder.tflite"), options)
    private val prompts = PromptEncoder(context.assets.open("depth/prompt_encoder.bin"))

    /** The encoder's outputs for the picture last passed to [encode], by tensor name. */
    private var features: Map<String, ByteBuffer>? = null

    /** Encodes [picture], stretched to the square model input as SAM 2 does. */
    fun encode(picture: Bitmap) {
        val input = encoder.getInputTensor(encoder.getInputIndex("image"))
        val side = input.shape()[1]
        val scaled = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(scaled).drawBitmap(picture, null, Rect(0, 0, side, side), Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(side * side)
        scaled.getPixels(pixels, 0, side, 0, 0, side, side)
        scaled.recycle()
        val image = ByteBuffer.allocateDirect(input.numBytes()).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            for (shift in intArrayOf(16, 8, 0)) put(image, input, ((p shr shift) and 0xFF) / 255f)
        }
        image.rewind()

        // The encoder also embeds a prompt; give it two known points so its answer can check
        // the Kotlin prompt encoder that every tap relies on.
        val inputs = arrayOfNulls<Any>(encoder.inputTensorCount)
        for (i in 0 until encoder.inputTensorCount) {
            val tensor = encoder.getInputTensor(i)
            val buffer = ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder())
            when (tensor.name()) {
                "image" -> {
                    inputs[i] = image
                    continue
                }
                "unnorm_coords" -> CHECK_POINTS.forEach { put(buffer, tensor, it) }
                "labels" -> repeat(2) { put(buffer, tensor, 1f) }
            }
            buffer.rewind()
            inputs[i] = buffer
        }
        val outputs = HashMap<Int, Any>()
        val named = HashMap<String, ByteBuffer>()
        for (i in 0 until encoder.outputTensorCount) {
            val tensor = encoder.getOutputTensor(i)
            val buffer = ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder())
            outputs[i] = buffer
            named[tensor.name()] = buffer
        }
        encoder.runForMultipleInputsOutputs(inputs, outputs)
        named.values.forEach { it.rewind() }
        features = named
        checkPrompts(named["sparse_embedding"])
    }

    /** Logs how far the Kotlin prompt encoder is from the model's own, for the check points. */
    private fun checkPrompts(model: ByteBuffer?) {
        model ?: return
        val tensor = encoderOutput("sparse_embedding")
        if (tensor.dataType() != DataType.FLOAT32) return
        val ours = prompts.encode(
            listOf(
                PromptEncoder.Point(CHECK_POINTS[0] * PromptEncoder.INPUT, CHECK_POINTS[1] * PromptEncoder.INPUT),
                PromptEncoder.Point(CHECK_POINTS[2] * PromptEncoder.INPUT, CHECK_POINTS[3] * PromptEncoder.INPUT),
            )
        )
        var worst = 0f
        for (v in ours) worst = maxOf(worst, kotlin.math.abs(v - model.float))
        model.rewind()
        Log.i(TAG, "Prompt encoder check: max difference $worst")
    }

    /**
     * The mask of the object the prompt picks, as coverage 0..1 over [MASK] x [MASK] cells of
     * the square model input. Points are in model input pixels.
     */
    fun decode(points: List<PromptEncoder.Point>): Decoded {
        val features = checkNotNull(features) { "encode() first" }
        val sparse = prompts.encode(points)
        val inputs = arrayOfNulls<Any>(decoder.inputTensorCount)
        for (i in 0 until decoder.inputTensorCount) {
            val tensor = decoder.getInputTensor(i)
            val buffer = ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder())
            if (tensor.name() == "sparse_embedding") {
                for (v in sparse) put(buffer, tensor, v)
            } else {
                // Carry the encoder's output over, converting between quantizations if needed.
                val source = features[tensor.name()] ?: error("No encoder output ${tensor.name()}")
                val from = encoderOutput(tensor.name())
                convert(source, from, buffer, tensor)
            }
            buffer.rewind()
            inputs[i] = buffer
        }
        val masksIndex = decoder.getOutputIndex("masks")
        val maskTensor = decoder.getOutputTensor(masksIndex)
        val outputs = HashMap<Int, Any>()
        for (i in 0 until decoder.outputTensorCount) {
            outputs[i] = ByteBuffer.allocateDirect(decoder.getOutputTensor(i).numBytes())
                .order(ByteOrder.nativeOrder())
        }
        decoder.runForMultipleInputsOutputs(inputs, outputs)
        val masks = (outputs[masksIndex] as ByteBuffer).apply { rewind() }
        val side = maskTensor.shape()[1]
        val logits = FloatArray(side * maskTensor.shape()[2]) { get(masks, maskTensor) }
        // The decoder's upsampling leaves a two-cell grid in its logits that shows as specks
        // wherever the model is less than certain; smoothing before the sigmoid removes it.
        smooth(logits, side)
        smooth(logits, side)
        val coverage = FloatArray(logits.size) { 1f / (1f + kotlin.math.exp(-logits[it])) }
        val scoreIndex = decoder.getOutputIndex("scores")
        val score = (outputs[scoreIndex] as ByteBuffer).let {
            it.rewind()
            get(it, decoder.getOutputTensor(scoreIndex))
        }
        return Decoded(coverage, score)
    }

    /** A decoded mask: coverage over [MASK] x [MASK] cells, and the model's confidence. */
    class Decoded(val coverage: FloatArray, val score: Float)

    private fun encoderOutput(name: String): Tensor {
        for (i in 0 until encoder.outputTensorCount) {
            val tensor = encoder.getOutputTensor(i)
            if (tensor.name() == name) return tensor
        }
        error("No encoder output $name")
    }

    override fun close() {
        encoder.close()
        decoder.close()
    }

    companion object {
        /** The side of the decoder's square mask, which [SelectionOp.Region] keeps. */
        const val MASK = SelectionOp.SIDE
        private const val TAG = "SegmentationEngine"
        /** Two points, as fractions of the picture: x0, y0, x1, y1. */
        private val CHECK_POINTS = floatArrayOf(0.5f, 0.5f, 0.25f, 0.75f)

        /**
         * A [1, 2, 1] / 4 blur across and down, edges clamped: it has no response at all to a
         * pattern that alternates every cell, and does not shift the mask.
         */
        private fun smooth(grid: FloatArray, side: Int) {
            val rows = grid.size / side
            val tmp = FloatArray(grid.size)
            for (y in 0 until rows) for (x in 0 until side) {
                val i = y * side + x
                val l = grid[if (x > 0) i - 1 else i]
                val r = grid[if (x < side - 1) i + 1 else i]
                tmp[i] = (l + 2 * grid[i] + r) / 4
            }
            for (y in 0 until rows) for (x in 0 until side) {
                val i = y * side + x
                val u = tmp[if (y > 0) i - side else i]
                val d = tmp[if (y < rows - 1) i + side else i]
                grid[i] = (u + 2 * tmp[i] + d) / 4
            }
        }

        private fun asset(context: Context, name: String): MappedByteBuffer {
            val fd = context.assets.openFd(name)
            return FileInputStream(fd.fileDescriptor).channel.use {
                it.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }

        private fun put(buffer: ByteBuffer, tensor: Tensor, value: Float) {
            when (tensor.dataType()) {
                DataType.FLOAT32 -> buffer.putFloat(value)
                DataType.UINT8, DataType.INT8 -> {
                    val q = tensor.quantizationParams()
                    val raw = Math.round(value / q.scale + q.zeroPoint)
                    val clamped = if (tensor.dataType() == DataType.UINT8) raw.coerceIn(0, 255)
                    else raw.coerceIn(-128, 127)
                    buffer.put(clamped.toByte())
                }
                else -> error("Unsupported tensor type ${tensor.dataType()}")
            }
        }

        private fun get(buffer: ByteBuffer, tensor: Tensor): Float = when (tensor.dataType()) {
            DataType.FLOAT32 -> buffer.float
            DataType.UINT8 -> {
                val q = tensor.quantizationParams()
                ((buffer.get().toInt() and 0xFF) - q.zeroPoint) * q.scale
            }
            DataType.INT8 -> {
                val q = tensor.quantizationParams()
                (buffer.get().toInt() - q.zeroPoint) * q.scale
            }
            else -> error("Unsupported tensor type ${tensor.dataType()}")
        }

        private fun convert(from: ByteBuffer, fromTensor: Tensor, to: ByteBuffer, toTensor: Tensor) {
            from.rewind()
            if (fromTensor.dataType() == toTensor.dataType() &&
                fromTensor.quantizationParams().scale == toTensor.quantizationParams().scale &&
                fromTensor.quantizationParams().zeroPoint == toTensor.quantizationParams().zeroPoint
            ) {
                to.put(from)
            } else {
                while (from.hasRemaining()) put(to, toTensor, get(from, fromTensor))
            }
            from.rewind()
        }
    }
}
