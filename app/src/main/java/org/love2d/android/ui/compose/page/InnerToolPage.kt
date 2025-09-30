package org.love2d.android.ui.compose.page

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.love2d.android.room.mod.ModInfo
import org.love2d.android.ui.activity.GameManagerViewModel
import org.love2d.android.ui.activity.Screen
import org.love2d.android.ui.compose.EmptyLottie
import java.io.File
import java.net.URLEncoder

/**
 * ClassName InnerToolPage
 * Description
 * Create by hjr
 * Date 2025/9/30 13:39
 */
@Composable
fun InnerToolPage(
    navController: NavController,
    localActivity: Activity,
    modPath: String,
    viewModel: GameManagerViewModel, // 假设的 ViewModel
) {
    val modsFlow = remember(modPath) { viewModel.getPathMods(modPath) }
    val modList by modsFlow.collectAsState(initial = emptyList())

    Box(modifier = Modifier.fillMaxSize()) {
        if (modList.isEmpty()) {
            // --- 空状态 ---
            Box(
                modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                // EmptyLottie 是您自定义的组件，这里保持原样
                EmptyLottie(emptyText = "还没有东西呢～")
            }
        } else {
            // --- 列表状态 ---
            LazyColumn(
                modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(
                    top = 10.dp,
                    start = 10.dp,
                    end = 10.dp,
                    bottom = 125.dp
                ), verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(modList.size) { index ->
                    InnerToolCard(
                        modInfo = modList[index], onConfirm = { mod ->
                            val fileName = if (mod.isLocal) {
                                mod.name
                            } else {
                                mod.resultName
                            }
                            val modFolder = File(mod.installPath, fileName)
                            //寻找assets
                            val assetsFolder = File(modFolder, "assets")
                            //寻找shaders
                            val shadersFolder = File(assetsFolder, "shaders")
                            if (shadersFolder.exists() && shadersFolder.isDirectory) {
                                val encodedPath = URLEncoder.encode(shadersFolder.absolutePath, "UTF-8")
                                navController.navigate(Screen.SHADERS_FIXER.name + "/$encodedPath")
                            } else {
                                Toast.makeText(localActivity, "未找到着色器文件", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun InnerToolCard(
    modInfo: ModInfo,
    onConfirm: (ModInfo) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically // 👈 关键：设置为垂直居中对齐
        ) {
            // 左侧：仅有标题
            Text(
                text = modInfo.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f) // 👈 关键：让标题占据所有可用空间，将按钮推到最右侧
                    .padding(end = 16.dp) // 在标题和按钮间留出一些间距
                    .basicMarquee(repeatDelayMillis = 1, iterations = Int.MAX_VALUE)
            )
        }

        // 2. 作者信息现在位于上方 Row 的下方
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "作者: ${modInfo.author}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // 为了对齐，可以给作者信息也加上一点左内边距，使其与标题对齐
            // 如果您的 MaterialTheme 版本较新，标题默认可能没有内边距，这一行则非必须
            // modifier = Modifier.padding(start = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))

        // 底部按钮区，从右往左排布
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            // 右侧优先的按钮
            Button(
                onClick = {
                    onConfirm(modInfo)
                }
            ) {
                Text("着色器修复")
            }
        }
    }
}