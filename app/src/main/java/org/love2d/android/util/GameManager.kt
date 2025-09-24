package org.love2d.android.util

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.love2d.android.AppConstants
import org.love2d.android.room.game.GameInfo
import org.love2d.android.ui.compose.page.getSaveFile
import java.io.File
import java.util.zip.ZipFile

/**
 * 负责管理游戏文件的安装、重命名、目录结构和数据库同步
 */
object GameManager {

    /**
     * 从 Uri 安装一个 .love 游戏文件到应用目录
     */
    fun installGame(context: Context, sourceUri: Uri, fileName: String): Boolean {
        val destDir = AppConstants.GAME_FOLDER_PATH
        val destFile = File(destDir, "$fileName.love")
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                destFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            CoroutineScope(Dispatchers.IO).launch {
                val name = fileName.replace(".love", "")
                val modsPath = createGameModsFolder(context, name)
                val savePath = createSavePath(context, name)

                val game = GameInfo(
                    name = name,
                    createTime = System.currentTimeMillis(),
                    filePath = destFile.absolutePath,
                    savePath = savePath.absolutePath,
                    modPath = modsPath,
                    lastPlayed = 0
                )
                GameDbUtil.insertGame(game)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * 重命名游戏及其关联的文件夹和数据库记录
     */
    fun renameGame(context: Context, oldGame: GameInfo?, newName: String): Boolean {
        if (oldGame == null) return false

        val oldFile = File(AppConstants.GAME_FOLDER_PATH, "${oldGame.name}.love")
        val newFile = File(AppConstants.GAME_FOLDER_PATH, "$newName.love")

        if (!oldFile.exists() || !oldFile.isFile) {
            Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
            return false
        }
        if (newFile.exists()) {
            Toast.makeText(context, "同名文件已存在", Toast.LENGTH_SHORT).show()
            return false
        }

        val newModPath = renameGameModsFolder(context, oldGame.name, newName)
        renameSaveFolder(context, oldGame.name, newName)

        if (oldFile.renameTo(newFile)) {
            oldGame.name = newName
            oldGame.modPath = newModPath
            oldGame.filePath = newFile.absolutePath
            CoroutineScope(Dispatchers.IO).launch {
                GameDbUtil.updateGame(oldGame)
            }
            return true
        }
        return false
    }

    /**
     * 同步游戏目录下的 .love 文件与数据库记录
     */
    fun syncGamesWithDatabase(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val gameDir = AppConstants.GAME_FOLDER_PATH
            if (!gameDir.exists() || !gameDir.isDirectory) return@launch

            val dao = GameDbUtil.getGameDao()
            val gamesInDb = dao.getAllGamesNow()
            val filesInDir = gameDir.listFiles { file -> file.extension == "love" } ?: emptyArray()

            // 1. 添加数据库中不存在的游戏
            filesInDir.filter { file -> gamesInDb.none { it.filePath == file.absolutePath } }
                .forEach { file ->
                    val name = file.nameWithoutExtension
                    val modsPath = createGameModsFolder(context, name)
                    val savePath = createSavePath(context, name)
                    val gameInfo = GameInfo(
                        name = name,
                        filePath = file.absolutePath,
                        savePath = savePath.absolutePath,
                        modPath = modsPath,
                        lastPlayed = System.currentTimeMillis()
                    )
                    dao.insertGame(gameInfo)
                }

            // 2. 删除数据库中有但文件中已不存在的游戏
            val filePaths = filesInDir.map { it.absolutePath }.toSet()
            gamesInDb.filter { it.filePath !in filePaths }
                .forEach { gameToDelete ->
                    dao.deleteGame(gameToDelete)
                }
        }
    }

    /**
     * 根据游戏名检查对应的 .love 文件是否存在
     */
    fun doesGameFileExist(name: String): Boolean {
        return File(AppConstants.GAME_FOLDER_PATH, "$name.love").exists()
    }

    /**
     * 从 .love 文件内部读取指定路径的文本文件内容
     */
    fun readFileFromLove(loveFile: File, innerFilePath: String): String? {
        return try {
            ZipFile(loveFile).use { zip ->
                zip.getEntry(innerFilePath)?.let { entry ->
                    zip.getInputStream(entry).bufferedReader().readText()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 为指定游戏创建 Mods 文件夹
     */
    fun createGameModsFolder(context: Context, name: String): String {
        val modsDir = File(context.getExternalFilesDir(null), "${name}_mods")
        if (!modsDir.exists()) {
            modsDir.mkdirs()
        }
        return modsDir.absolutePath
    }

    /**
     * 为指定游戏创建存档文件夹
     */
    suspend fun createSavePath(context: Context, gameName: String): File {
        val file = getSaveFile(context, gameName)
        if (!file.exists()) {
            file.mkdirs()
        }
        return file
    }

    private fun renameGameModsFolder(context: Context, oldName: String, newName: String): String {
        val modsRoot = context.getExternalFilesDir(null) ?: return ""
        val oldModsDir = File(modsRoot, "${oldName}_mods")
        val newModsDir = File(modsRoot, "${newName}_mods")

        if (oldModsDir.exists() && oldModsDir.isDirectory) {
            if (newModsDir.exists()) {
                Log.w("GameManager", "目标 Mods 文件夹已存在，未重命名: ${newModsDir.name}")
                return newModsDir.absolutePath
            }
            return if (oldModsDir.renameTo(newModsDir)) newModsDir.absolutePath else ""
        }
        Log.w("GameManager", "旧 Mods 文件夹不存在: ${oldModsDir.name}")
        return ""
    }

    private fun renameSaveFolder(context: Context, oldName: String, newName: String) {
        val oldSaveDir = File(context.getExternalFilesDir(null), "save/love/$oldName-LOVE")
        val newSaveDir = File(context.getExternalFilesDir(null), "save/love/$newName-LOVE")

        if (oldSaveDir.exists() && oldSaveDir.isDirectory) {
            if (newSaveDir.exists()) {
                Log.w("GameManager", "目标存档文件夹已存在，未重命名: ${newSaveDir.name}")
                return
            }
            if (!oldSaveDir.renameTo(newSaveDir)) {
                Log.w("GameManager", "存档文件夹重命名失败: $oldName -> $newName")
            }
        } else {
            Log.w("GameManager", "旧存档文件夹不存在: ${oldSaveDir.name}")
        }
    }
}