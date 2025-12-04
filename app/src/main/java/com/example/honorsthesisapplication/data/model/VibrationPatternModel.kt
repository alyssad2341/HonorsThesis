package com.example.honorsthesisapplication.data.model

data class VibrationModel(
    val name: String,
    val timings: LongArray,
    val amplitudes: IntArray,
    val sensationTags: List<String>,
    val emotionTags: List<String>,
    val metaphors: List<String>,
    val usageExamples: List<String>
)


object VibrationPatterns {

    val VIB001 = VibrationModel(
        name = "HIGH_HEART_RATE",
        timings = longArrayOf(10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10),
        amplitudes = intArrayOf(0, 7, 29, 28, 110, 251, 217, 151, 246, 229, 160, 236, 211, 99, 31, 31, 18, 30, 2, 0, 0, 0, 0, 0, 0, 0, 0, 70, 53, 70, 253, 175, 159, 255, 187, 185, 236, 93, 82, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        sensationTags = listOf("simple","discontinuous","soft","grainy","short"),
        emotionTags = listOf("comfortable","calm","natural","familiar","pleasant","boring","predictable"),
        metaphors = listOf("hearbeat","pulsing","tapping","poking"),
        usageExamples = listOf("confirmation","get ready","milestone","reminder","battery low","incoming msg","pause","resume")
    )

    // Add more patterns below this using the same format:
    // val LOW_HEART_RATE = VibrationModel(...)
}
