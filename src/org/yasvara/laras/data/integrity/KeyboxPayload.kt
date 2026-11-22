/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

/** Strict envelope checks before the shared provider/service validate key and chain. */
internal object KeyboxPayload {
    const val MAX_BYTES = 200_000

    fun utf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()

    fun xml(raw: String): String? = try {
        require(raw.length <= MAX_BYTES && raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val text = raw.trim().removePrefix("\uFEFF").trim()
        val xml = if (text.startsWith("<")) text else {
            // Only ASCII Base64 and its permitted whitespace, not a lenient MIME decoder
            // that silently discards arbitrary bytes in an error page.
            val compact = text.filterNot { it == ' ' || it == '\t' || it == '\r' || it == '\n' }
            utf8(Base64.getDecoder().decode(compact)).trim().removePrefix("\uFEFF").trim()
        }
        require(xml.startsWith("<") && xml.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        // Reject DTDs before invoking the Android/JVM XML implementation. No entity
        // declarations or external resource resolution is needed for a keybox.
        require(!xml.contains("<!DOCTYPE", ignoreCase = true) &&
            !xml.contains("<!ENTITY", ignoreCase = true))
        val builder = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            isExpandEntityReferences = false
        }.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw IllegalArgumentException("external entity") }
        builder.setErrorHandler(object : DefaultHandler() {
            override fun warning(e: SAXParseException) { throw e }
            override fun error(e: SAXParseException) { throw e }
            override fun fatalError(e: SAXParseException) { throw e }
        })
        builder.parse(InputSource(StringReader(xml)))
        xml
    } catch (_: Exception) { null }
}
