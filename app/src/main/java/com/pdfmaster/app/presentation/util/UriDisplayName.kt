package com.pdfmaster.app.presentation.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** SAF Uris don't carry a readable filename directly - it has to be queried. */
fun Context.displayNameOf(uri: Uri): String =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } ?: (uri.lastPathSegment ?: "document.pdf")
