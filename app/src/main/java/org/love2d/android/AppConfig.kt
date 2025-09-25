package org.love2d.android

import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import org.love2d.android.util.MMKVHelper

object AppConfig {
    const val IS_FIRST_START = "is_first_start"

    const val USER_USED_THEME = "user_use_theme"
    var userUsedTheme = MMKVHelper.getInt(USER_USED_THEME, -1)

    const val IS_PIRACY_PACKAGE = "is_piracy_package"

    val userNotice = """
            🆓 免费使用声明
        
            本软件完全免费，仅供学习交流，禁止商用！
        
            ❌ 禁止行为：
            • 任何形式的收费或售卖（包括闲鱼 / 淘宝 / 拼多多等平台）
            • 二次打包后收费
            • 捆绑其他软件获利
        
            ✅ 允许行为：
            • 个人学习使用
            • 技术交流分享
        
            ⚠️ 如果你是买的，那么你被骗了，请立即举报！
        """.trimIndent()
}

object ThemeConfig {
    const val LIGHT_THEME = 999
    const val DARK_THEME = -999
    const val SYSTEM_THEME = -1

    fun getUserUsedTheme(): Int {
        return if (AppConfig.userUsedTheme == -1) {
            SYSTEM_THEME
        } else {
            AppConfig.userUsedTheme
        }
    }

    fun setTheme(value: Boolean) {
        MMKVHelper.putInt(AppConfig.USER_USED_THEME, if (value) DARK_THEME else LIGHT_THEME)
    }

    @Composable
    fun rememberUserPreferredTheme(): MutableState<Boolean> {
        val currentTheme = getUserUsedTheme()
        val int = MMKVHelper.getInt(AppConfig.USER_USED_THEME, -1)
        Log.e("HJR", "AppConfig ThemeConfig currentTheme $currentTheme mmkvVal $int")
        val userUsedTheme = when (int) {
            LIGHT_THEME -> false
            DARK_THEME -> true
            else -> isSystemInDarkTheme()
        }

        return remember { mutableStateOf(userUsedTheme) }
    }
}

object Net {
    const val BASE_URL = "https://mod-api-prod.zhki.org"
}

object CreatePackage {
    const val H_PACKAGE_MANIFEST_NAME = "hPackageManifest.json"
}

object DatabaseConfig {
    const val DATABASE_VERSION = 2
}

object SettingConfig {
    const val TOUCH_TO_MOUSE = "touch_to_mouse"

    const val SCREEN_LOCK_LEFT = "screen_lock_left"

    const val SCREEN_LOCK_RIGHT = "screen_lock_right"

    const val AUTO_INSTALL_MOD = "auto_install_mod"
}