package org.love2d.android

import android.os.Environment
import java.io.File

/**
 * 应用内使用的目录和常量
 */
object AppConstants {
    const val MODS_FOLDER_NAME = "mods"
    const val GAME_FOLDER_NAME = "hLauncherGame"

    val GAME_FOLDER_PATH: File =
        File(Environment.getExternalStorageDirectory(), GAME_FOLDER_NAME)
}