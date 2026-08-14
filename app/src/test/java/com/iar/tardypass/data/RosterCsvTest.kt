package com.iar.tardypass.data

import com.iar.tardypass.data.csv.LogCsv
import com.iar.tardypass.data.csv.RosterCsv
import com.iar.tardypass.domain.Pass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterCsvTest {

    private val sample = """
        Student_Number,First_Name,Last_Name,Grade,schedule
        10001,Alice,Nguyen,09,"{""P1"":{""course"":""Algebra I"",""room"":""204""},""P2"":{""course"":""Biology"",""room"":""117""}}"
        10002,Bob,Lee,10,"{""P1"":{""course"":""English II"",""room"":""301""}}"
    """.trimIndent()

    @Test
    fun `parses the PWA sample format`() {
        val result = RosterCsv.parse(sample)
        assertEquals(2, result.students.size)
        assertTrue(result.errors.isEmpty())
        val alice = result.students[0]
        assertEquals("10001", alice.id)
        assertEquals("Alice", alice.firstName)
        assertEquals("Nguyen", alice.lastName)
        assertEquals("09", alice.grade)
        assertEquals("Algebra I", alice.schedule["P1"]?.course)
        assertEquals("204", alice.schedule["P1"]?.room)
    }

    @Test
    fun `capital Schedule column also works`() {
        val csv = "Student_Number,First_Name,Last_Name,Grade,Schedule\n" +
            "1,A,B,09,\"{\"\"P1\"\":{\"\"course\"\":\"\"Math\"\",\"\"room\"\":\"\"1\"\"}}\""
        val result = RosterCsv.parse(csv)
        assertEquals("Math", result.students.single().schedule["P1"]?.course)
    }

    @Test
    fun `bad schedule row is reported but import continues`() {
        val csv = "Student_Number,First_Name,Last_Name,Grade,schedule\n" +
            "1,A,B,09,not-json\n" +
            "2,C,D,09,\"{\"\"P1\"\":{\"\"course\"\":\"\"Math\"\",\"\"room\"\":\"\"1\"\"}}\""
        val result = RosterCsv.parse(csv)
        assertEquals(2, result.students.size) // bad-schedule student kept with empty schedule
        assertEquals(1, result.errors.size)
        assertTrue(result.students[0].schedule.isEmpty())
    }

    @Test
    fun `missing name is reported and row skipped`() {
        val csv = "Student_Number,First_Name,Last_Name\n1,,B\n2,C,D"
        val result = RosterCsv.parse(csv)
        assertEquals(1, result.students.size)
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `alternate lowercase headers work`() {
        val csv = "id,first,last,grade\n5,Eve,Stone,11"
        val result = RosterCsv.parse(csv)
        assertEquals("Eve", result.students.single().firstName)
    }

    @Test
    fun `log export matches PWA column contract`() {
        val pass = Pass(
            studentId = "10001", studentFirstName = "Alice", studentLastName = "Nguyen",
            timestampMillis = 1755087660000L, // fixed instant
            period = "P2", course = "Biology", room = "117",
            reason = "Late", staffInitials = "CW", excused = true,
            printDriver = "serial", printConfirmed = true
        )
        val csv = LogCsv.build(listOf(pass))
        val lines = csv.trim().split("\r\n")
        assertEquals(
            "Student_Number,First_Name,Last_Name,Date,Time,Period,Course,Room,Reason,Recorded_By,Excused",
            lines[0]
        )
        assertTrue(lines[1].startsWith("10001,Alice,Nguyen,"))
        assertTrue(lines[1].endsWith(",P2,Biology,117,Late,CW,TRUE"))
    }

    @Test
    fun `csv fields with commas are quoted`() {
        val pass = Pass(
            studentId = "1", studentFirstName = "A", studentLastName = "B",
            timestampMillis = 0L, period = "P1", course = "Foods, Nutrition", room = "1",
            reason = null, staffInitials = null, excused = false,
            printDriver = "rawbt", printConfirmed = false
        )
        val csv = LogCsv.build(listOf(pass))
        assertTrue(csv.contains("\"Foods, Nutrition\""))
    }
}
