package com.example.media

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import android.app.Activity

class MainActivity : Activity(), SeekBar.OnSeekBarChangeListener {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvTrackName: TextView
    private lateinit var playBtn: Button
    private lateinit var pauseBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var selectTrackBtn: Button
    private lateinit var tracksListView: ListView
    private lateinit var tracksContainer: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var isPaused = false
    private var isReady = false
    private var currentTrack = ""
    private val TAG = "MediaActivity"

    private val trackList = listOf(
        "Трек A" to "treka",
        "Трек B" to "trekb",
        "Трек C" to "trekc"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media)

        playBtn      = findViewById(R.id.playBtn)
        pauseBtn     = findViewById(R.id.pauseBtn)
        stopBtn      = findViewById(R.id.stopBtn)
        selectTrackBtn = findViewById(R.id.selectTrackBtn)
        tracksListView = findViewById(R.id.tracksListView)
        tracksContainer = findViewById(R.id.tracksContainer)
        seekBar      = findViewById(R.id.seek_bar)
        tvCurrentTime = findViewById(R.id.tv_pass)
        tvDuration   = findViewById(R.id.tv_due)
        tvTrackName  = findViewById(R.id.tvTrackName)

        seekBar.setOnSeekBarChangeListener(this)

        setControlsEnabled(play = false, pause = false, stop = false)

        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_1,
            trackList.map { it.first }
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(0xFFCCCCDD.toInt())
                v.textSize = 15f
                v.setPadding(40, 32, 40, 32)
                v.setBackgroundColor(0x00000000)
                return v
            }
        }
        tracksListView.adapter = adapter

        selectTrackBtn.setOnClickListener {
            tracksContainer.visibility =
                if (tracksContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        tracksListView.setOnItemClickListener { _, _, position, _ ->
            val (name, file) = trackList[position]
            tracksContainer.visibility = View.GONE
            loadTrack(file, name)
        }

        playBtn.setOnClickListener  { playCurrentTrack() }
        pauseBtn.setOnClickListener { pauseCurrentTrack() }
        stopBtn.setOnClickListener  { stopCurrentTrack() }
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private fun playCurrentTrack() {
        val player = mediaPlayer ?: return showToast("Сначала выберите трек")
        if (!isReady) return showToast("Трек ещё не готов")
        if (player.isPlaying) return

        player.start()
        isPaused = false
        updateSeekBar()
        setControlsEnabled(play = false, pause = true, stop = true)
    }

    private fun pauseCurrentTrack() {
        val player = mediaPlayer ?: return
        if (!player.isPlaying) return

        player.pause()
        isPaused = true
        stopSeekBarUpdates()
        setControlsEnabled(play = true, pause = false, stop = true)
        showToast("Пауза")
    }

    private fun stopCurrentTrack() {
        val player = mediaPlayer ?: return
        if (!player.isPlaying && !isPaused) return

        player.stop()
        stopSeekBarUpdates()

        // Reload so we can play again from the start
        val file = trackList.find { it.first == currentTrack }?.second ?: return
        loadTrack(file, currentTrack)
        showToast("Остановлено")
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    private fun loadTrack(trackFile: String, trackName: String) {
        releasePlayer()

        val resId = resources.getIdentifier(trackFile, "raw", packageName)
        if (resId == 0) {
            Log.e(TAG, "Resource not found: $trackFile")
            showToast("Файл не найден: $trackFile (проверь res/raw/)")
            return
        }

        val player = MediaPlayer.create(this, resId) ?: run {
            showToast("Не удалось создать плеер")
            return
        }

        mediaPlayer = player
        isReady = true
        currentTrack = trackName
        tvTrackName.text = trackName

        seekBar.max = player.duration
        seekBar.progress = 0
        tvDuration.text = formatTime(player.duration)
        tvCurrentTime.text = "00:00"

        player.setOnCompletionListener {
            seekBar.progress = 0
            tvCurrentTime.text = "00:00"
            stopSeekBarUpdates()
            setControlsEnabled(play = true, pause = false, stop = false)
        }

        setControlsEnabled(play = true, pause = false, stop = false)
        showToast("«$trackName» — нажмите ▶")
    }

    // ── SeekBar ───────────────────────────────────────────────────────────────

    private fun updateSeekBar() {
        stopSeekBarUpdates()
        runnable = Runnable {
            val player = mediaPlayer ?: return@Runnable
            if (player.isPlaying) {
                val pos = player.currentPosition
                seekBar.progress = pos
                tvCurrentTime.text = formatTime(pos)
                handler.postDelayed(runnable!!, 100)
            }
        }
        handler.postDelayed(runnable!!, 100)
    }

    private fun stopSeekBarUpdates() {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            mediaPlayer?.seekTo(progress)
            tvCurrentTime.text = formatTime(progress)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
    override fun onStopTrackingTouch(seekBar: SeekBar?) {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun setControlsEnabled(play: Boolean, pause: Boolean, stop: Boolean) {
        playBtn.isEnabled  = play
        pauseBtn.isEnabled = pause
        stopBtn.isEnabled  = stop
    }

    private fun releasePlayer() {
        stopSeekBarUpdates()
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "release error: ${e.message}")
        }
        mediaPlayer = null
        isReady = false
        isPaused = false
    }

    private fun formatTime(ms: Int): String {
        val s = ms / 1000
        return "%02d:%02d".format(s / 60, s % 60)
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }
}