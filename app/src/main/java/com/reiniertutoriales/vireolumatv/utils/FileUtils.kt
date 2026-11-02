package com.reiniertutoriales.vireolumatv.utils

import java.io.File

/**
 * Deletes [file] recursively. Returns false when it does not exist or cannot be fully deleted.
 */
fun deleteDirectory(file: File): Boolean = file.exists() && file.deleteRecursively()
