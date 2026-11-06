package com.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
class BackendApplication

fun main(args: Array<String>) {
    runApplication<BackendApplication>(*args)
}