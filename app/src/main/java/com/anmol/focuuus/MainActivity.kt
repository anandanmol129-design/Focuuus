package com.anmol.focuuus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.compose.ui.graphics.toArgb
import com.anmol.focuuus.ui.W
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.anmol.focuuus.ui.AppPickerScreen
import com.anmol.focuuus.ui.FocuuusTheme
import com.anmol.focuuus.ui.HomeScreen
import com.anmol.focuuus.ui.LimitEditor
import com.anmol.focuuus.ui.RoutineEditor

sealed interface Screen {
    data object Home : Screen
    data class EditRoutine(val id: String?) : Screen
    data object PickLimitApp : Screen
    data class EditLimit(val pkg: String) : Screen
}

class MainActivity : ComponentActivity() {
    private val stack = mutableStateListOf<Screen>(Screen.Home)

    /** Bumped on every resume, so permission and usage readouts refresh after a trip to Settings. */
    private var resumes by mutableIntStateOf(0)
    private var tab by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        tab = savedInstanceState?.getInt("tab") ?: 0
        val bars = SystemBarStyle.dark(W.Paper.toArgb())
        enableEdgeToEdge(bars, bars)
        setContent {
            FocuuusTheme {
                val back = { stack.removeAt(stack.lastIndex); Unit }
                BackHandler(enabled = stack.size > 1, onBack = back)
                when (val s = stack.last()) {
                    Screen.Home -> HomeScreen(tab = tab, onTab = { tab = it }, resumes = resumes, open = { stack += it })
                    is Screen.EditRoutine -> RoutineEditor(id = s.id, onDone = back)
                    Screen.PickLimitApp -> AppPickerScreen(
                        title = "Limit an app",
                        exclude = Store.limits.value.map { it.pkg }.toSet(),
                        onPick = { pkg -> stack[stack.lastIndex] = Screen.EditLimit(pkg) },
                        onBack = back,
                    )
                    is Screen.EditLimit -> LimitEditor(pkg = s.pkg, resumes = resumes, onDone = back)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }

    override fun onResume() {
        super.onResume()
        resumes++
    }
}
