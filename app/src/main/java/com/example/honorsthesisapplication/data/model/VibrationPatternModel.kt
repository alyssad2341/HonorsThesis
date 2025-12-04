package com.example.honorsthesisapplication.data.model

data class VibrationModel(
    val timings: LongArray,
    val amplitudes: IntArray,
    val sensationTags: List<String>,
    val emotionTags: List<String>,
    val metaphors: List<String>,
    val usageExamples: List<String>
)


object VibrationPatterns {

    val VIB000 = VibrationModel(
        timings = longArrayOf(10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10),
        amplitudes = intArrayOf(0, 0, 0, 0, 0, 4, 8, 17, 32, 61, 57, 29, 14, 7, 4, 30, 55, 101, 185, 252, 153, 85, 47, 26, 12, 6, 13, 25, 48, 67, 37, 19, 9, 5, 20, 43, 78, 143, 247, 196, 110, 61, 34, 18, 5, 10, 19, 36, 66, 50, 25, 13, 6, 12, 35, 64, 117, 214, 235, 133, 74, 41, 23, 8, 8, 15, 29, 56, 62, 32, 16, 8, 4, 24, 48, 88, 161, 255, 174, 97, 54, 30, 15, 5, 11, 21, 41, 68, 44, 22, 11, 5),
        sensationTags = listOf("dynamic","regular","grainy","rough"),
        emotionTags = listOf("urgent"),
        metaphors = listOf("tapping","pulsing"),
        usageExamples = listOf("speed up","running out of time","overtime","finish","encouragement","warning","one minute left")
    )

    val VIB011 = VibrationModel(
        timings = longArrayOf(10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10),
        amplitudes = intArrayOf(0, 7, 29, 28, 110, 251, 217, 151, 246, 229, 160, 236, 211, 99, 31, 31, 18, 30, 2, 0, 0, 0, 0, 0, 0, 0, 0, 70, 53, 70, 253, 175, 159, 255, 187, 185, 236, 93, 82, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        sensationTags = listOf("simple","discontinuous","soft","grainy","short"),
        emotionTags = listOf("comfortable","calm","natural","familiar","pleasant","boring","predictable"),
        metaphors = listOf("hearbeat","pulsing","tapping","poking"),
        usageExamples = listOf("confirmation","get ready","milestone","reminder","battery low","incoming msg","pause","resume")
    )

    val all = listOf(
        VIB000,
        VIB011
    )
}
