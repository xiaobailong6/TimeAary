package local.daybook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import local.daybook.data.EntryDatabase
import local.daybook.data.LocalEntryRepository
import local.daybook.data.SharedPreferencesSettingsStore
import local.daybook.ui.AppScreen
import local.daybook.ui.AppTheme
import local.daybook.ui.AppViewModel

class AppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // ViewModel 存活期间持有应用 Context 创建的数据库，不持有 Activity。
            val factory = remember {
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                        require(modelClass == AppViewModel::class.java)
                        val db = Room.databaseBuilder(applicationContext, EntryDatabase::class.java, "entries.db")
                            .addMigrations(EntryDatabase.MIGRATION_1_2, EntryDatabase.MIGRATION_2_3)
                            .build()
                        return AppViewModel(LocalEntryRepository(db), extras.createSavedStateHandle(),
                            settingsStore = SharedPreferencesSettingsStore(applicationContext)) as T
                    }
                }
            }
            val state: AppViewModel = viewModel(factory = factory)
            // 主题模式与字号立即生效：设置变化后整棵 Compose 树按新参数重组。
            val settings by state.settings.collectAsStateWithLifecycle()
            AppTheme(settings) { AppScreen(state) }
        }
    }
}
