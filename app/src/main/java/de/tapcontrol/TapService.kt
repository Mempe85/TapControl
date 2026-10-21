package de.tapcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class TapService : Service(), SensorEventListener {

    companion object {
        const val ACTION_STOP = "de.tapcontrol.STOP"
        private const val CHANNEL_ID = "tap_service"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TapService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TapService::class.java).setAction(ACTION_STOP))
        }
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var audioManager: AudioManager
    private lateinit var prefs: SharedPreferences
    private var sensorThread: HandlerThread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(android.os.Looper.getMainLooper())

    private val detector = TapDetector(
        onTap = { strength -> TapState.lastTapStrength.value = strength },
        onGesture = { count -> mainHandler.post { handleGesture(count) } },
    )

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == TapState.KEY_THRESHOLD) {
            detector.threshold = p.getFloat(key, TapDetector.DEFAULT_THRESHOLD)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        prefs = TapState.prefs(this)
        detector.threshold = prefs.getFloat(TapState.KEY_THRESHOLD, TapDetector.DEFAULT_THRESHOLD)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )

        if (sensorThread == null) startSensing()
        TapState.running.value = true
        return START_STICKY
    }

    private fun startSensing() {
        // Ohne Wake-Lock liefern viele Handys bei ausgeschaltetem Bildschirm keine Sensordaten.
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TapControl::sensor")
            .apply { acquire() }

        val thread = HandlerThread("tap-sensor").also { it.start() }
        sensorThread = thread
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        detector.reset()
        sensorManager.registerListener(
            this, accel, SensorManager.SENSOR_DELAY_FASTEST, Handler(thread.looper)
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        detector.onSample(event.timestamp, event.values[0], event.values[1], event.values[2])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun handleGesture(count: Int) {
        val (keyCode, label) = when (count) {
            1 -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "1× – Play/Pause"
            2 -> KeyEvent.KEYCODE_MEDIA_NEXT to "2× – Nächster Titel"
            3 -> KeyEvent.KEYCODE_MEDIA_PREVIOUS to "3× – Titel zurück"
            else -> return
        }
        sendMediaKey(keyCode)
        TapState.lastGesture.value = label
        if (prefs.getBoolean(TapState.KEY_VIBRATE, true)) vibrate(count)
    }

    /** Schickt die Medientaste an die aktive Mediensitzung (z. B. Spotify). */
    private fun sendMediaKey(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun vibrate(count: Int) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        // pro erkanntem Klopfer ein kurzer Puls als Bestätigung
        val pattern = LongArray(count * 2) { i -> if (i % 2 == 0) 60L else 30L }
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Klopfsteuerung", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TapService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Klopfsteuerung aktiv")
            .setContentText("1× Play/Pause · 2× Weiter · 3× Zurück")
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(0, "Stopp", stop)
            .build()
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        sensorThread?.quitSafely()
        sensorThread = null
        wakeLock?.takeIf { it.isHeld }?.release()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        TapState.running.value = false
        super.onDestroy()
    }
}
