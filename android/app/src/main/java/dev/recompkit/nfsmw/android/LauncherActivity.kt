package dev.recompkit_nfsmw.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import dev.recompkit_nfsmw.android.game.AssetStager
import dev.recompkit_nfsmw.android.game.GameActivity

/**
 * The launcher: status of the game library and files, Play, touch controls
 * editor, settings and about. The game itself runs in [GameActivity].
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var buildChip: TextView
    private lateinit var binaryStatus: TextView
    private lateinit var filesStatus: TextView
    private lateinit var deviceStatus: TextView
    private lateinit var playButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        buildChip = findViewById(R.id.build_chip)
        binaryStatus = findViewById(R.id.binary_status)
        filesStatus = findViewById(R.id.files_status)
        deviceStatus = findViewById(R.id.device_status)
        playButton = findViewById(R.id.play_button)

        findViewById<Button>(R.id.controls_button).setOnClickListener {
            startActivity(Intent(this, ControlsActivity::class.java))
        }
        findViewById<Button>(R.id.settings_button).setOnClickListener { showSettingsDialog() }
        findViewById<Button>(R.id.about_button).setOnClickListener { showAboutDialog() }
        playButton.setOnClickListener { onPlay() }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        buildChip.text = if (BuildInfo.isReleaseBuild) {
            getString(R.string.build_chip_release, BuildInfo.VERSION_NAME, BuildInfo.BUILD_ID)
        } else {
            getString(R.string.build_chip_dev)
        }

        binaryStatus.text = if (BuildInfo.hasGame) {
            getString(R.string.status_binary_built, BuildInfo.BUILD_ID, BuildInfo.LIB_SHA.take(8))
        } else {
            getString(R.string.status_binary_missing)
        }

        val staged = AssetStager.stagedReport(this)
        filesStatus.text = when {
            !BuildInfo.hasAssets -> getString(R.string.status_files_none)
            staged.ok -> getString(R.string.status_files_staged, BuildInfo.ASSET_COUNT)
            else -> getString(
                R.string.status_files_bundled,
                BuildInfo.ASSET_COUNT,
                formatMebibytes(BuildInfo.ASSET_BYTES)
            )
        }

        deviceStatus.text = getString(
            R.string.status_device_value,
            Build.SUPPORTED_ABIS.firstOrNull() ?: "?",
            Build.VERSION.RELEASE
        )

        playButton.isEnabled = BuildInfo.hasGame
        playButton.contentDescription = getString(
            if (BuildInfo.hasGame) R.string.cd_play else R.string.cd_play_disabled
        )
    }

    private fun formatMebibytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1000) "${(mb / 1000.0).let { String.format("%.1f", it) }} GB"
        else String.format("%.0f", mb) + " MB"
    }

    private fun onPlay() {
        if (!BuildInfo.hasGame) {
            AlertDialog.Builder(this)
                .setTitle(R.string.no_game_title)
                .setMessage(R.string.no_game_body)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        if (!AssetStager.staged(this)) {
            stageThenStart()
        } else {
            startGame()
        }
    }

    /** Copies the bundled game files with a progress dialog, then starts. */
    private fun stageThenStart() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_progress, null)
        val bar: ProgressBar = dialogView.findViewById(R.id.progress_bar)
        val text: TextView = dialogView.findViewById(R.id.progress_text)
        val dialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(false).show()

        val ui = Handler(Looper.getMainLooper())
        Thread({
            val report = AssetStager.stage(this) { p ->
                val totalBytes = BuildInfo.ASSET_BYTES.coerceAtLeast(1L)
                val pct = (p.bytesDone * 1000L / totalBytes).toInt().coerceIn(0, 1000)
                ui.post {
                    bar.max = 1000
                    bar.progress = pct
                    text.text = getString(
                        R.string.staging_body, p.filesDone, p.filesTotal,
                        formatMebibytes(p.bytesDone), formatMebibytes(totalBytes)
                    )
                }
            }
            ui.post {
                dialog.dismiss()
                if (report.ok) startGame()
                else {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.staging_failed)
                        .setMessage(report.message)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }
            }
        }, "nfsmw-stager").start()
    }

    private fun startGame() {
        startActivity(Intent(this, GameActivity::class.java))
    }

    private fun showSettingsDialog() {
        val v = layoutInflater.inflate(R.layout.dialog_settings, null)
        val scaleValue: TextView = v.findViewById(R.id.scale_value)
        val limitValue: TextView = v.findViewById(R.id.limit_value)
        val scaleBar: SeekBar = v.findViewById(R.id.scale_bar)
        val limitBar: SeekBar = v.findViewById(R.id.limit_bar)
        val audio: SwitchCompat = v.findViewById(R.id.audio_switch)
        val haptics: SwitchCompat = v.findViewById(R.id.haptics_switch)

        val s = SettingsStore.load(this)
        scaleBar.max = Math.round((SettingsStore.MAX_SCALE - SettingsStore.MIN_SCALE) / 0.05f).toInt()
        scaleBar.progress = Math.round((s.renderScale - SettingsStore.MIN_SCALE) / 0.05f).toInt()
        limitBar.max = SettingsStore.FRAME_LIMITS.size - 1
        limitBar.progress = SettingsStore.FRAME_LIMITS.indexOf(s.frameLimit).coerceAtLeast(0)
        audio.isChecked = s.audio
        haptics.isChecked = s.haptics

        fun syncLabels() {
            scaleValue.text = String.format("%.2f", s.renderScale)
            val limit = SettingsStore.FRAME_LIMITS[limitBar.progress]
            limitValue.text = getString(
                if (limit == 0) R.string.settings_unlimited else R.string.settings_fps_value, limit
            )
        }
        syncLabels()

        scaleBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    s.renderScale = (SettingsStore.MIN_SCALE + progress * 0.05f)
                        .coerceIn(SettingsStore.MIN_SCALE, SettingsStore.MAX_SCALE)
                    SettingsStore.save(this@LauncherActivity, s)
                    syncLabels()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        limitBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    s.frameLimit = SettingsStore.FRAME_LIMITS[limitBar.progress]
                    SettingsStore.save(this@LauncherActivity, s)
                    syncLabels()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        audio.setOnCheckedChangeListener { _, checked ->
            s.audio = checked
            SettingsStore.save(this, s)
        }
        haptics.setOnCheckedChangeListener { _, checked ->
            s.haptics = checked
            SettingsStore.save(this, s)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(v)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showAboutDialog() {
        val body = getString(
            R.string.about_body,
            BuildInfo.VERSION_NAME,
            BuildInfo.BUILD_ID,
            if (BuildInfo.LIB_SHA.isEmpty()) "-" else BuildInfo.LIB_SHA.take(12),
            BuildInfo.ASSET_COUNT,
            if (BuildInfo.hasAssets) formatMebibytes(BuildInfo.ASSET_BYTES) else "-",
            if (BuildInfo.SOURCE_URL.isEmpty()) "-" else BuildInfo.SOURCE_URL,
            if (BuildInfo.BUILT_AT.isEmpty()) "-" else BuildInfo.BUILT_AT
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setMessage(body)
            .setPositiveButton(R.string.ok, null)
            .show()
    }
}
