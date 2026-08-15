package com.iar.tardypass.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterDiffTest {

    private fun diff(file: Set<String>, active: Set<String>) = RosterDiff(
        newIds = file - active,
        updatedIds = file intersect active,
        deactivateIds = active - file
    )

    @Test
    fun `partitions new, updated and departed`() {
        val d = diff(file = setOf("1", "2", "3"), active = setOf("2", "3", "4"))
        assertEquals(setOf("1"), d.newIds)
        assertEquals(setOf("2", "3"), d.updatedIds)
        assertEquals(setOf("4"), d.deactivateIds)
        assertEquals(3, d.activeBefore) // updated + deactivated, not the file size
    }

    @Test
    fun `re-uploading the same roster deactivates nobody`() {
        val ids = setOf("1", "2", "3")
        assertTrue(diff(ids, ids).deactivateIds.isEmpty())
    }

    @Test
    fun `first import has no existing roster and cannot divide by zero`() {
        val d = diff(file = setOf("1", "2"), active = emptySet())
        assertEquals(0.0, d.deactivateShare, 0.0)
        assertFalse(d.looksLikePartialFile())
    }

    @Test
    fun `a partial file trips the guard`() {
        // 20 active, file has 5 → 15 deactivations = 75%
        val active = (1..20).map { it.toString() }.toSet()
        val d = diff(file = (1..5).map { it.toString() }.toSet(), active = active)
        assertEquals(15, d.deactivateIds.size)
        assertTrue(d.looksLikePartialFile())
    }

    @Test
    fun `ordinary end-of-term turnover does not trip the guard`() {
        // 100 active, 12 departed = 12%
        val active = (1..100).map { it.toString() }.toSet()
        val d = diff(file = (13..100).map { it.toString() }.toSet(), active = active)
        assertEquals(12, d.deactivateIds.size)
        assertFalse(d.looksLikePartialFile())
    }

    @Test
    fun `the floor keeps a tiny test roster from tripping the guard`() {
        // 4 active, file has 1 → 75% share, but below the 10-student floor.
        val d = diff(file = setOf("1"), active = setOf("1", "2", "3", "4"))
        assertTrue(d.deactivateShare > 0.40)
        assertFalse(d.looksLikePartialFile())
    }

    @Test
    fun `threshold is exclusive at exactly forty percent`() {
        // 20 active, 8 departed = exactly 40% → allowed through.
        val active = (1..20).map { it.toString() }.toSet()
        val d = diff(file = (9..20).map { it.toString() }.toSet(), active = active)
        assertEquals(0.40, d.deactivateShare, 0.0001)
        assertFalse(d.looksLikePartialFile())
    }
}
