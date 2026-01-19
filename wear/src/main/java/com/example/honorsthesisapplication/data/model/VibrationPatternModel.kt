package com.example.honorsthesisapplication.data.model

import com.example.honorsthesisapplication.R

data class VibrationModel(
    val id: String,
    val timings: LongArray,
    val amplitudes: IntArray
)

object VibrationPatterns {

    // default vibration pattern
    val default = VibrationModel(
        id = "default",
        timings = longArrayOf(10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10),
        amplitudes = intArrayOf(0, 0, 0, 0, 0, 4, 8, 17, 32, 61, 57, 29, 14, 7, 4, 30, 55, 101, 185, 252, 153, 85, 47, 26, 12, 6, 13, 25, 48, 67, 37, 19, 9, 5, 20, 43, 78, 143, 247, 196, 110, 61, 34, 18, 5, 10, 19, 36, 66, 50, 25, 13, 6, 12, 35, 64, 117, 214, 235, 133, 74, 41, 23, 8, 8, 15, 29, 56, 62, 32, 16, 8, 4, 24, 48, 88, 161, 255, 174, 97, 54, 30, 15, 5, 11, 21, 41, 68, 44, 22, 11, 5)
    )

    val allPatterns = listOf(default)

    fun getById(id: String?): VibrationModel {
        return allPatterns.find { it.id == id } ?: default
    }
}
