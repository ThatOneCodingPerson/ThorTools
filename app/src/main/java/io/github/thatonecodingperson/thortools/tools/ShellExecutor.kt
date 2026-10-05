package io.github.thatonecodingperson.thortools.tools

import android.annotation.SuppressLint
import android.content.Context
import android.os.IBinder
import android.os.Parcel
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@SuppressLint("DiscouragedPrivateApi", "PrivateApi") // :kekw:
class ShellExecutor @Inject constructor(@ApplicationContext private val context: Context) {

    @Volatile
    private var binder: IBinder? = null
    private val transactLock = Any()
    private val scratchCounter = AtomicLong()

    /** PServer registers late during boot, so a missing service is looked up again on the next call. */
    val pServerAvailable: Boolean get() = pServer() != null

    private fun pServer(): IBinder? {
        binder?.takeIf { it.isBinderAlive }?.let { return it }
        return runCatching {
            Class.forName("android.os.ServiceManager")
                .getDeclaredMethod("getService", String::class.java)
                .invoke(null, SERVICE_NAME) as IBinder?
        }.getOrNull().also { binder = it }
    }

    /**
     * Runs [cmd] through AYN's PServer. Only the first line of output comes back and commands longer than
     * roughly 300 characters are dropped silently; use [capture] or [script] for those cases.
     */
    fun executeAsRoot(cmd: String): Result<String?> {
        val binder = pServer() ?: return Result.failure(IllegalStateException("PServer not available!"))

        // PServer answers overlapping transactions with an empty reply, so calls take turns.
        synchronized(transactLock) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeStringArray(arrayOf(cmd, "1"))
                if (!binder.transact(0, data, reply, 0)) return Result.failure(IllegalStateException("PServer rejected the call"))
                val result = reply.createByteArray()?.toString(Charset.defaultCharset())?.trim()
                Result.success(result?.takeUnless { it == "null" })
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }

    /** True when PServer answers as uid 0. It stops answering once SELinux is enforcing. */
    fun probe(): Boolean = executeAsRoot("id").getOrNull()?.contains("uid=0") == true

    /** Runs [cmd] as root and returns its complete stdout and stderr, routed through a scratch file. */
    fun capture(cmd: String): Result<String> {
        val out = scratchFile("out")
        return script("($cmd) > ${out.absolutePath} 2>&1; chmod 644 ${out.absolutePath}")
            .mapCatching { out.readText().trimEnd() }
            .also { out.delete() }
    }

    /** Runs [body] as a root shell script, which sidesteps PServer's command length limit. */
    fun script(body: String): Result<String?> {
        if (body.length < INLINE_LIMIT && '\n' !in body) return executeAsRoot(body)

        val file = scratchFile("sh")
        return runCatching { file.writeText(body) }
            .mapCatching { file.setReadable(true, false) }
            .fold(
                onSuccess = { executeAsRoot("sh ${file.absolutePath}") },
                onFailure = { Result.failure(it) },
            )
            .also { file.delete() }
    }

    private fun scratchFile(extension: String): File {
        val dir = File(context.cacheDir, "shell").apply {
            mkdirs()
            setExecutable(true, false)
        }
        return File(dir, "${System.currentTimeMillis()}-${scratchCounter.incrementAndGet()}.$extension")
    }

    private fun getProperty(property: String): Result<String?> = executeAsRoot("getprop $property")

    fun getIntProperty(property: String, defaultValue: Int): Int = getProperty(property)
        .mapCatching { it?.toInt() ?: defaultValue }
        .getOrDefault(defaultValue)

    fun getFloatProperty(property: String, defaultValue: Float): Float = getProperty(property)
        .mapCatching { it?.toFloat() ?: defaultValue }
        .getOrDefault(defaultValue)

    fun getBooleanProperty(property: String, defaultValue: Boolean): Boolean = getProperty(property)
        .map { if (it == null) defaultValue else it == "1" }
        .getOrDefault(defaultValue)

    fun getStringProperty(property: String, defaultValue: String): String = getProperty(property)
        .map { it ?: defaultValue }
        .getOrDefault(defaultValue)

    /** AYN's own keys can be read without root; hidden AOSP keys throw for us, so those go through the shell. */
    private fun getSystemSetting(setting: String): Result<String?> =
        runCatching { Settings.System.getString(context.contentResolver, setting) }
            .recoverCatching { executeAsRoot("settings get system $setting").getOrThrow() }

    fun getStringSystemSetting(setting: String, defaultValue: String): String = getSystemSetting(setting)
        .map { it ?: defaultValue }
        .getOrDefault(defaultValue)

    fun setStringSystemSetting(setting: String, value: String) {
        executeAsRoot("settings put system $setting $value")
    }

    fun getIntSystemSetting(setting: String, defaultValue: Int): Int = getSystemSetting(setting)
        .mapCatching { it?.toInt() ?: defaultValue }
        .getOrDefault(defaultValue)

    fun setIntSystemSetting(setting: String, value: Int) {
        executeAsRoot("settings put system $setting $value")
    }

    fun getBooleanSystemSetting(setting: String, defaultValue: Boolean): Boolean = getSystemSetting(setting)
        .map { if (it == null) defaultValue else it == "1" }
        .getOrDefault(defaultValue)

    fun setBooleanSystemSetting(setting: String, value: Boolean) {
        setIntSystemSetting(setting, if (value) 1 else 0)
    }

    private fun getValue(file: String): Result<String?> = executeAsRoot("cat $file")

    fun getStringValue(file: String, defaultValue: String): String = getValue(file)
        .map { it ?: defaultValue }
        .getOrDefault(defaultValue)

    fun setStringValue(file: String, value: String) {
        executeAsRoot("echo $value > $file")
    }

    fun getIntValue(file: String, defaultValue: Int): Int = getValue(file)
        .mapCatching { it?.toInt() ?: defaultValue }
        .getOrDefault(defaultValue)

    fun setIntValue(file: String, value: Int) {
        executeAsRoot("echo $value > $file")
    }

    fun getFloatValue(file: String, defaultValue: Float): Float = getValue(file)
        .mapCatching { it?.toFloat() ?: defaultValue }
        .getOrDefault(defaultValue)

    fun setFloatValue(file: String, value: Float) {
        executeAsRoot("echo $value > $file")
    }

    fun getBooleanValue(file: String, defaultValue: Boolean): Boolean = getValue(file)
        .map { if (it == null) defaultValue else it == "1" }
        .getOrDefault(defaultValue)

    fun setBooleanValue(file: String, value: Boolean) {
        setIntValue(file, if (value) 1 else 0)
    }

    private companion object {
        const val SERVICE_NAME = "PServerBinder"
        const val INLINE_LIMIT = 280
    }
}
