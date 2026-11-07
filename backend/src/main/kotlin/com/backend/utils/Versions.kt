package com.backend.utils

import kotlin.math.sign

fun compareVersions(left: String, right: String): Int {
    val leftParts = versionParts(left)
    val rightParts = versionParts(right)

    for(index in 0 until maxOf(leftParts.size, rightParts.size)) {
        val diff = leftParts.getOrElse(index) { 0 }.compareTo(rightParts.getOrElse(index) { 0 })

        if(diff != 0) {
            return diff.sign
        }
    }

    return 0
}

private fun versionParts(version: String): List<Int> =
    version.trim().removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: 0 }