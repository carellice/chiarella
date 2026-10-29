package it.chiarella.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageTest {

    private fun assistant(content: String) = ChatMessage("1", ChatMessage.Role.ASSISTANT, content)

    @Test
    fun azioniTolteDalTestoEConvertiteInPulsanti() {
        val m = assistant("Proviamo a rallentare un attimo.\n[[suoni: mare, vento]]\n[[respiro]]")
        assertEquals("Proviamo a rallentare un attimo.", m.text)
        assertEquals(listOf(ChatAction.PlaySounds(listOf("mare", "vento")), ChatAction.Breathe), m.actions)
    }

    @Test
    fun idSconosciutiIgnorati() {
        val m = assistant("Ecco. [[suoni: pianoforte, Pioggia_Dolce]] [[ballo]] [[momento]]")
        assertEquals(listOf(ChatAction.PlaySounds(listOf("pioggia_dolce")), ChatAction.Moment), m.actions)
        assertEquals("Ecco.", m.text)
    }

    @Test
    fun azioneAMetaDuranteLoStreamingNascosta() {
        assertEquals("Che ne dici di un respiro?", assistant("Che ne dici di un respiro?\n[[resp").text)
        assertEquals("Che ne dici?", assistant("Che ne dici?\n[").text)
    }
}
