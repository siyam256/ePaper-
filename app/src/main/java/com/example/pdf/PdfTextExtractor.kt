package com.example.pdf

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.InflaterInputStream
import android.util.Log

object PdfTextExtractor {

    fun init(context: Context) {
        // No external libraries needed!
    }

    suspend fun extractTextFromUri(context: Context, uri: Uri): List<String> = withContext(Dispatchers.IO) {
        val pagesText = mutableListOf<String>()
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext emptyList()
                
            val textBuilder = StringBuilder()
            var index = 0
            
            // Find all "stream" blocks in the PDF
            while (index < bytes.size - 6) {
                // Look for "stream"
                if (bytes[index] == 's'.toByte() &&
                    bytes[index + 1] == 't'.toByte() &&
                    bytes[index + 2] == 'r'.toByte() &&
                    bytes[index + 3] == 'e'.toByte() &&
                    bytes[index + 4] == 'a'.toByte() &&
                    bytes[index + 5] == 'm'.toByte()
                ) {
                    // Find start of stream content (skip line break after "stream")
                    var streamStart = index + 6
                    while (streamStart < bytes.size && (bytes[streamStart] == '\r'.toByte() || bytes[streamStart] == '\n'.toByte())) {
                        streamStart++
                    }
                    
                    // Find "endstream"
                    var streamEnd = streamStart
                    var foundEnd = false
                    while (streamEnd < bytes.size - 9) {
                        if (bytes[streamEnd] == 'e'.toByte() &&
                            bytes[streamEnd + 1] == 'n'.toByte() &&
                            bytes[streamEnd + 2] == 'd'.toByte() &&
                            bytes[streamEnd + 3] == 's'.toByte() &&
                            bytes[streamEnd + 4] == 't'.toByte() &&
                            bytes[streamEnd + 5] == 'r'.toByte() &&
                            bytes[streamEnd + 6] == 'e'.toByte() &&
                            bytes[streamEnd + 7] == 'a'.toByte() &&
                            bytes[streamEnd + 8] == 'm'.toByte()
                        ) {
                            foundEnd = true
                            break
                        }
                        streamEnd++
                    }
                    
                    if (foundEnd && streamEnd > streamStart) {
                        val streamBytes = bytes.copyOfRange(streamStart, streamEnd)
                        
                        // Check if this stream is FlateDecoded by checking previous 1000 bytes for "/FlateDecode"
                        val lookbackStart = (streamStart - 1000).coerceAtLeast(0)
                        val lookbackText = String(bytes.copyOfRange(lookbackStart, streamStart))
                        val isCompressed = lookbackText.contains("/FlateDecode") || lookbackText.contains("/Flate")
                        
                        try {
                            val decompressed = if (isCompressed) {
                                decompressFlate(streamBytes)
                            } else {
                                String(streamBytes, Charsets.ISO_8859_1)
                            }
                            
                            val extracted = extractTextFromStream(decompressed)
                            if (extracted.trim().isNotEmpty()) {
                                textBuilder.append(extracted).append("\n")
                            }
                        } catch (e: Exception) {
                            // Some streams are images or fonts, ignore compression errors
                        }
                    }
                    index = streamEnd + 9
                } else {
                    index++
                }
            }
            
            // Split extracted text into logical "pages" or sections
            val fullText = textBuilder.toString().trim()
            if (fullText.isNotEmpty()) {
                // Split paragraphs
                val paragraphs = fullText.split("\n").filter { it.trim().isNotEmpty() }
                val pageSize = 6 // paragraphs per page
                var pageContent = StringBuilder()
                var count = 0
                
                paragraphs.forEach { para ->
                    pageContent.append(para).append("\n\n")
                    count++
                    if (count >= pageSize) {
                        pagesText.add(pageContent.toString().trim())
                        pageContent = StringBuilder()
                        count = 0
                    }
                }
                if (pageContent.isNotEmpty()) {
                    pagesText.add(pageContent.toString().trim())
                }
            }
            
        } catch (e: Exception) {
            Log.e("PdfTextExtractor", "Error parsing PDF", e)
        }
        
        // Fallback if no text extracted
        if (pagesText.isEmpty()) {
            pagesText.add("[PDF থেকে টেক্সট বের করা যায়নি। এটি সম্ভবত একটি স্ক্যান করা ছবি-ভিত্তিক পিডিএফ। অনুগ্রহ করে একটি টেক্সট-ভিত্তিক ইংরেজি পিডিএফ আপলোড করুন বা ডেমো আর্টিকেলগুলো পড়ুন।]")
        }
        return@withContext pagesText
    }

    private fun decompressFlate(bytes: ByteArray): String {
        return try {
            val inflaterStream = InflaterInputStream(ByteArrayInputStream(bytes))
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var length: Int
            while (inflaterStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
            }
            outputStream.toString("UTF-8")
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractTextFromStream(streamContent: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = streamContent.length
        
        // We look for text inside BT (Begin Text) and ET (End Text)
        while (i < len - 2) {
            val btIndex = streamContent.indexOf("BT", i)
            if (btIndex == -1) break
            
            val etIndex = streamContent.indexOf("ET", btIndex)
            if (etIndex == -1) break
            
            val textBlock = streamContent.substring(btIndex + 2, etIndex)
            
            // Extract text inside parentheses (...) in this text block
            var j = 0
            while (j < textBlock.length) {
                val startParen = textBlock.indexOf('(', j)
                if (startParen == -1) break
                
                // Find matching end paren, ignoring escaped parens e.g. \( and \)
                var endParen = startParen + 1
                var escape = false
                while (endParen < textBlock.length) {
                    val char = textBlock[endParen]
                    if (escape) {
                        escape = false
                    } else if (char == '\\') {
                        escape = true
                    } else if (char == ')') {
                        break
                    }
                    endParen++
                }
                
                if (endParen < textBlock.length) {
                    val textSegment = textBlock.substring(startParen + 1, endParen)
                    // Unescape characters
                    val unescaped = textSegment
                        .replace("\\(", "(")
                        .replace("\\)", ")")
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\t", "\t")
                        
                    // Filter out non-ascii control chars, keep standard punctuation & spaces
                    val filtered = unescaped.filter { 
                        it.code in 32..126 || it == '\n' || it == '\r' || it == '\t'
                    }
                    sb.append(filtered)
                }
                j = endParen + 1
            }
            sb.append("\n")
            i = etIndex + 2
        }
        
        // Clean up formatting
        return sb.toString()
            .replace(Regex("\\s+"), " ") // normalize spacing
            .replace(Regex("(?<=\\w)-\\s+(?=\\w)"), "") // join hyphenated words at line end
            .trim()
    }
}
