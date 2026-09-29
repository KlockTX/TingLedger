package com.klocktx.moneyblocks

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klocktx.moneyblocks.core.QuickShortcuts
import com.klocktx.moneyblocks.domain.model.ThemeMode
import com.klocktx.moneyblocks.ui.MoneyBlocksRoot
import com.klocktx.moneyblocks.ui.rememberAppContainer
import com.klocktx.moneyblocks.ui.theme.MonetTheme
import com.klocktx.moneyblocks.ui.theme.MoneyBlocksTheme

class MainActivity : ComponentActivity() {

    /**
     * 快捷记账请求：(账单 id, 序号)。
     * 序号是必须的——连着点同一个快捷项时账单 id 不变，
     * 只把 id 交给 LaunchedEffect 的话 key 没变，第二次不会响应。
     */
    private var shortcutRequest by mutableStateOf(0L to 0L)
    private var shortcutSeq = 0L

    private fun takeShortcut(intent: Intent?) {
        val billId = QuickShortcuts.billIdOf(intent)
        if (billId > 0L) {
            shortcutSeq += 1
            shortcutRequest = billId to shortcutSeq
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        takeShortcut(intent)
        enableEdgeToEdge()
        setContent {
            val container = rememberAppContainer()
            val themeMode by remember(container) { container.settingsRepository.themeMode }
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            // 主题色和黑白模式是两个独立开关：一个管色相，一个管明暗
            val monet by remember(container) { container.settingsRepository.monetThemeKey }
                .collectAsStateWithLifecycle(initialValue = "")

            MoneyBlocksTheme(themeMode = themeMode, monet = MonetTheme.fromKey(monet)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MoneyBlocksRoot(container, shortcutRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeShortcut(intent)
    }
}
