package dev.sk2andy.materialbrowser.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSpeechLanguageDetectorInstrumentedTest {
    @Test
    fun packagedProfilesDetectGermanEnglishAndUnsupportedFrench() = runBlocking {
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
        assertEquals("fr", ReaderSpeechLanguageDetector.detect(
            "La ville ouvrira une nouvelle bibliothèque lundi prochain. Les visiteurs pourront " +
                "emprunter des livres et lire dans une salle tranquille. Des activités gratuites " +
                "seront proposées aux enfants et aux familles tout au long de la semaine."
        ))
    }
}
