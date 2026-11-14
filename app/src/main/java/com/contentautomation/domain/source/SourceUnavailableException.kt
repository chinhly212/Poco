package com.contentautomation.domain.source

class SourceUnavailableException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
