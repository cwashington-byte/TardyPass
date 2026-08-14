package com.iar.tardypass.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    val enableLogging: Boolean = true,
    val passTitle: String = "IAR Tardy Pass",
    val passFooter: String = "Proceed directly to class.",
    val staffInitials: String = "",             // remembered across passes (PWA re-typed every time)
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
        val ENABLE_LOGGING = booleanPreferencesKey("enable_logging")
        val PASS_TITLE = stringPreferencesKey("pass_title")
        val PASS_FOOTER = stringPreferencesKey("pass_footer")
        val STAFF_INITIALS = stringPreferencesKey("staff_initials")
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
            enableLogging = p[Keys.ENABLE_LOGGING] ?: true,
            passTitle = p[Keys.PASS_TITLE] ?: "IAR Tardy Pass",
            passFooter = p[Keys.PASS_FOOTER] ?: "Proceed directly to class.",
            staffInitials = p[Keys.STAFF_INITIALS] ?: "",
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
    suspend fun setEnableLogging(v: Boolean) = context.dataStore.edit { it[Keys.ENABLE_LOGGING] = v }
    suspend fun setPassTitle(v: String) = context.dataStore.edit { it[Keys.PASS_TITLE] = v }
    suspend fun setStaffInitials(v: String) = context.dataStore.edit { it[Keys.STAFF_INITIALS] = v }
    suspend fun setSerialDevicePath(v: String) = context.dataStore.edit { it[Keys.SERIAL_PATH] = v }
    suspend fun setBluetoothAddress(v: String) = context.dataStore.edit { it[Keys.BT_ADDRESS] = v }
}
