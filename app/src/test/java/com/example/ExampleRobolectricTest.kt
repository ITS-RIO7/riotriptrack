package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.LocationPointEntity
import com.example.data.model.TripEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  private lateinit var db: AppDatabase

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Trip Tracker", appName)
  }

  @Test
  fun `insert and retrieve trip with location points`() = runBlocking {
    val trip = TripEntity(
        title = "Highway Test Ride",
        category = "Drive",
        startTime = System.currentTimeMillis(),
        distanceMeters = 5400.0,
        avgSpeedKmh = 48.5,
        maxSpeedKmh = 72.0,
        stayCount = 2,
        totalStayDurationMs = 180000L
    )
    val tripId = db.tripDao().insertTrip(trip)

    val point = LocationPointEntity(
        tripId = tripId,
        latitude = 37.7749,
        longitude = -122.4194,
        speedKmh = 45.0,
        timestamp = System.currentTimeMillis(),
        isStayPoint = true,
        stayDurationMs = 120000L
    )
    db.locationPointDao().insertPoint(point)

    val trips = db.tripDao().getAllTrips().first()
    assertEquals(1, trips.size)
    assertEquals("Highway Test Ride", trips[0].title)

    val points = db.locationPointDao().getPointsForTrip(tripId).first()
    assertEquals(1, points.size)
    assertEquals(true, points[0].isStayPoint)
  }
}
