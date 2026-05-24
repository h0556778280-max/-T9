package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.SoundEffectConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

// Input modes representation
enum class KeyboardMode(val displayHebrew: String, val displayEnglish: String) {
    HEBREW("עברית (א ב ג)", "Hebrew"),
    ENGLISH("אנגלית (abc)", "English"),
    NUMBERS("מספרים (123)", "Numbers")
}

// Data class representing saved note
data class SavedNote(
    val id: String,
    val text: String,
    val timestamp: Long
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    T9KeyboardAppScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun T9KeyboardAppScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current

    // State Declarations
    var textBuffer by remember { mutableStateOf("") }
    
    // Multi-tap state
    var activeKey by remember { mutableStateOf<Int?>(null) } // null means no active character compiling
    var activeCharIndex by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var timeoutMs by remember { mutableLongStateOf(1000L) } // adjustable multi-tap timeout

    var activeMode by remember { mutableStateOf(KeyboardMode.HEBREW) }
    
    // Copy state feedback
    var showCopySuccess by remember { mutableStateOf(false) }
    
    // Help & Settings Modal Toggles
    var showInfoDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Saved Notes List State
    val sharedPrefs = remember { context.getSharedPreferences("T9KeyboardPrefs", Context.MODE_PRIVATE) }
    var savedNotesList by remember { mutableStateOf(loadSavedNotes(sharedPrefs)) }

    // Popular Emojis library
    val emojis = listOf(
        "😀", "😂", "😍", "👍", "🎉", "❤️", "🔥", "👏", "🌟", "💡", 
        "📱", "⏳", "😊", "😭", "🤔", "😉", "🇮🇱", "✨", "🙌", "🎂", 
        "🌹", "☀️", "☕", "🍕", "🚗", "🏃", "🎧", "💪", "🌈", "🎈"
    )

    // Popular nostalgic rapid greetings in Hebrew
    val quickPhrases = listOf(
        "מה קורה?", "איפה אתה?", "אני בדרך", "חג שמח!", "שבת שלום! ❤️", 
        "בוקר טוב ☀️", "לילה טוב", "מזל טוב!! 🎉", "תודה רבה!", "נדבר יותר מאוחר"
    )

    // Star Cycle list
    val starSymbols = listOf("*", "#", "+", "=", "-", "%", "$", "&", "@", "!", "?", "(", ")", "[", "]", "{", "}")

    // Character mapping function
    fun getCharsForKey(key: Int, mode: KeyboardMode): List<Char> {
        return when (mode) {
            KeyboardMode.HEBREW -> {
                when (key) {
                    1 -> listOf('.', ',', '?', '!', '@', '-', '_', '1')
                    2 -> listOf('א', 'ב', 'ג', '2')
                    3 -> listOf('ד', 'ה', 'ו', '3')
                    4 -> listOf('ז', 'ח', 'ט', '4')
                    5 -> listOf('י', 'כ', 'ך', 'ל', '5')
                    6 -> listOf('מ', 'ם', 'נ', 'ן', 'ס', '6')
                    7 -> listOf('ע', 'פ', 'ף', 'צ', 'ץ', '7')
                    8 -> listOf('ק', 'ר', 'ש', '8')
                    9 -> listOf('ת', '9')
                    else -> emptyList()
                }
            }
            KeyboardMode.ENGLISH -> {
                when (key) {
                    1 -> listOf('.', ',', '?', '!', '@', '-', '_', '1')
                    2 -> listOf('a', 'b', 'c', '2')
                    3 -> listOf('d', 'e', 'f', '3')
                    4 -> listOf('g', 'h', 'i', '4')
                    5 -> listOf('j', 'k', 'l', '5')
                    6 -> listOf('m', 'n', 'o', '6')
                    7 -> listOf('p', 'q', 'r', 's', '7')
                    8 -> listOf('t', 'u', 'v', '8')
                    9 -> listOf('w', 'x', 'y', 'z', '9')
                    else -> emptyList()
                }
            }
            KeyboardMode.NUMBERS -> {
                listOf(key.toString()[0])
            }
        }
    }

    // List of chars currently cycled
    val activeKeyList = remember(activeKey, activeMode) {
        when {
            activeKey == null -> emptyList()
            activeKey == -1 -> starSymbols.map { it[0] } // Star cycling Mode
            else -> getCharsForKey(activeKey!!, activeMode)
        }
    }

    // Compute active character string
    val activeCharStr = remember(activeKey, activeCharIndex, activeKeyList) {
        if (activeKey != null && activeKeyList.isNotEmpty()) {
            activeKeyList[activeCharIndex % activeKeyList.size].toString()
        } else ""
    }

    // Text to display in phone screen
    val currentFullText = textBuffer + activeCharStr

    // Commit current active character helper
    fun commitActiveChar() {
        if (activeKey != null && activeCharStr.isNotEmpty()) {
            textBuffer += activeCharStr
            activeKey = null
            activeCharIndex = 0
        }
    }

    // Clock count-down ticking effect
    var progressFraction by remember { mutableStateOf(0f) }
    var timeRemainingFormatted by remember { mutableStateOf("") }

    LaunchedEffect(activeKey, lastTapTime, timeoutMs) {
        if (activeKey != null) {
            while (true) {
                val now = System.currentTimeMillis()
                val elapsed = now - lastTapTime
                val remaining = timeoutMs - elapsed
                if (remaining <= 0) {
                    commitActiveChar()
                    progressFraction = 0f
                    timeRemainingFormatted = ""
                    break
                }
                progressFraction = (remaining.toFloat() / timeoutMs).coerceIn(0f, 1f)
                timeRemainingFormatted = String.format("%.1fs", remaining / 1000f)
                delay(16) // ~60fps smooth progression updates
            }
        } else {
            progressFraction = 0f
            timeRemainingFormatted = ""
        }
    }

    // Key tapping core logic
    fun onKeyPress(key: Int) {
        view.playSoundEffect(SoundEffectConstants.CLICK)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val now = System.currentTimeMillis()

        // Pressed the same key within interval
        if (activeKey == key && now - lastTapTime < timeoutMs) {
            activeCharIndex += 1
            lastTapTime = now
        } else {
            // Commit previous key character if existed
            commitActiveChar()
            // Start taping on new key
            activeKey = key
            activeCharIndex = 0
            lastTapTime = now
        }
    }

    // Toggle Keyboard Input Mode
    fun cycleKeyboardMode() {
        view.playSoundEffect(SoundEffectConstants.CLICK)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        commitActiveChar()
        activeMode = when (activeMode) {
            KeyboardMode.HEBREW -> KeyboardMode.ENGLISH
            KeyboardMode.ENGLISH -> KeyboardMode.NUMBERS
            KeyboardMode.NUMBERS -> KeyboardMode.HEBREW
        }
    }

    // Delete character logic
    fun onDelete() {
        view.playSoundEffect(SoundEffectConstants.CLICK)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (activeKey != null) {
            // Cancel active temporary composing character
            activeKey = null
            activeCharIndex = 0
        } else if (textBuffer.isNotEmpty()) {
            textBuffer = textBuffer.substring(0, textBuffer.length - 1)
        }
    }

    // Reset everything
    fun clearWorkspace() {
        commitActiveChar()
        textBuffer = ""
        activeKey = null
        activeCharIndex = 0
    }

    // Enter press
    fun onEnterPress() {
        view.playSoundEffect(SoundEffectConstants.CLICK)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        commitActiveChar()
        textBuffer += "\n"
    }

    Column(
        modifier = modifier
            .background(Color(0xFF0F172A)) // dark obsidian depth
            .fillMaxSize()
    ) {
        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = "Device Logo",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "נוסטלגיית מקשים",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "מקלדת מקשים T9 חכמה",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Info Button
                IconButton(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier.testTag("info_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Help Guide",
                        tint = Color(0xFF38BDF8)
                    )
                }
            }
        }

        // Adaptive main viewport splits content cleanly
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val isTabletLayout = maxWidth >= 600.dp
            
            if (isTabletLayout) {
                // Side-by-Side canonical Material 3 panel
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Column: LCD screen + tools
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PhoneLcdScreen(
                            textToDisplay = currentFullText,
                            activeCharStr = activeCharStr,
                            activeMode = activeMode,
                            progressFraction = progressFraction,
                            timeRemainingFormatted = timeRemainingFormatted,
                            onClearRequested = { showClearConfirm = true },
                            onCopyRequested = {
                                commitActiveChar()
                                clipboard.setText(AnnotatedString(textBuffer))
                                showCopySuccess = true
                            },
                            onShareRequested = {
                                commitActiveChar()
                                if (textBuffer.isNotEmpty()) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, textBuffer)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "שתף טקסט"))
                                }
                            },
                            onSaveRequested = {
                                commitActiveChar()
                                if (textBuffer.isNotBlank()) {
                                    savedNotesList = saveNote(sharedPrefs, textBuffer, savedNotesList)
                                    textBuffer = ""
                                }
                            }
                        )

                        SettingsPanel(
                            timeoutMs = timeoutMs,
                            onTimeoutChange = { timeoutMs = it }
                        )

                        EmojiAndPhrasesDeck(
                            emojis = emojis,
                            quickPhrases = quickPhrases,
                            onEmojiSelected = { emoji ->
                                commitActiveChar()
                                textBuffer += emoji
                                view.playSoundEffect(SoundEffectConstants.CLICK)
                            },
                            onPhraseSelected = { phrase ->
                                commitActiveChar()
                                textBuffer += phrase
                                view.playSoundEffect(SoundEffectConstants.CLICK)
                            }
                        )
                    }

                    // Right Column: Tactile physical simulated keyboard + Saved Notes
                    Column(
                        modifier = Modifier
                            .weight(0.9f)
                            .background(Color(0xFF1E293B))
                            .fillMaxHeight()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        T9NumericKeypad(
                            activeMode = activeMode,
                            onKeyPress = ::onKeyPress,
                            onStarPress = {
                                view.playSoundEffect(SoundEffectConstants.CLICK)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (activeKey == -1) {
                                    activeCharIndex += 1
                                    lastTapTime = System.currentTimeMillis()
                                } else {
                                    commitActiveChar()
                                    activeKey = -1
                                    activeCharIndex = 0
                                    lastTapTime = System.currentTimeMillis()
                                }
                            },
                            onHashPress = ::cycleKeyboardMode,
                            onBackspace = ::onDelete,
                            onEnter = ::onEnterPress,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        SavedNotesArchive(
                            notes = savedNotesList,
                            onLoadNote = { note ->
                                commitActiveChar()
                                textBuffer = note.text
                                view.playSoundEffect(SoundEffectConstants.CLICK)
                            },
                            onDeleteNote = { note ->
                                savedNotesList = deleteNote(sharedPrefs, note.id, savedNotesList)
                                view.playSoundEffect(SoundEffectConstants.CLICK)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // Mobile-First Scrollable Vertical Stack Layout
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        PhoneLcdScreen(
                            textToDisplay = currentFullText,
                            activeCharStr = activeCharStr,
                            activeMode = activeMode,
                            progressFraction = progressFraction,
                            timeRemainingFormatted = timeRemainingFormatted,
                            onClearRequested = { showClearConfirm = true },
                            onCopyRequested = {
                                commitActiveChar()
                                clipboard.setText(AnnotatedString(textBuffer))
                                showCopySuccess = true
                            },
                            onShareRequested = {
                                commitActiveChar()
                                if (textBuffer.isNotEmpty()) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, textBuffer)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "שתף טקסט"))
                                }
                            },
                            onSaveRequested = {
                                commitActiveChar()
                                if (textBuffer.isNotBlank()) {
                                    savedNotesList = saveNote(sharedPrefs, textBuffer, savedNotesList)
                                    textBuffer = ""
                                }
                            }
                        )
                    }

                    item {
                        SettingsPanel(
                            timeoutMs = timeoutMs,
                            onTimeoutChange = { timeoutMs = it }
                        )
                    }

                    item {
                        EmojiAndPhrasesDeck(
                            emojis = emojis,
                            quickPhrases = quickPhrases,
                            onEmojiSelected = { emoji ->
                                commitActiveChar()
                                textBuffer += emoji
                                view.playSoundEffect(SoundEffectConstants.CLICK)
                            },
                            onPhraseSelected = { phrase ->
                                commitActiveChar()
                                textBuffer += phrase
                                view.playSoundEffect(SoundEffectConstants.CLICK)
                            }
                        )
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF1E293B),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            T9NumericKeypad(
                                activeMode = activeMode,
                                onKeyPress = ::onKeyPress,
                                onStarPress = {
                                    view.playSoundEffect(SoundEffectConstants.CLICK)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (activeKey == -1) {
                                        activeCharIndex += 1
                                        lastTapTime = System.currentTimeMillis()
                                    } else {
                                        commitActiveChar()
                                        activeKey = -1
                                        activeCharIndex = 0
                                        lastTapTime = System.currentTimeMillis()
                                    }
                                },
                                onHashPress = ::cycleKeyboardMode,
                                onBackspace = ::onDelete,
                                onEnter = ::onEnterPress,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    item {
                        SavedNotesArchive(
                            notes = savedNotesList,
                            onLoadNote = { note ->
                                commitActiveChar()
                                textBuffer = note.text
                                view.playSoundEffect(SoundEffectConstants.CLICK)
                            },
                            onDeleteNote = { note ->
                                savedNotesList = deleteNote(sharedPrefs, note.id, savedNotesList)
                                view.playSoundEffect(SoundEffectConstants.CLICK)
                            },
                            modifier = Modifier.heightIn(max = 280.dp)
                        )
                    }
                }
            }
        }

        // Visual Snackbar Notification on copy
        AnimatedVisibility(
            visible = showCopySuccess,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            Snackbar(
                action = {
                    TextButton(onClick = { showCopySuccess = false }) {
                        Text("הבנתי", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF1E293B),
                contentColor = Color.White,
                modifier = Modifier.padding(12.dp)
            ) {
                Text("הטקסט הועתק ללוח בהצלחה! 📋", textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
            }
            LaunchedEffect(showCopySuccess) {
                if (showCopySuccess) {
                    delay(2500)
                    showCopySuccess = false
                }
            }
        }
    }

    // Confirmation dialog for clearing workspace
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("מחיקת טקסט", fontWeight = FontWeight.Bold, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
            text = { Text("האם אתה בטוח שברצונך למחוק את כל הטקסט שרשמת?", textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(
                    onClick = {
                        clearWorkspace()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("מחק הכל", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("ביטול", color = Color.Gray)
                }
            }
        )
    }

    // Instruction Manual Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text(
                    text = "מדריך שימוש - מקלדת T9",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "ברוך הבא למדמה מקלדת המקשים הקלאסית (T9) המשודרגת! הנה דרך הפעולה:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Divider()
                    }
                    item {
                        Text(
                            text = "⌨️ הקלדה רב-לחיצתית (Multi-tap):",
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "כל מקש מספר מכיל מספר אותיות. לחץ ברצף על אותו המקש כדי לדפדף בין האותיות שלו. למשל במצב עברית, לחיצה אחת על 2 תקליד 'א', שתי לחיצות מהירות תקליד 'ב', ושלוש לחיצות תקליד 'ג'.",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Text(
                            text = "⏳ טיימר חכם ודינמי:",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "מד ההתקדמות הירוק מציג כמה זמן נותר לסיום נעילת האות. אם תמתין עד לסיום המד (או תלחץ על מקש אחר או על 'רווח'), האות הנבחרת תינעל והלחיצה הבאה תתחיל אות חדשה! באפשרותך להתאים את מהירות הטיימר באמצעות סליידר קצב הלחיצה.",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Text(
                            text = "🌐 החלפת שפה ומספרים (#):",
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "לחיצה על סולמית (#) מחליפה באופן מיידי בין המצבים: עברית (א ב ג), אנגלית (abc), ומצב ספרות ישיר (123).",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Text(
                            text = "✨ סימנים מיוחדים (*):",
                            color = Color(0xFFEC4899),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "לחיצות חוזרות על מקש הכוכבית (*) מדפדפות ויוצרות סימנים מיוחדים (כמו +, -, = ועוד) עד שנגמרים הסימנים ואז המקלדת חוזרת למצב ההקלדה הרגיל.",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Text(
                            text = "🥰 תמיכה באימוג'ים וביטויים מהירים ונוסטלגיים:",
                            color = Color(0xFFA855F7),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "שלב אימוג'ים בלחיצה אחת מתוך לוח האימוג'ים העשיר, או השתמש בקומפוזיציות קצרות וברכות נוסטלגיות מוכנות מראש כמו 'מה קורה?', 'שבת שלום' ועוד.",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Text(
                            text = "💾 ארכיון הודעות ומכתבים:",
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "תוכל לשמור הודעות שכתבת על מנת לקרוא ולהעתיק אותן מחדש בעתיד, מה שמתפקד כיומן נוסטלגי אישי מושלם!",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                ) {
                    Text("הבנתי, בוא נתחיל!", color = Color.White)
                }
            }
        )
    }
}

// Phone Retro glass LCD screen
@Composable
fun PhoneLcdScreen(
    textToDisplay: String,
    activeCharStr: String,
    activeMode: KeyboardMode,
    progressFraction: Float,
    timeRemainingFormatted: String,
    onClearRequested: () -> Unit,
    onCopyRequested: () -> Unit,
    onShareRequested: () -> Unit,
    onSaveRequested: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(2.dp, Color(0xFF475569))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // LCD Upper display status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("רשת פעילה T9", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }

                // Mode Indicator Badge
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF2563EB).copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Color(0xFF2563EB))
                ) {
                    Text(
                        text = "מצב: ${activeMode.displayHebrew}",
                        color = Color(0xFF60A5FA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
            }

            // Glass screen content window
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF121C18), Color(0xFF1B2D26)) // Glowing emerald matrix
                        )
                    )
                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                if (textToDisplay.isEmpty()) {
                    // Empty Screen Tip state
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Keyboard,
                            contentDescription = "No text",
                            tint = Color(0xFF10B981).copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "התחל להקליד במקשים למטה...",
                            color = Color(0xFF10B981).copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Scrollable green console screen text display
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = false
                    ) {
                        item {
                            Text(
                                text = textToDisplay,
                                style = TextStyle(
                                    color = Color(0xFF34D399),
                                    fontSize = 20.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 28.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Right
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Multi-tap Timer and typing countdown line
            if (activeCharStr.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ממתין לנעילה...",
                                color = Color(0xFFF59E0B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "($timeRemainingFormatted)",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = "מקליד כעת: '$activeCharStr'",
                            color = Color(0xFFF59E0B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // Countdown Progress bar
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        color = Color(0xFF10B981),
                        trackColor = Color(0xFF334155),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                // Idle status indication
                Text(
                    text = "מוכן להקלדה",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Screen Action Deck
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Clear all button
                Button(
                    onClick = onClearRequested,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("נקה", color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                // Copy note button
                Button(
                    onClick = onCopyRequested,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6).copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, Color(0xFF3B82F6)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("העתק", color = Color(0xFF3B82F6), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                // Share button
                Button(
                    onClick = onShareRequested,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981).copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("share_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("שתף", color = Color(0xFF10B981), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                // Save Note archive button
                Button(
                    onClick = onSaveRequested,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("save_note_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = "Save Note",
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("שמור", color = Color(0xFFF59E0B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Timeout / Speed Calibrator Settings panel
@Composable
fun SettingsPanel(
    timeoutMs: Long,
    onTimeoutChange: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Config text
                Text(
                    text = "זמן המתנה: ${timeoutMs}ms (${String.format("%.1f", timeoutMs / 1000f)} שנ')",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "⏱️ קצב לחיצה חוזרת",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))

            Slider(
                value = timeoutMs.toFloat(),
                onValueChange = { onTimeoutChange(it.toLong()) },
                valueRange = 350f..2500f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFF59E0B),
                    activeTrackColor = Color(0xFFF59E0B),
                    inactiveTrackColor = Color(0xFF334155)
                ),
                modifier = Modifier.testTag("timeout_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "מהיר מאוד (350ms)",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Left
                )
                Text(
                    text = "רגוע/איטי (2.5 שניות)",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Right
                )
            }
        }
    }
}

// Emoji and preformed text row templates
@Composable
fun EmojiAndPhrasesDeck(
    emojis: List<String>,
    quickPhrases: List<String>,
    onEmojiSelected: (String) -> Unit,
    onPhraseSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Emojis row title
            Text(
                text = "🥰 מקלדת אימוג'י מהירה",
                color = Color(0xFFEC4899),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(emojis) { emoji ->
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF334155))
                            .clickable { onEmojiSelected(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 18.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Phrases row header
            Text(
                text = "💬 נוסחים מהירים וברכות",
                color = Color(0xFF38BDF8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("phrases_deck"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(quickPhrases) { phrase ->
                    SuggestionChip(
                        onClick = { onPhraseSelected(phrase) },
                        label = { Text(text = phrase, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF38BDF8).copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }
    }
}

// Tactile simulated Phone Keys (T9 Keypad arrangement)
@Composable
fun T9NumericKeypad(
    activeMode: KeyboardMode,
    onKeyPress: (Int) -> Unit,
    onStarPress: () -> Unit,
    onHashPress: () -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Grid numbers 1 to 9
        val numberLayout = listOf(
            listOf(1, 2, 3),
            listOf(4, 5, 6),
            listOf(7, 8, 9)
        )

        numberLayout.forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowKeys.forEach { number ->
                    KeypadButton(
                        number = number,
                        activeMode = activeMode,
                        onPress = { onKeyPress(number) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Bottom row containing *, 0, #
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Star (*) Cycles symbols
            KeypadSpecialButton(
                primaryLabel = "*",
                subLabel = "סימנים",
                colors = CardDefaults.cardColors(containerColor = Color(0xFF334155)),
                onPress = onStarPress,
                modifier = Modifier
                    .weight(1f)
                    .testTag("star_key")
            )

            // 0 Space
            KeypadSpecialButton(
                primaryLabel = "0",
                subLabel = "רווח [ ]",
                colors = CardDefaults.cardColors(containerColor = Color(0xFF475569)),
                onPress = { onKeyPress(0) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("space_key")
            )

            // Hash (#) Toggle Modes
            KeypadSpecialButton(
                primaryLabel = "#",
                subLabel = when (activeMode) {
                    KeyboardMode.HEBREW -> "עב ⬅️ אנג"
                    KeyboardMode.ENGLISH -> "אנג ⬅️ 123"
                    KeyboardMode.NUMBERS -> "123 ⬅️ עב"
                },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0284C7)),
                onPress = onHashPress,
                modifier = Modifier
                    .weight(1f)
                    .testTag("hash_key")
            )
        }

        // Control Keys: Backspace, Enter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Backspace/Delete button
            Button(
                onClick = onBackspace,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("backspace_button"),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "Backspace",
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("מחק", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            // Enter button
            Button(
                onClick = onEnter,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1.2f)
                    .height(52.dp)
                    .testTag("enter_button"),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardReturn,
                    contentDescription = "Enter New Line",
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("אנטר", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

// Custom Grid Alpha Button Key
@Composable
fun KeypadButton(
    number: Int,
    activeMode: KeyboardMode,
    onPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subText = remember(number, activeMode) {
        when (activeMode) {
            KeyboardMode.HEBREW -> {
                when (number) {
                    1 -> ". , ? !"
                    2 -> "א ב ג"
                    3 -> "ד ה ו"
                    4 -> "ז ח ט"
                    5 -> "י כ ל ך"
                    6 -> "מ נ ס ם ן"
                    7 -> "ע פ צ ף ץ"
                    8 -> "ק ר ש"
                    9 -> "ת"
                    else -> ""
                }
            }
            KeyboardMode.ENGLISH -> {
                when (number) {
                    1 -> ". , ? !"
                    2 -> "a b c"
                    3 -> "d e f"
                    4 -> "g h i"
                    5 -> "j k l"
                    6 -> "m n o"
                    7 -> "p q r s"
                    8 -> "t u v"
                    9 -> "w x y z"
                    else -> ""
                }
            }
            KeyboardMode.NUMBERS -> ""
        }
    }

    Card(
        modifier = modifier
            .height(54.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onPress
            )
            .testTag("keypad_btn_$number"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF334155)),
        border = BorderStroke(1.dp, Color(0xFF475569))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = number.toString(),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            if (subText.isNotEmpty()) {
                Text(
                    text = subText,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Special functional key button for Layout grid
@Composable
fun KeypadSpecialButton(
    primaryLabel: String,
    subLabel: String,
    colors: CardColors,
    onPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(54.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onPress
            ),
        shape = RoundedCornerShape(14.dp),
        colors = colors,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = primaryLabel,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subLabel,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Saved Notes Archive Layout
@Composable
fun SavedNotesArchive(
    notes: List<SavedNote>,
    onLoadNote: (SavedNote) -> Unit,
    onDeleteNote: (SavedNote) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Number of active files saved
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFF59E0B).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${notes.size} רשומות",
                        color = Color(0xFFF59E0B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "💾 ארכיון הודעות ומכתבים",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "אין הודעות שמורות בארכיון הגאדג'ט שלך.",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF334155).copy(alpha = 0.5f))
                                .clickable { onLoadNote(note) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = { onDeleteNote(note) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete from history",
                                    tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = note.text,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Right
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "עודכן לאחרונה",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Mail Icon",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Load Saved Notes from SharedPreferences
fun loadSavedNotes(prefs: android.content.SharedPreferences): List<SavedNote> {
    val jsonStr = prefs.getString("saved_notes_v1", null) ?: return emptyList()
    return try {
        val jsonArray = JSONArray(jsonStr)
        val list = mutableListOf<SavedNote>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                SavedNote(
                    id = obj.getString("id"),
                    text = obj.getString("text"),
                    timestamp = obj.getLong("timestamp")
                )
            )
        }
        list.sortedByDescending { it.timestamp }
    } catch (e: Exception) {
        emptyList()
    }
}

// Save Note to SharedPreferences
fun saveNote(
    prefs: android.content.SharedPreferences,
    text: String,
    currentList: List<SavedNote>
): List<SavedNote> {
    val id = System.currentTimeMillis().toString()
    val newNote = SavedNote(id = id, text = text, timestamp = System.currentTimeMillis())
    val updatedList = (listOf(newNote) + currentList).take(20) // Cap list at 20 rows
    
    val jsonArray = JSONArray()
    updatedList.forEach { note ->
        val obj = JSONObject().apply {
            put("id", note.id)
            put("text", note.text)
            put("timestamp", note.timestamp)
        }
        jsonArray.put(obj)
    }
    prefs.edit().putString("saved_notes_v1", jsonArray.toString()).apply()
    return updatedList
}

// Delete Note from SharedPreferences
fun deleteNote(
    prefs: android.content.SharedPreferences,
    noteId: String,
    currentList: List<SavedNote>
): List<SavedNote> {
    val updatedList = currentList.filter { it.id != noteId }
    val jsonArray = JSONArray()
    updatedList.forEach { note ->
        val obj = JSONObject().apply {
            put("id", note.id)
            put("text", note.text)
            put("timestamp", note.timestamp)
        }
        jsonArray.put(obj)
    }
    prefs.edit().putString("saved_notes_v1", jsonArray.toString()).apply()
    return updatedList
}
