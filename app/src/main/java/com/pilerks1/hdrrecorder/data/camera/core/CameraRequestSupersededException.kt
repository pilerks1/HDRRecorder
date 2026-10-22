package com.pilerks1.hdrrecorder.data.camera.core

/** Expected when a camera closes, rebinds, or replaces an outstanding operation. */
class CameraRequestSupersededException(cause: Throwable? = null) : Exception(null, cause)
