package org.tarish.adhan.ui.util

import androidx.annotation.StringRes
import org.tarish.adhan.R
import org.tarish.adhan.core.result.AppError

/** Friendly copy for each error; never the raw exception text (BUG-28). */
@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.NoNetwork -> R.string.error_no_network
    is AppError.Http -> R.string.error_server
    AppError.BadData -> R.string.error_bad_data
    AppError.LocationServicesOff -> R.string.error_location_off
    AppError.LocationPermissionDenied -> R.string.error_location_denied
    AppError.LocationPermissionPermanentlyDenied -> R.string.error_location_denied_forever
    AppError.LocationTimeout -> R.string.error_location_timeout
    AppError.GeocoderUnavailable -> R.string.error_geocoder_unavailable
    AppError.NoLocation -> R.string.home_set_location_body
    is AppError.Unknown -> R.string.error_prayer_times_generic
}

val AppError.isLocationError: Boolean
    get() = this is AppError.LocationServicesOff || this is AppError.LocationPermissionDenied ||
        this is AppError.LocationPermissionPermanentlyDenied || this is AppError.LocationTimeout || this is AppError.NoLocation
