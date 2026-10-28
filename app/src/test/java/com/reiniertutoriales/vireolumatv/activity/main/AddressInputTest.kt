package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class AddressInputTest {
    private val search = "https://search.test/?q=[query]"

    @Test fun addressesOpenAndGetHttpsWhenTheyHaveNoScheme() {
        assertEquals("https://example.com", AddressInput.resolve("example.com", search))
        assertEquals("http://plain.test/a", AddressInput.resolve("http://plain.test/a", search))
        assertEquals("https://example.com/path?x=1", AddressInput.resolve("example.com/path?x=1", search))
    }

    @Test fun wordsAreSearchedWithTheTextEncoded() {
        assertEquals("https://search.test/?q=android+tv+navegador", AddressInput.resolve("android tv navegador", search))
        assertEquals("https://search.test/?q=a%26b%3Dc", AddressInput.resolve("a&b=c", search))
    }
}
