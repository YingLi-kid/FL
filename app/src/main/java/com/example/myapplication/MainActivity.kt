package com.example.myapplication

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.ui.HomeScreen
import com.example.myapplication.ui.SpeechGenerationViewModel
import com.example.myapplication.ui.StartupDisclaimer
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.read(newBase).wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 内容延伸到状态栏/导航栏后方，由各组件按系统内边距自行留白
        enableEdgeToEdge()
        val generation = ViewModelProvider(this)[SpeechGenerationViewModel::class.java].generation
        val language = AppLanguage.read(this)
        val changeLanguage: (AppLanguage) -> Unit = { selection ->
            if (selection != language) {
                AppLanguage.write(this, selection)
                refreshAppStrings(this)
                recreate()
            }
        }
        setContent {
            // 固定科技黑深色主题
            MyApplicationTheme {
                StartupDisclaimer(language, changeLanguage, onDecline = ::finish) {
                    HomeScreen(generation, language, changeLanguage)
                }
            }
        }
    }
}
