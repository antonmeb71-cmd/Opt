package com.orbit.optimizer

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private val REQ_SHIZUKU = 1001
    private lateinit var metrics: TextView
    private lateinit var log: TextView

    private val permListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQ_SHIZUKU) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    applyDeepOptimization()
                } else {
                    appendLog("Shizuku: право не выдано — глубокая оптимизация недоступна")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permListener)
        buildUi()
        refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permListener)
    }

    private fun buildUi() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun title(s: String) = TextView(this).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setPadding(0, pad, 0, pad / 2)
        }
        fun button(text: String, action: () -> Unit) = Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        }

        metrics = TextView(this).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f) }
        log = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(0xFF666666.toInt())
        }

        root.addView(title("Оптимизатор"))
        root.addView(metrics)
        root.addView(button("Обновить показатели") { refresh() })

        root.addView(title("Быстрая оптимизация"))
        root.addView(button("Оптимизировать всё") { optimizeAll() })

        root.addView(title("Отдельные действия"))
        root.addView(button("Адаптивная яркость") { setAdaptiveBrightness() })
        root.addView(button("Таймаут экрана: 30 сек") { setScreenTimeout() })
        root.addView(button("Выгрузить фоновые приложения") { killBackgrounds() })
        root.addView(button("Глубокая оптимизация (Shizuku)") { requestShizukuAndApply() })
        root.addView(button("Настройки батареи") {
            startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        })
        root.addView(button("Хранилище") {
            startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        })

        root.addView(title("Журнал"))
        root.addView(log)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun refresh() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val ramUsedMb = (mi.totalMem - mi.availMem) / 1048576
        val ramTotalMb = mi.totalMem / 1048576
        val ramFreePct = if (mi.totalMem > 0) mi.availMem * 100 / mi.totalMem else 0

        val stat = StatFs(Environment.getDataDirectory().path)
        val storageFreeGb = java.lang.String.format(
            java.util.Locale.US, "%.1f", stat.availableBytes / 1073741824.0
        )
        val storageTotalGb = java.lang.String.format(
            java.util.Locale.US, "%.1f", stat.totalBytes / 1073741824.0
        )

        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val battIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = (battIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0

        val upMs = SystemClock.elapsedRealtime()
        val hours = upMs / 3600000
        val minutes = (upMs % 3600000) / 60000

        metrics.text = listOf(
            "RAM: " + ramUsedMb + " из " + ramTotalMb + " МБ (свободно " + ramFreePct + "%)",
            "Хранилище: свободно " + storageFreeGb + " из " + storageTotalGb + " ГБ",
            "Батарея: " + level + "%, температура " + temp + " град. C",
            "Время работы: " + hours + " ч " + minutes + " мин",
            "Анимации: " + readAnimState()
        ).joinToString("\n")
    }

    private fun readAnimState(): String {
        val w = Settings.Global.getFloat(
            contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f
        )
        return if (w == 0f) "отключены" else "включены, шкала " + w
    }

    private fun optimizeAll() {
        setAdaptiveBrightness()
        setScreenTimeout()
        killBackgrounds()
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            applyDeepOptimization()
        } else {
            appendLog(
                "Быстрая оптимизация выполнена. " +
                    "Для анимаций нужен Shizuku — нажмите «Глубокая оптимизация»."
            )
        }
        refresh()
    }

    private fun setAdaptiveBrightness() {
        val mode = Settings.System.getInt(
            contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1
        )
        if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            appendLog("Адаптивная яркость уже включена")
            return
        }
        if (Settings.System.canWrite(this)) {
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            )
            appendLog("Адаптивная яркость включена")
        } else {
            appendLog("Нужно право на изменение настроек — открою экран выдачи")
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + packageName)
                )
            )
        }
    }

    private fun setScreenTimeout() {
        if (!Settings.System.canWrite(this)) {
            appendLog("Нужно право на изменение настроек — открою экран выдачи")
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + packageName)
                )
            )
            return
        }
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30000)
        appendLog("Таймаут экрана установлен: 30 секунд")
    }

    private fun killBackgrounds() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val procs = am.runningAppProcesses ?: return
        var count = 0
        for (p in procs) {
            if (p.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED) {
                am.killBackgroundProcesses(p.processName.split(":")[0])
                count++
            }
        }
        appendLog(
            "Запрос на выгрузку отправлен для " + count + " процессов. " +
                "Часть система может игнорировать — это её право."
        )
    }

    private fun requestShizukuAndApply() {
        if (!Shizuku.pingBinder()) {
            appendLog("Shizuku не запущен. Установите приложение Shizuku и запустите сервер.")
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            applyDeepOptimization()
        } else {
            Shizuku.requestPermission(REQ_SHIZUKU)
        }
    }

    private fun shizukuCmd(cmd: String): String? = try {
        val proc = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
        proc.waitFor()
        proc.inputStream.bufferedReader().readText().trim().ifEmpty { null }
    } catch (e: Exception) {
        "ошибка: " + e.message
    }

    private fun applyDeepOptimization() {
        val cmds = listOf(
            "settings put global window_animation_scale 0.5",
            "settings put global transition_animation_scale 0.5",
            "settings put global animator_duration_scale 0.5",
            "settings put global low_power 1"
        )
        for (cmd in cmds) {
            val out = shizukuCmd(cmd)
            appendLog(cmd + " — " + (out ?: "выполнено"))
        }
        appendLog("Глубокая оптимизация завершена. Включён энергосберегающий режим.")
        refresh()
    }

    private fun appendLog(s: String) {
        log.text = if (log.text.isEmpty()) s else (log.text as String) + "\n" + s
    }
}
