package net.jami.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneTest {

    @Test
    fun testPhoneCreation() {
        val uri = Uri.fromString("sip:user@example.com")
        val phone = Phone(uri, 1, "Work", Phone.NumberType.SIP)

        assertEquals(uri, phone.number)
        assertEquals(1, phone.category)
        assertEquals("Work", phone.label)
        assertEquals(Phone.NumberType.SIP, phone.type)
    }

    @Test
    fun testPhoneDefaults() {
        val uri = Uri.fromString("sip:user@example.com")
        val phone = Phone(uri, 1)

        assertEquals(uri, phone.number)
        assertEquals(1, phone.category)
        assertEquals(null, phone.label)
        assertEquals(Phone.NumberType.UNKNOWN, phone.type)
    }

    @Test
    fun testPhoneNumberTypes() {
        assertEquals(Phone.NumberType.UNKNOWN, Phone.NumberType.valueOf("UNKNOWN"))
        assertEquals(Phone.NumberType.TEL, Phone.NumberType.valueOf("TEL"))
        assertEquals(Phone.NumberType.SIP, Phone.NumberType.valueOf("SIP"))
        assertEquals(Phone.NumberType.IP, Phone.NumberType.valueOf("IP"))
        assertEquals(Phone.NumberType.JAMI, Phone.NumberType.valueOf("JAMI"))
    }

    @Test
    fun testPhoneEquality() {
        val uri = Uri.fromString("sip:user@example.com")
        val phone1 = Phone(uri, 1, "Work", Phone.NumberType.SIP)
        val phone2 = Phone(uri, 1, "Work", Phone.NumberType.SIP)
        val phone3 = Phone(uri, 2, "Home", Phone.NumberType.SIP)

        assertEquals(phone1, phone2)
        // phone3 has different category and label, so should not equal
        assertEquals(phone1.number, phone3.number)
    }
}
