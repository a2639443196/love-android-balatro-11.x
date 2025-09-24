package org.love2d.android.util

import android.content.Context
import android.util.Log
import org.love2d.android.AppConstants
import java.io.File

/**
 * 通用的文件和目录操作工具
 */
object AppFileUtils {

    /**
     * 检查并创建本地游戏根目录
     */
    fun checkOrCreateLocalGameFolder(): Boolean {
        val gameFolder = AppConstants.GAME_FOLDER_PATH
        Log.d("AppFileUtils", "Game folder path: $gameFolder")
        return if (gameFolder.exists()) {
            Log.d("AppFileUtils", "Game folder exists.")
            true
        } else {
            val success = gameFolder.mkdirs()
            Log.d("AppFileUtils", "Game folder creation result: $success")
            success
        }
    }

    /**
     * 检查并创建本地 Mods 根目录
     */
    fun checkOrCreateLocalModsFolder(context: Context): Boolean {
        val modsFolder = File(context.getExternalFilesDir(null), AppConstants.MODS_FOLDER_NAME)
        Log.d("AppFileUtils", "Mods folder path: $modsFolder")
        return if (modsFolder.exists()) {
            Log.d("AppFileUtils", "Mods folder exists.")
            true
        } else {
            val success = modsFolder.mkdirs()
            Log.d("AppFileUtils", "Mods folder creation result: $success")
            success
        }
    }

    /**
     * 递归删除文件或目录
     */
    fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteRecursively(child)
            }
        }
        return file.delete()
    }

    /**
     * 获取指定 Mod 的日志文件列表
     */
    fun getLovelyLogList(modPath: String): Array<out File>? {
        val modDir = File(modPath, "lovely/log")
        return modDir.listFiles()?.reversedArray()
    }
}