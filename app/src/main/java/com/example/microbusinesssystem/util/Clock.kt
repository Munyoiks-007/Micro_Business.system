package com.example.microbusinesssystem.util

import javax.inject.Inject
import javax.inject.Singleton

interface Clock {
    fun currentTimeMillis(): Long
}

@Singleton
class SystemClock @Inject constructor() : Clock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
