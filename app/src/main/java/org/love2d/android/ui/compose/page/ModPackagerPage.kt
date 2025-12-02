package org.love2d.android.ui.compose.page

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.love2d.android.ui.activity.GameManagerViewModel
import org.love2d.android.room.mod.ModInfo
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModPackagerPage(
    viewModel: GameManagerViewModel,
    navController: NavController,
    localActivity: Activity
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 获取当前游戏信息
    val currentGame = viewModel.currentGame.collectAsState().value

    // 状态管理
    var selectedMods by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSelectAll by remember { mutableStateOf(false) }
    var packName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var availableMods by remember { mutableStateOf<List<ModInfo>>(emptyList()) }

    // 文件选择器启动器（用于导入mod）
    val importModLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris?.let { selectedUris ->
            scope.launch {
                // 处理导入的mod文件
                Log.d("ModPackager", "选择了 ${selectedUris.size} 个mod文件")
                // TODO: 处理mod导入逻辑
            }
        }
    }

    // 加载可用的mod列表
    LaunchedEffect(currentGame?.modPath) {
        currentGame?.modPath?.let { modPath ->
            scope.launch {
                try {
                    viewModel.getPathMods(modPath).collect { mods ->
                        availableMods = mods
                    }
                } catch (e: Exception) {
                    Log.e("ModPackager", "加载mod列表失败: ${e.message}", e)
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("创建整合包") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { innerPadding ->
            if (availableMods.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "暂无可用的模组",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {
                                importModLauncher.launch("application/zip")
                            }
                        ) {
                            Text("导入模组")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 整合包名称输入
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "整合包名称",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = packName,
                                    onValueChange = { packName = it },
                                    label = { Text("输入整合包名称") },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("例如: 我的最爱模组包") }
                                )
                            }
                        }
                    }

                    // 全选控制
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isSelectAll = !isSelectAll
                                        selectedMods = if (isSelectAll) {
                                            emptySet()
                                        } else {
                                            availableMods.map { it.resultName }.toSet()
                                        }
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "全选/取消全选",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )

                                Checkbox(
                                    checked = isSelectAll && selectedMods.size == availableMods.size,
                                    onCheckedChange = { checked ->
                                        isSelectAll = checked
                                        selectedMods = if (checked) {
                                            availableMods.map { it.resultName }.toSet()
                                        } else {
                                            emptySet()
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // 已选择的模组数量
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "已选择 ${selectedMods.size} 个模组",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // 导入更多模组
                    item {
                        Button(
                            onClick = {
                                importModLauncher.launch("application/zip")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("导入更多模组")
                        }
                    }

                    // 模组列表
                    items(availableMods) { mod ->
                        val isSelected = selectedMods.contains(mod.resultName)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedMods = if (isSelected) {
                                        selectedMods - mod.resultName
                                    } else {
                                        selectedMods + mod.resultName
                                    }
                                    // 更新全选状态
                                    isSelectAll = selectedMods.size == availableMods.size
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedMods = if (checked) {
                                            selectedMods + mod.resultName
                                        } else {
                                            selectedMods - mod.resultName
                                        }
                                        // 更新全选状态
                                        isSelectAll = selectedMods.size == availableMods.size
                                    }
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = mod.name ?: mod.resultName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Text(
                                        text = "版本: ${mod.version ?: "未知"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (mod.description.isNotEmpty()) {
                                        Text(
                                            text = mod.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 创建按钮
                    item {
                        Button(
                            onClick = {
                                if (packName.isBlank()) {
                                    // TODO: 显示错误提示
                                    return@Button
                                }

                                scope.launch {
                                    createModPack(
                                        context,
                                        packName,
                                        availableMods.filter { selectedMods.contains(it.resultName) },
                                        selectedMods.toList()
                                    ) { success, message ->
                                        isCreating = false
                                        if (success) {
                                            // TODO: 显示成功提示或跳转到分享页面
                                            Log.d("ModPackager", "整合包创建成功: $message")
                                        } else {
                                            Log.e("ModPackager", "整合包创建失败: $message")
                                        }
                                    }
                                }
                            },
                            enabled = !isCreating && packName.isNotBlank() && selectedMods.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (isCreating) "创建中..." else "创建整合包")
                        }
                    }
                }
            }
        }
    }
}

// 创建整合包
private suspend fun createModPack(
    context: android.content.Context,
    packName: String,
    mods: List<ModInfo>,
    selectedModNames: List<String>,
    onResult: (Boolean, String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            // 创建临时文件夹
            val tempDir = File(context.cacheDir, "mod_pack_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            // 为每个选中的mod创建文件夹
            val packDir = File(tempDir, packName)
            packDir.mkdirs()

            // 复制mod文件到整合包文件夹
            mods.forEach { mod ->
                if (selectedModNames.contains(mod.resultName)) {
                    // TODO: 复制mod文件逻辑
                    // 这里需要根据实际的mod存储结构来复制文件
                    Log.d("ModPackager", "复制mod: ${mod.name}")
                }
            }

            // 创建ZIP文件
            val zipFile = File(context.cacheDir, "${packName}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.zip")
            createZipFromDirectory(packDir, zipFile)

            // 分享ZIP文件
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                zipFile
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "模组整合包: $packName")
                putExtra(Intent.EXTRA_TEXT, "分享模组整合包: $packName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "分享整合包"))

            // 清理临时文件
            tempDir.deleteRecursively()

            onResult(true, "整合包创建并分享成功")

        } catch (e: Exception) {
            Log.e("ModPackager", "创建整合包失败: ${e.message}", e)
            onResult(false, "创建失败: ${e.message}")
        }
    }
}

// 递归删除目录
private fun File.deleteRecursively(): Boolean {
    return try {
        if (this.isDirectory) {
            this.listFiles()?.forEach { it.deleteRecursively() }
        }
        this.delete()
    } catch (e: Exception) {
        false
    }
}

// 从目录创建ZIP文件
private fun createZipFromDirectory(sourceDir: File, zipFile: File) {
    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipOut ->
        sourceDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val entry = ZipEntry(file.relativeTo(sourceDir).path)
                entry.time = file.lastModified()
                zipOut.putNextEntry(entry)

                file.inputStream().use { input ->
                    input.copyTo(zipOut)
                }
                zipOut.closeEntry()
            }
        }
    }
}