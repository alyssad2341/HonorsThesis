package com.example.honorsthesisapplication.data.model

import android.content.Context
import com.example.honorsthesisapplication.R
import org.json.JSONArray
import java.io.BufferedReader
import java.util.concurrent.atomic.AtomicBoolean

data class VibrationModel(
    val id: String,
    val timings: LongArray,
    val amplitudes: IntArray,
    val sensationTags: List<String>,
    val emotionTags: List<String>,
    val metaphors: List<String>,
    val usageExamples: List<String>,
    val imagePath: Int
)

/**
 * Matches your JSON structure (imagePath is a string like "vib000")
 */
private data class VibrationModelJson(
    val id: String,
    val timings: LongArray,
    val amplitudes: IntArray,
    val sensationTags: List<String>,
    val emotionTags: List<String>,
    val metaphors: List<String>,
    val usageExamples: List<String>,
    val imagePathName: String
)

object VibrationPatterns {

    private const val ASSET_FILE = "vibration_patterns.json"

    // We load once and keep in memory
    @Volatile private var cached: List<VibrationModel> = emptyList()
    private val initialized = AtomicBoolean(false)

    /**
     * Call once at app start (Application or first Activity).
     */
    fun init(context: Context) {
        if (initialized.getAndSet(true)) return
        cached = loadFromAssets(context.applicationContext)
    }

    /**
     * Safe access — returns empty list if init() wasn't called yet.
     * (But you SHOULD call init() early.)
     */
    val all: List<VibrationModel>
        get() = cached

    fun getById(id: String?): VibrationModel? {
        if (id == null) return null
        return cached.firstOrNull { it.id == id }
    }

    private fun loadFromAssets(context: Context): List<VibrationModel> {
        val jsonText = context.assets.open(ASSET_FILE).use { input ->
            input.bufferedReader().use(BufferedReader::readText)
        }

        val arr = JSONArray(jsonText)
        val out = ArrayList<VibrationModel>(arr.length())

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)

            val id = obj.getString("id")
            val timings = obj.getJSONArray("timings").toLongArray()
            val amplitudes = obj.getJSONArray("amplitudes").toIntArray()

            val sensationTags = obj.getJSONArray("sensationTags").toStringList()
            val emotionTags = obj.getJSONArray("emotionTags").toStringList()
            val metaphors = obj.getJSONArray("metaphors").toStringList()
            val usageExamples = obj.getJSONArray("usageExamples").toStringList()

            // From JSON: "vib000", "vib001", etc.
            val imagePathName = obj.getString("imagePath")

            // Convert drawable name -> resource id
            val imageResId = context.resources.getIdentifier(
                imagePathName,
                "drawable",
                context.packageName
            ).let { resId ->
                if (resId != 0) resId else R.drawable.ic_launcher_foreground
            }

            // Optional: sanity check lengths match
            if (timings.size != amplitudes.size) {
                // If you prefer, throw instead of skipping
                // throw IllegalArgumentException("timings/amplitudes length mismatch for $id")
                continue
            }

            out.add(
                VibrationModel(
                    id = id,
                    timings = timings,
                    amplitudes = amplitudes,
                    sensationTags = sensationTags,
                    emotionTags = emotionTags,
                    metaphors = metaphors,
                    usageExamples = usageExamples,
                    imagePath = imageResId
                )
            )
        }

        return out
    }
}

/** Helpers */
private fun JSONArray.toLongArray(): LongArray {
    val out = LongArray(length())
    for (i in 0 until length()) out[i] = getLong(i)
    return out
}

private fun JSONArray.toIntArray(): IntArray {
    val out = IntArray(length())
    for (i in 0 until length()) out[i] = getInt(i)
    return out
}

private fun JSONArray.toStringList(): List<String> {
    val out = ArrayList<String>(length())
    for (i in 0 until length()) out.add(getString(i))
    return out
}
