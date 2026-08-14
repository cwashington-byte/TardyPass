package com.iar.tardypass.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsTest {

    @Test
    fun `first and last name`() {
        assertEquals("JD", initialsFrom("Jane Doe"))
    }

    @Test
    fun `middle name is skipped, not used as the second letter`() {
        assertEquals("JD", initialsFrom("Jane Q. Doe"))
        assertEquals("MG", initialsFrom("Maria de la Garcia"))
    }

    @Test
    fun `single name yields one letter`() {
        assertEquals("C", initialsFrom("Cher"))
    }

    @Test
    fun `blank and whitespace are safe`() {
        assertEquals("", initialsFrom(""))
        assertEquals("", initialsFrom("   "))
    }

    @Test
    fun `extra whitespace is collapsed and case normalized`() {
        assertEquals("JD", initialsFrom("  jane   doe "))
    }
}
