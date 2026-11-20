package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.myapplication.ui.HomeScreen
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 内容延伸到状态栏/导航栏后方，由各组件按系统内边距自行留白
        enableEdgeToEdge()
        setContent {
            // 固定科技黑深色主题
            MyApplicationTheme {
                HomeScreen()
            }
        }
    }
}
