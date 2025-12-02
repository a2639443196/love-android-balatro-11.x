package org.love2d.android.util

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.love2d.android.AppConstants
import org.love2d.android.room.game.GameInfo
import java.io.File
import java.util.concurrent.Executors
import java.util.zip.ZipFile

/**
 * 负责管理游戏文件的安装、重命名、目录结构和数据库同步
 */
object GameManager {

    // 线程安全的协程作用域
    private val gameManagerScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    // 用于同步操作的互斥锁
    private val gameOperationMutex = Mutex()

    // 用于数据库同步的互斥锁
    private val syncMutex = Mutex()

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
            // 使用线程安全的协程作用域
            gameManagerScope.launch {
                try {
                    val name = fileName.replace(".love", "")
                    val modsPath = createGameModsFolder(context, name)

                    val game = GameInfo(
                        id = generateGameId(name, destFile.absolutePath), // 生成唯一的字符串ID
                        name = name,
                        createTime = System.currentTimeMillis(),
                        filePath = destFile.absolutePath,
                        savePath = "",
                        modPath = modsPath,
                        lastPlayed = 0,
                        totalPlayTime = 0L,        // 初始化总游玩时长
                        sessionStartTime = 0L     // 初始化会话开始时间
                    )
                    GameDbUtil.insertGame(game)
                } catch (e: Exception) {
                    Log.e("GameManager", "Error inserting game to database", e)
                }
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
            // 使用线程安全的协程作用域和互斥锁
            gameManagerScope.launch {
                gameOperationMutex.withLock {
                    try {
                        GameDbUtil.updateGame(oldGame)
                    } catch (e: Exception) {
                        Log.e("GameManager", "Error updating game in database", e)
                    }
                }
            }
            return true
        }
        return false
    }

    /**
     * 同步游戏目录下的 .love 文件与数据库记录
     */
    fun syncGamesWithDatabase(context: Context) {
        gameManagerScope.launch {
            syncMutex.withLock {
                try {
                    val gameDir = AppConstants.GAME_FOLDER_PATH
                    if (!gameDir.exists() || !gameDir.isDirectory) return@launch

                    val dao = GameDbUtil.getGameDao()
                    val gamesInDb = dao.getAllGamesNow()
                    val filesInDir = gameDir.listFiles { file -> file.extension == "love" } ?: emptyArray()

                    // 1. 添加数据库中不存在的游戏
                    val gamesToInsert = mutableListOf<GameInfo>()
                    filesInDir.filter { file -> gamesInDb.none { it.filePath == file.absolutePath } }
                        .forEach { file ->
                            val name = file.nameWithoutExtension
                            val modsPath = createGameModsFolder(context, name)
                            val gameInfo = GameInfo(
                                id = generateGameId(name, file.absolutePath), // 生成唯一的字符串ID
                                name = name,
                                filePath = file.absolutePath,
                                savePath = "",
                                modPath = modsPath,
                                lastPlayed = System.currentTimeMillis(),
                                totalPlayTime = 0L,        // 初始化总游玩时长
                                sessionStartTime = 0L     // 初始化会话开始时间
                            )
                            gamesToInsert.add(gameInfo)
                        }

                    // 批量插入以提高性能
                    if (gamesToInsert.isNotEmpty()) {
                        gamesToInsert.forEach { game ->
                            dao.insertGame(game)
                        }
                    }

                    // 2. 删除数据库中有但文件中已不存在的游戏
                    val filePaths = filesInDir.map { it.absolutePath }.toSet()
                    val gamesToDelete = gamesInDb.filter { it.filePath !in filePaths }

                    // 批量删除
                    gamesToDelete.forEach { gameToDelete ->
                        dao.deleteGame(gameToDelete)
                    }

                    Log.d("GameManager", "Database sync completed. Added: ${gamesToInsert.size}, Deleted: ${gamesToDelete.size}")
                } catch (e: Exception) {
                    Log.e("GameManager", "Error syncing games with database", e)
                }
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
        val modsDir = File(context.getExternalFilesDir(null), "mods_${name}")
        if (!modsDir.exists()) {
            modsDir.mkdirs()
        }
        return modsDir.absolutePath
    }

    private fun renameGameModsFolder(context: Context, oldName: String, newName: String): String {
        val modsRoot = context.getExternalFilesDir(null) ?: return ""
        val oldModsDir = File(modsRoot, "mods_${oldName}")
        val newModsDir = File(modsRoot, "mods_${newName}")

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

    /**
     * 生成唯一游戏ID，支持字符串如 "va231des"
     * 优先使用游戏名称，如果冲突则添加时间戳
     */
    private fun generateGameId(gameName: String, filePath: String): String {
        // 清理游戏名称，移除特殊字符
        val cleanName = gameName.replace(Regex("[^a-zA-Z0-9_-]"), "_")

        // 如果名称为空，使用文件名
        val baseId = if (cleanName.isNotBlank()) {
            cleanName.lowercase()
        } else {
            File(filePath).nameWithoutExtension.replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase()
        }

        // 添加简短的时间戳以避免冲突
        val timestamp = (System.currentTimeMillis() / 1000) % 10000 // 取后4位
        return "${baseId}_${timestamp}"
    }
}