package app.chargeshare.shared.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileLimitsTest {

    @Test
    fun acceptsCommonPhoneFormats() {
        listOf("+49 421 1234567", "0421 1234567", "0421/1234567", "(0421) 123-4567", "123456").forEach {
            assertTrue(ProfileLimits.isValidPhone(it), it)
        }
    }

    @Test
    fun rejectsLettersTooFewDigitsAndTooLongNumbers() {
        listOf("abc", "12345", "+", "0421 abc 123", "<b>123456</b>", "+49 421 12345678901234567890", "49+421123456").forEach {
            assertFalse(ProfileLimits.isValidPhone(it), it)
        }
    }
}
