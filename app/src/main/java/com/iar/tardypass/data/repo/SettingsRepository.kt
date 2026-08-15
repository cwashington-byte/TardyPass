package com.iar.tardypass.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.iar.tardypass.printing.PrinterEncoding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Firestore path segment for this site. A real name (not "default") so adding a
 * second school later is a config change rather than a data migration.
 */
const val DEFAULT_SCHOOL_ID = "invictus"

data class AppSettings(
    val printerDriverId: String = "auto",       // auto | vendor | serial | bluetooth | rawbt
    val encoding: PrinterEncoding = PrinterEncoding.CP437,
    val selectCodePage: Boolean = false,
    val hidePeriod: Boolean = false,
    val hideCourse: Boolean = false,
    val hideRoom: Boolean = false,
    val hideReason: Boolean = false,
    val enableLogging: Boolean = true,
    val passTitle: String = "IAR Tardy Pass",
    val passFooter: String = "Proceed directly to class.",
    val staffInitials: String = "",             // remembered across passes (PWA re-typed every time)
    // Shared-device mode: who is currently claiming passes on this printer.
    val selectedStaffUid: String = "",
    val selectedStaffName: String = "",
    val selectedStaffAtMillis: Long = 0L,
    val staffTimeoutMinutes: Int = 5,           // 0 = never clear
    /** Last role Firestore reported, so a cold start knows the mode immediately. */
    val lastKnownRole: String = "",
    /** Cached copy of the school's bell schedule; printing must not need network. */
    val bellScheduleJson: String = "",
    val serialDevicePath: String = "",          // filled by Phase 0 discovery / probing
    val bluetoothAddress: String = "",
    val schoolId: String = DEFAULT_SCHOOL_ID    // multi-school later without migration
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DRIVER = stringPreferencesKey("printer_driver")
        val ENCODING = stringPreferencesKey("printer_encoding")
        val SELECT_CODE_PAGE = booleanPreferencesKey("select_code_page")
        val HIDE_PERIOD = booleanPreferencesKey("hide_period")
        val HIDE_COURSE = booleanPreferencesKey("hide_course")
        val HIDE_ROOM = booleanPreferencesKey("hide_room")
        val HIDE_REASON = booleanPreferencesKey("hide_reason")
        val ENABLE_LOGGING = booleanPreferencesKey("enable_logging")
        val PASS_TITLE = stringPreferencesKey("pass_title")
        val PASS_FOOTER = stringPreferencesKey("pass_footer")
        val STAFF_INITIALS = stringPreferencesKey("staff_initials")
        val SELECTED_STAFF_UID = stringPreferencesKey("selected_staff_uid")
        val SELECTED_STAFF_NAME = stringPreferencesKey("selected_staff_name")
        val SELECTED_STAFF_AT = longPreferencesKey("selected_staff_at")
        val STAFF_TIMEOUT_MIN = intPreferencesKey("staff_timeout_min")
        val LAST_KNOWN_ROLE = stringPreferencesKey("last_known_role")
        val BELL_SCHEDULE_JSON = stringPreferencesKey("bell_schedule_json")
        val SERIAL_PATH = stringPreferencesKey("serial_device_path")
        val BT_ADDRESS = stringPreferencesKey("bt_address")
        val SCHOOL_ID = stringPreferencesKey("school_id")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            printerDriverId = p[Keys.DRIVER] ?: "auto",
            encoding = runCatching { PrinterEncoding.valueOf(p[Keys.ENCODING] ?: "CP437") }
                .getOrDefault(PrinterEncoding.CP437),
            selectCodePage = p[Keys.SELECT_CODE_PAGE] ?: false,
            hidePeriod = p[Keys.HIDE_PERIOD] ?: false,
            hideCourse = p[Keys.HIDE_COURSE] ?: false,
            hideRoom = p[Keys.HIDE_ROOM] ?: false,
            hideReason = p[Keys.HIDE_REASON] ?: false,
            enableLogging = p[Keys.ENABLE_LOGGING] ?: true,
            passTitle = p[Keys.PASS_TITLE] ?: "IAR Tardy Pass",
            passFooter = p[Keys.PASS_FOOTER] ?: "Proceed directly to class.",
            staffInitials = p[Keys.STAFF_INITIALS] ?: "",
            selectedStaffUid = p[Keys.SELECTED_STAFF_UID] ?: "",
            selectedStaffName = p[Keys.SELECTED_STAFF_NAME] ?: "",
            selectedStaffAtMillis = p[Keys.SELECTED_STAFF_AT] ?: 0L,
            staffTimeoutMinutes = p[Keys.STAFF_TIMEOUT_MIN] ?: 5,
            lastKnownRole = p[Keys.LAST_KNOWN_ROLE] ?: "",
            bellScheduleJson = p[Keys.BELL_SCHEDULE_JSON] ?: "",
            serialDevicePath = p[Keys.SERIAL_PATH] ?: "",
            bluetoothAddress = p[Keys.BT_ADDRESS] ?: "",
            schoolId = p[Keys.SCHOOL_ID] ?: DEFAULT_SCHOOL_ID
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setDriver(id: String) = context.dataStore.edit { it[Keys.DRIVER] = id }
    suspend fun setEncoding(e: PrinterEncoding) = context.dataStore.edit { it[Keys.ENCODING] = e.name }
    suspend fun setSelectCodePage(v: Boolean) = context.dataStore.edit { it[Keys.SELECT_CODE_PAGE] = v }
    suspend fun setHidePeriod(v: Boolean) = context.dataStore.edit { it[Keys.HIDE_PERIOD] = v }
    suspend fun setHideCourse(v: Boolean) = context.dataStore.edit { it[Keys.HIDE_COURSE] = v }
    suspend fun setHideRoom(v: Boolean) = context.dataStore.edit { it[Keys.HIDE_ROOM] = v }
    suspend fun setHideReason(v: Boolean) = context.dataStore.edit { it[Keys.HIDE_REASON] = v }
    suspend fun setEnableLogging(v: Boolean) = context.dataStore.edit { it[Keys.ENABLE_LOGGING] = v }
    suspend fun setPassTitle(v: String) = context.dataStore.edit { it[Keys.PASS_TITLE] = v }
    suspend fun setStaffInitials(v: String) = context.dataStore.edit { it[Keys.STAFF_INITIALS] = v }

    /** All three written together so uid, name and timestamp can never desync. */
    suspend fun setSelectedStaff(uid: String, name: String) = context.dataStore.edit {
        it[Keys.SELECTED_STAFF_UID] = uid
        it[Keys.SELECTED_STAFF_NAME] = name
        it[Keys.SELECTED_STAFF_AT] = System.currentTimeMillis()
    }

    suspend fun clearSelectedStaff() = context.dataStore.edit {
        it[Keys.SELECTED_STAFF_UID] = ""
        it[Keys.SELECTED_STAFF_NAME] = ""
        it[Keys.SELECTED_STAFF_AT] = 0L
    }

    /** Extends the idle window after activity, without changing who is selected. */
    suspend fun touchSelectedStaff() = context.dataStore.edit {
        if (!it[Keys.SELECTED_STAFF_UID].isNullOrEmpty()) {
            it[Keys.SELECTED_STAFF_AT] = System.currentTimeMillis()
        }
    }

    suspend fun setStaffTimeoutMinutes(v: Int) = context.dataStore.edit { it[Keys.STAFF_TIMEOUT_MIN] = v }
    suspend fun setLastKnownRole(v: String) = context.dataStore.edit { it[Keys.LAST_KNOWN_ROLE] = v }
    suspend fun setBellScheduleJson(v: String) = context.dataStore.edit { it[Keys.BELL_SCHEDULE_JSON] = v }
    suspend fun setSerialDevicePath(v: String) = context.dataStore.edit { it[Keys.SERIAL_PATH] = v }
    suspend fun setBluetoothAddress(v: String) = context.dataStore.edit { it[Keys.BT_ADDRESS] = v }
}
