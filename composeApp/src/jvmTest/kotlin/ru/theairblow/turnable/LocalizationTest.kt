package ru.theairblow.turnable

import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import turnable.composeapp.generated.resources.*

class LocalizationTest {
    @Test
    fun resolvesEnglishAndRussianStrings() = runBlocking {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("Clients", getString(Res.string.tab_clients))
            assertEquals("Added 1 client", getPluralString(Res.plurals.clients_added, 1, 1))
            assertEquals("Added 3 clients", getPluralString(Res.plurals.clients_added, 3, 3))

            Locale.setDefault(Locale.forLanguageTag("ru"))
            assertEquals("Клиенты", getString(Res.string.tab_clients))
            assertEquals("Добавлен 1 клиент", getPluralString(Res.plurals.clients_added, 1, 1))
            assertEquals("Добавлено 3 клиента", getPluralString(Res.plurals.clients_added, 3, 3))
            assertEquals("Добавлено 5 клиентов", getPluralString(Res.plurals.clients_added, 5, 5))
            assertEquals("Добавлен 21 клиент", getPluralString(Res.plurals.clients_added, 21, 21))
            assertEquals("Добавлено 11 клиентов", getPluralString(Res.plurals.clients_added, 11, 11))
        } finally {
            Locale.setDefault(original)
        }
    }
}
