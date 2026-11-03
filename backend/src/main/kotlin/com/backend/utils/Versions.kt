package com.backend.utils

import kotlin.math.sign

fun compareVersions(left: String, right: String): Int {
    val leftParts = left.split(".").map { it.toIntOrNull() ?: 0 }
    val rightParts = right.split(".").map { it.toIntOrNull() ?: 0 }

    for(index in 0 until maxOf(leftParts.size, rightParts.size)) {
        val diff = leftParts.getOrElse(index) { 0 } - rightParts.getOrElse(index) { 0 }

        if(diff != 0) {
            return diff.sign
        }
    }

    return 0
}