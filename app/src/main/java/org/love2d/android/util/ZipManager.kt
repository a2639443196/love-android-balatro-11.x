package org.love2d.android.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import org.love2d.android.room.mod.ModInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 封装所有与 ZIP 文件相关的操作，使用 Zip4j 库实现。
 * 功能包括压缩、解压和 Mod 安装。
 */
object ZipManager {

    /**
     * 从 Uri 安装一个 Mod。
     * 由于 zip4j 需要文件句柄，我们会先将 Uri 内容复制到缓存文件中再处理。
     */
    fun installModFromUri(context: Context, modPath: String, zipUri: Uri) {
        val fileNameWithExt = UriUtil.getFileNameFromUri(context, zipUri) ?: "temp.zip"

        // 创建一个临时文件来存放 Uri 的内容
        val tempZipFile = File(context.cacheDir, fileNameWithExt)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 将 Uri 的内容复制到临时文件
                context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                    FileOutputStream(tempZipFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // 2. 调用基于 File 的安装方法
                val resultName = installModFromFile(context, modPath, tempZipFile, isUpdate = false)

                // 3. 安装成功后插入数据库
                if (resultName.isNotBlank()) {
                    val modInfo = ModInfo().apply {
                        isLocal = true
                        name = resultName
                        from = fileNameWithExt
                        installPath = modPath
                    }
                    ModDbUtil.insertMod(modInfo)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Mod 安装失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                // 4. 删除临时文件
                tempZipFile.delete()
            }
        }
    }

    /**
     * 从 Assets 目录安装一个 Mod。
     * 同样，先复制到缓存文件再处理。
     */
    fun installModFromAssets(context: Context, modPath: String, assetZipName: String) {
        val tempZipFile = File(context.cacheDir, assetZipName)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 复制 Assets 文件
                context.assets.open(assetZipName).use { inputStream ->
                    FileOutputStream(tempZipFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // 2. 调用安装
                val resultName = installModFromFile(context, modPath, tempZipFile, isUpdate = false)

                // 3. 插入数据库
                if (resultName.isNotBlank()) {
                    val modInfo = ModInfo().apply {
                        isLocal = true
                        name = resultName
                        from = assetZipName
                        installPath = modPath
                    }
                    ModDbUtil.insertMod(modInfo)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                tempZipFile.delete()
            }
        }
    }

    /**
     * 从 File 安装一个 Mod，不插入数据库记录。这是核心实现。
     * @return 返回解压后的根文件夹名，如果操作被跳过或失败，则返回空字符串。
     */
    suspend fun installModFromFile(
        context: Context,
        modPath: String,
        zipFile: File,
        isUpdate: Boolean = false
    ): String {
        if (!zipFile.exists()) return ""

        val zip = ZipFile(zipFile)
        val modDir = File(modPath).apply { mkdirs() }
        val fileNameFromUri = zipFile.nameWithoutExtension

        val zipStructure = analyzeZipStructure(zip)
        val needTopFolder = zipStructure.needsNewRootFolder
        var resultName = if (needTopFolder) fileNameFromUri else zipStructure.singleRootFolderName ?: fileNameFromUri

        val targetFolder = File(modDir, resultName)

        // 更新操作：先删除旧文件夹
        if (isUpdate) {
            if (targetFolder.exists()) {
                Log.d("ZipManager", "更新操作：删除旧文件夹 ${targetFolder.absolutePath}")
                AppFileUtils.deleteRecursively(targetFolder)
            }
        } else {
            // 非更新操作：检查冲突
            if (targetFolder.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "已存在同名文件夹：${targetFolder.name}，跳过解压", Toast.LENGTH_SHORT).show()
                }
                return "" // 操作被跳过
            }
        }

        val targetDir = if (needTopFolder) {
            targetFolder.mkdirs()
            targetFolder
        } else {
            modDir
        }

        // 使用 zip4j 解压
        try {
            Log.d("ZipManager", "开始解压 ${zipFile.name} 到 ${targetDir.absolutePath}")
            zip.extractAll(targetDir.absolutePath)
            Log.d("ZipManager", "解压成功")
            return resultName
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "解压失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            // 如果解压失败，清理可能已创建的文件夹
            if (targetDir.exists()) {
                AppFileUtils.deleteRecursively(targetDir)
            }
            return "" // 解压失败
        }
    }

    /**
     * 将 Base64 字符串解码为 ZIP 文件并解压到特定目录。
     */
    fun decodeBase64AndUnzip(context: Context, base64: String) {
        val outputZip = File(context.cacheDir, "wifiSave-LOVE.zip")
        try {
            // 1. 解码并写入临时 ZIP 文件
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            outputZip.writeBytes(bytes)
            Log.d("ZipManager", "Base64 解码并写入到: ${outputZip.absolutePath}")

            // 2. 准备目标目录
            val outputDir = File(context.getExternalFilesDir(null), "save/love/wifi-save-LOVE")
            if (outputDir.exists()) {
                AppFileUtils.deleteRecursively(outputDir)
            }
            outputDir.mkdirs()

            // 3. 使用 zip4j 解压
            ZipFile(outputZip).extractAll(outputDir.absolutePath)
            Log.d("ZipManager", "成功解压到: ${outputDir.absolutePath}")

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ZipManager", "解码或解压 Base64 失败: ${e.message}")
        } finally {
            outputZip.delete() // 清理临时文件
        }
    }

    /**
     * 将指定目录压缩成 ZIP 文件。
     */
    fun zipDirectory(sourceDir: File, outputZip: File) {
        try {
            if (!sourceDir.exists() || !sourceDir.isDirectory) {
                Log.e("ZipManager", "源文件夹不存在或不是一个目录: ${sourceDir.absolutePath}")
                return
            }
            Log.d("ZipManager", "开始压缩目录 ${sourceDir.name} 到 ${outputZip.absolutePath}")
            ZipFile(outputZip).addFolder(sourceDir)
            Log.d("ZipManager", "压缩成功")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("ZipManager", "压缩失败: ${e.message}")
        }
    }

    // --- 私有辅助方法 ---

    private data class ZipStructureInfo(
        val needsNewRootFolder: Boolean,
        val singleRootFolderName: String?
    )

    /**
     * 分析 ZIP 文件结构，判断解压时是否需要创建新的根目录。
     * @return ZipStructureInfo 包含判断结果和单根目录的名称（如果存在）。
     */
    private fun analyzeZipStructure(zipFile: ZipFile): ZipStructureInfo {
        val fileHeaders: List<FileHeader> = zipFile.fileHeaders
        if (fileHeaders.isEmpty()) {
            return ZipStructureInfo(true, null) // 空zip，创建一个新目录
        }

        // 获取所有顶级条目（文件或文件夹）
        val topLevelEntries = fileHeaders.map {
            it.fileName.substringBefore('/')
        }.distinct()

        return if (topLevelEntries.size > 1) {
            // 根级别有多个文件/文件夹，需要创建新目录
            ZipStructureInfo(true, null)
        } else {
            val singleEntryName = topLevelEntries.first()
            // 检查这唯一的顶级条目是否是文件夹
            val isSingleFolder = fileHeaders.all { it.fileName.startsWith("$singleEntryName/") }
            if (isSingleFolder) {
                // 只有一个顶级文件夹，不需要创建新目录
                ZipStructureInfo(false, singleEntryName)
            } else {
                // 根级别只有一个文件，需要创建新目录
                ZipStructureInfo(true, null)
            }
        }
    }
}