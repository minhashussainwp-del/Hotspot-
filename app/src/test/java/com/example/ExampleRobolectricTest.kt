package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AccessState
import com.example.data.local.ConnectedDeviceEntity
import com.example.data.local.HotspotDatabase
import com.example.data.local.HotspotPreferences
import com.example.data.local.HotspotRepository
import com.example.network.CapabilityDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify app name resource and unrooted capability detection`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Hotspot Control Pro", appName)

        val detector = CapabilityDetector(context)
        val report = detector.detectCapabilities("UNROOTED_STANDARD")
        assertEquals("UNROOTED_STANDARD", report.activeOperatingMode)
        assertTrue(report.canBindLocalGateway)
        assertTrue(report.items.isNotEmpty())
    }

    @Test
    fun `verify room database persistence for devices profiles and csv export`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, HotspotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefs = HotspotPreferences(context)
        val repo = HotspotRepository(db.hotspotDao(), prefs)

        repo.ensureSeeded()
        val profiles = repo.getProfilesOnce()
        assertEquals(6, profiles.size)

        val friendDevice = ConnectedDeviceEntity(
            deviceId = "ip_192.168.43.105",
            customName = "Friend Tablet",
            detectedHostname = "Galaxy-Tab",
            ipAddress = "192.168.43.105",
            downloadLimitKbps = 500,
            uploadLimitKbps = 250,
            allocatedCombinedBytes = 500L * 1024L * 1024L,
            accessState = AccessState.ALLOWED
        )
        repo.upsertDevice(friendDevice)

        val loaded = repo.getDeviceById("ip_192.168.43.105")
        assertNotNull(loaded)
        assertEquals("Friend Tablet", loaded?.customName)
        assertEquals(500, loaded?.downloadLimitKbps)

        val csv = repo.exportAnalyticsCsv()
        assertTrue(csv.contains("Friend Tablet"))
        assertTrue(csv.contains("192.168.43.105"))

        db.close()
    }
}
