package dev.sk2andy.materialbrowser.reader

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSpeechLanguageDetectorTest {
    @Test
    fun `German and English articles select their own language`() = runBlocking {
        assertEquals("de", ReaderSpeechLanguageDetector.detect(
            "Die Stadt eröffnet am Montag eine neue Bibliothek. Besucher können dort Bücher " +
                "ausleihen und in einem ruhigen Raum lesen. Für Kinder gibt es jede Woche eine " +
                "Vorlesestunde. Der Eintritt ist kostenlos und alle Menschen sind willkommen."
        ))
        assertEquals("en", ReaderSpeechLanguageDetector.detect(
            "The city will open a new library on Monday. Visitors can borrow books and read " +
                "in a quiet room. Children can listen to stories every week. Admission is free " +
                "and everyone is welcome to explore the collection and meet the librarians."
        ))
    }

    @Test
    fun `Portuguese and Spanish articles are not conflated`() = runBlocking {
        assertEquals("pt", ReaderSpeechLanguageDetector.detect(
            "A cidade vai abrir uma nova biblioteca na próxima segunda-feira. Os visitantes " +
                "poderão levar livros para casa e encontrar um lugar tranquilo para ler. " +
                "Haverá atividades gratuitas para crianças e famílias durante toda a semana."
        ))
        assertEquals("es", ReaderSpeechLanguageDetector.detect(
            "La ciudad abrirá una nueva biblioteca el próximo lunes. Los visitantes podrán " +
                "llevarse libros a casa y encontrar un lugar tranquilo para leer. Habrá " +
                "actividades gratuitas para niños y familias durante toda la semana."
        ))
    }

    @Test
    fun `unsupported French and Russian stay identifiable instead of choosing an installed voice`() = runBlocking {
        assertEquals("fr", ReaderSpeechLanguageDetector.detect(
            "La ville ouvrira une nouvelle bibliothèque lundi prochain. Les visiteurs pourront " +
                "emprunter des livres et lire dans une salle tranquille. Des activités gratuites " +
                "seront proposées aux enfants et aux familles tout au long de la semaine."
        ))
        assertEquals("ru", ReaderSpeechLanguageDetector.detect(
            "В понедельник в городе откроется новая библиотека. Посетители смогут брать книги " +
                "домой и читать в тихом зале. Для детей каждую неделю будут проводить бесплатные " +
                "занятия. Все жители города приглашаются на открытие библиотеки."
        ))
    }

    @Test
    fun `short or nonlinguistic text does not invent a language`() = runBlocking {
        assertNull(ReaderSpeechLanguageDetector.detect(""))
        assertNull(ReaderSpeechLanguageDetector.detect("Hallo"))
        assertNull(ReaderSpeechLanguageDetector.detect("12345 😀".repeat(100)))
    }

    @Test
    fun `cancelled request cannot deliver language result`() = runBlocking {
        var delivered = false
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            ReaderSpeechLanguageDetector.detect("This article describes the history of the city.".repeat(100))
            delivered = true
        }
        request.cancelAndJoin()
        assertFalse(delivered)
    }
}
