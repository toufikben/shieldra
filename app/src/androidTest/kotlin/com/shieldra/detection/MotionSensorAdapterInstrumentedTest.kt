package com.shieldra.detection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * Batch G / slice G3: the adapter amendment A-12 approved, tested on real hardware.
 *
 * What is asserted here is only what the approved scope promises: the adapter can find the
 * accelerometer, register and unregister it, hand over samples stamped by the *sensor* clock, stop
 * when told to, and report a stream it cannot read instead of pretending. The one-shot
 * `SIGNIFICANT_MOTION` tripwire is only *seen*: G3 never arms it, because A-12 forbids mixing a
 * trigger sensor into the continuous layer, and the static gate forbids the trigger API anywhere in
 * `app/src/main`.
 *
 * `REQUESTED_REPORT_PERIOD_US` is exactly what its name says — a request. Android treats the
 * reporting period as a suggestion, and slice G2 measured that RMX3910 delivered around 106 Hz for a
 * 20 ms request. So every rate below is **computed from the timestamps that arrived** and compared
 * against a median computed independently in this file, never against the requested period.
 */
@RunWith(AndroidJUnit4::class)
class MotionSensorAdapterInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    @Test
    fun the_adapter_reports_which_motion_sources_exist_without_touching_either() {
        val adapter = MotionSensorAdapter(sensorManager)

        assertTrue(
            "this detector cannot run without an accelerometer, and RMX3910 has one (measured in G2)",
            adapter.accelerometerAvailable,
        )
        assertNull(
            "availability must not have side effects: nothing may be registered yet",
            adapter.registeredSensorType,
        )
        assertEquals(0, adapter.deliveredSampleCount)
        Log.i(TAG, "significant_motion_available=${adapter.significantMotionAvailable}")
    }

    @Test
    fun starting_reporting_registers_the_accelerometer_and_nothing_else() {
        val adapter = MotionSensorAdapter(sensorManager)
        val received = Received()

        val result = adapter.startReporting(received::offer, REQUESTED_REPORT_PERIOD_US)
        try {
            assertEquals(MotionStartResult.Started, result)
            assertEquals(
                "A-12 allows only the continuous accelerometer inside this layer",
                Sensor.TYPE_ACCELEROMETER,
                adapter.registeredSensorType,
            )
            assertTrue("the adapter must say that it is reporting", adapter.isReporting)
        } finally {
            adapter.stopReporting()
        }
    }

    @Test
    fun the_adapter_delivers_accelerometer_samples_stamped_by_the_sensor_clock() {
        val adapter = MotionSensorAdapter(sensorManager)
        val received = Received()

        adapter.startReporting(received::offer, REQUESTED_REPORT_PERIOD_US)
        Thread.sleep(WINDOW_MS)
        adapter.stopReporting()

        assertTrue(
            "a resumed app received events in G2, so fewer than two samples here means the adapter lost them: ${received.samples}",
            received.samples.size >= 2,
        )
        for (index in received.samples.indices) {
            val sample = received.samples[index]
            assertTrue(
                "sample $index is not a finite acceleration: ${sample.x}, ${sample.y}, ${sample.z}",
                sample.isFiniteAcceleration(),
            )
            if (index > 0) {
                assertTrue(
                    "samples must leave the adapter in strictly increasing sensor time",
                    sample.elapsedMs > received.samples[index - 1].elapsedMs,
                )
            }
        }
        assertEquals(
            "every sample the adapter handed over must be counted",
            received.samples.size,
            adapter.deliveredSampleCount,
        )
        Log.i(TAG, "delivered=${received.samples.size} clockResolutionDrops=${adapter.clockResolutionDrops}")
    }

    @Test
    fun stopping_the_adapter_stops_the_stream() {
        val adapter = MotionSensorAdapter(sensorManager)
        val received = Received()

        adapter.startReporting(received::offer, REQUESTED_REPORT_PERIOD_US)
        Thread.sleep(WINDOW_MS)
        adapter.stopReporting()
        val afterStop = received.samples.size
        val typeAfterStop = adapter.registeredSensorType

        assertTrue("the first window delivered nothing, so the second window would prove nothing", afterStop >= 2)
        Thread.sleep(WINDOW_MS)
        assertEquals("unregistering must end the stream, not merely stop the caller's interest", afterStop, received.samples.size)
        assertNull("nothing may stay registered after stop", typeAfterStop)
        assertFalse("stop must be honest about there being nothing to stop", adapter.stopReporting())
    }

    @Test
    fun the_measured_rate_is_computed_from_delivered_timestamps_not_from_the_request() {
        val adapter = MotionSensorAdapter(sensorManager)
        val received = Received()

        adapter.startReporting(received::offer, REQUESTED_REPORT_PERIOD_US)
        Thread.sleep(WINDOW_MS)
        adapter.stopReporting()

        val signal = MotionSignalNormalizer(STALL_TOLERANCE_FACTOR).normalize(received.samples)
        assertNotNull("a delivered stream must carry a measured interval", signal.medianIntervalMs)
        val median = signal.medianIntervalMs!!
        assertEquals(
            "the normalizer must report the interval the timestamps say, not the requested period",
            independentMedianIntervalMs(received.samples),
            median,
            1e-9,
        )
        assertEquals(1000.0 / median, signal.measuredRateHz!!, 1e-9)
        assertEquals(MotionSignalQuality.USABLE, signal.quality)
        Log.i(
            TAG,
            "requested_period_us=$REQUESTED_REPORT_PERIOD_US measured_median_interval_ms=$median " +
                "measured_rate_hz=${signal.measuredRateHz} longest_gap_ms=${signal.longestIntervalMs} " +
                "clockResolutionDrops=${adapter.clockResolutionDrops}",
        )
    }

    @Test
    fun a_device_without_an_accelerometer_is_reported_as_unavailable_and_never_registers() {
        val adapter = MotionSensorAdapter(sensorManager, accelerometer = null)
        val received = Received()

        assertEquals(
            "a missing accelerometer is a fact to report, not an exception to crash on",
            MotionStartResult.NoAccelerometer,
            adapter.startReporting(received::offer, REQUESTED_REPORT_PERIOD_US),
        )
        assertNull(adapter.registeredSensorType)
        assertEquals(0, adapter.deliveredSampleCount)
        assertEquals(0, received.samples.size)
    }

    @Test
    fun a_fresh_adapter_after_a_process_recreation_is_unregistered_and_silent() {
        val first = MotionSensorAdapter(sensorManager)
        first.startReporting(Received()::offer, REQUESTED_REPORT_PERIOD_US)
        Thread.sleep(SETTLE_MS)
        first.stopReporting()

        val recreated = MotionSensorAdapter(sensorManager)
        assertFalse("a new process must not inherit a monitoring claim", recreated.isReporting)
        assertEquals(0, recreated.deliveredSampleCount)
        assertEquals(0, recreated.clockResolutionDrops)
        assertNull(recreated.registeredSensorType)
    }

    private class Received {
        val samples: MutableList<MotionSample> = Collections.synchronizedList(mutableListOf<MotionSample>())

        fun offer(sample: MotionSample) {
            samples.add(sample)
        }
    }

    /** Computed here, on purpose, so the normalizer cannot be agreeing with itself. */
    private fun independentMedianIntervalMs(samples: List<MotionSample>): Double {
        val intervals = samples.sortedBy { it.elapsedMs }.zipWithNext { earlier, later -> later.elapsedMs - earlier.elapsedMs }
        if (intervals.isEmpty()) return 0.0
        val sorted = intervals.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle].toDouble()
        }
    }

    private fun MotionSample.isFiniteAcceleration(): Boolean =
        x.isFinite() && y.isFinite() && z.isFinite()

    private companion object {
        const val TAG = "MotionAdapterG3"
        const val REQUESTED_REPORT_PERIOD_US = 20_000
        const val WINDOW_MS = 1_500L
        const val SETTLE_MS = 400L
        const val STALL_TOLERANCE_FACTOR = 3.0
    }
}
