package org.tarish.adhan.core.result

import java.io.IOException

sealed interface AppError {
    data object NoNetwork : AppError
    data class Http(val code: Int) : AppError
    data object BadData : AppError
    data object LocationServicesOff : AppError
    data object LocationPermissionDenied : AppError
    data object LocationPermissionPermanentlyDenied : AppError
    data object LocationTimeout : AppError
    data object GeocoderUnavailable : AppError
    data object NoLocation : AppError
    data class Unknown(val cause: Throwable) : AppError
}

/** Carries an [AppError] through kotlin.Result. */
class AppException(val error: AppError) : Exception(error.toString())

fun Throwable.toAppError(): AppError = when (this) {
    is AppException -> error
    is retrofit2.HttpException -> AppError.Http(code())
    is IOException -> AppError.NoNetwork
    is kotlinx.serialization.SerializationException -> AppError.BadData
    else -> AppError.Unknown(this)
}

fun <T> failure(e: AppError): Result<T> = Result.failure(AppException(e))

val Result<*>.appError: AppError? get() = exceptionOrNull()?.toAppError()
