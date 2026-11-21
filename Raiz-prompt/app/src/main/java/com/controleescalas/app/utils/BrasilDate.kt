package com.controleescalas.app.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Datas operacionais do app (hoje, amanhã, documentos yyyy-MM-dd)
 * sempre no fuso de Brasília, independente do relógio do emulador.
 */
object BrasilDate {
    val fuso: TimeZone = TimeZone.getTimeZone("America/Sao_Paulo")
    private val localePtBr = Locale("pt", "BR")

    fun calendar(): Calendar = Calendar.getInstance(fuso)

    fun hoje(): String = formatYyyyMmDd(Date())

    fun amanha(): String {
        val cal = calendar()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return formatYyyyMmDd(cal.time)
    }

    fun millisAteProximaMeiaNoite(): Long {
        val agora = calendar()
        val proxima = calendar()
        proxima.set(Calendar.HOUR_OF_DAY, 0)
        proxima.set(Calendar.MINUTE, 0)
        proxima.set(Calendar.SECOND, 0)
        proxima.set(Calendar.MILLISECOND, 0)
        proxima.add(Calendar.DAY_OF_MONTH, 1)
        return (proxima.timeInMillis - agora.timeInMillis).coerceAtLeast(0L)
    }

    fun formatYyyyMmDd(date: Date = Date()): String = format("yyyy-MM-dd", date)

    fun formatDdMmYyyy(date: Date = Date()): String = format("dd/MM/yyyy", date)

    fun format(pattern: String, date: Date = Date()): String =
        formatter(pattern).format(date)

    fun parseYyyyMmDd(data: String): Date? = try {
        formatter("yyyy-MM-dd").apply { isLenient = false }.parse(data)
    } catch (_: Exception) {
        null
    }

    fun yyyyMmDdParaDdMmYyyy(data: String): String {
        val date = parseYyyyMmDd(data) ?: return data
        return formatDdMmYyyy(date)
    }

    private fun formatter(pattern: String): SimpleDateFormat =
        SimpleDateFormat(pattern, localePtBr).apply { timeZone = fuso }
}
