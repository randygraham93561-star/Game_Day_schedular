package com.sports.gamedayschedular.util

import android.content.Context
import android.util.Log
import com.dantsu.escposprinter.EscPosCharsetEncoding
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.tcp.TcpConnection
import com.sports.gamedayschedular.data.model.PrintJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class PrinterManager(private val context: Context) {

    /**
     * Prints pre-formatted content (ESC/POS) received from the cloud.
     * This acts as the final step in the chain: RA -> Cloud -> GDS -> Printer.
     */
    suspend fun print(job: PrintJob, printerIp: String, settings: Map<String, String>?): Boolean = withContext(Dispatchers.IO) {
        var connection: TcpConnection? = null
        try {
            val safeSettings = settings ?: emptyMap()
            val widthMm = safeSettings["paper_width"]?.replace("mm", "")?.toIntOrNull() ?: 80
            val charsPerLine = if (widthMm < 70) 32 else 42
            
            Log.d("GDS_PRINTER", "LOCAL PRINT: jobId=${job.id}, target=$printerIp")
            
            connection = TcpConnection(printerIp, 9100, 15000)
            
            val charset = try {
                EscPosCharsetEncoding(safeSettings["character_set"] ?: "UTF-8", 16)
            } catch (e: Exception) {
                null
            }

            val printer = if (charset != null) {
                EscPosPrinter(connection, 203, widthMm.toFloat(), charsPerLine, charset)
            } else {
                EscPosPrinter(connection, 203, widthMm.toFloat(), charsPerLine)
            }

            // Use the pre-rendered content from the Referee App
            val content = if (job.renderedContent.isNotEmpty()) {
                job.renderedContent
            } else {
                // Fallback for older data format
                job.data["content"] ?: "[C]Error: No printable content found."
            }

            val autoCut = safeSettings["auto_cut"]?.toBoolean() ?: true
            if (autoCut) {
                printer.printFormattedTextAndCut(content)
            } else {
                printer.printFormattedText(content)
            }
            
            delay(1000) // Physical cut/feed buffer
            true
        } catch (e: Exception) {
            Log.e("GDS_PRINTER", "Physical print failed for job ${job.id}: ${e.message}")
            e.printStackTrace()
            false
        } finally {
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
