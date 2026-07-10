package com.example.ui

import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GeminiAnalysisResult
import kotlinx.coroutines.delay
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel) {
    val context = LocalContext.current
    val currentArticle by viewModel.currentArticle.collectAsState()
    val currentPageIndex by viewModel.currentPageIndex.collectAsState()
    val themePreference by viewModel.readerTheme.collectAsState()
    val fontSizePreference by viewModel.readerFontSize.collectAsState()

    // Gemini Lookup States
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val activeWord by viewModel.activeLookupWord.collectAsState()
    val activeSentence by viewModel.activeLookupSentence.collectAsState()
    val errorMsg by viewModel.analysisError.collectAsState()

    // Reading Colors based on Theme
    val themeColors = remember(themePreference) {
        when (themePreference) {
            "Sepia" -> ReaderColors(
                background = Color(0xFFF4ECD8),
                surface = Color(0xFFFFFBF0),
                text = Color(0xFF3E2723),
                primary = Color(0xFF795548)
            )
            "Light" -> ReaderColors(
                background = Color(0xFFF3F4F9),
                surface = Color(0xFFFFFFFF),
                text = Color(0xFF1A1C1E),
                primary = Color(0xFF0061A4)
            )
            else -> ReaderColors( // Night
                background = Color(0xFF111318),
                surface = Color(0xFF1A1C20),
                text = Color(0xFFE3E2E6),
                primary = Color(0xFFA4C9FE)
            )
        }
    }

    // TTS Setup
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    LaunchedEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
    }

    // Ensure TTS is released
    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.Home) }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = currentArticle?.title ?: "ইংরেজি ই-পেপার",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 200.dp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = currentArticle?.source ?: "ePaper Reader",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                },
                actions = {
                    // Quick Settings toggle
                    var showQuickSettings by remember { mutableStateOf(false) }
                    IconButton(onClick = { showQuickSettings = !showQuickSettings }) {
                        Icon(
                            imageVector = Icons.Default.TextFormat,
                            contentDescription = "Reader Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    
                    if (showQuickSettings) {
                        QuickSettingsDialog(
                            currentTheme = themePreference,
                            currentFontSize = fontSizePreference,
                            onThemeChanged = { viewModel.changeTheme(it) },
                            onFontSizeChanged = { viewModel.changeFontSize(it) },
                            onDismiss = { showQuickSettings = false }
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // Reader Navigation Bar (Previous / Page X of Y / Next)
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val maxPages = currentArticle?.pages?.size ?: 0
                    
                    OutlinedButton(
                        onClick = { viewModel.prevPage() },
                        enabled = currentPageIndex > 0,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.NavigateBefore, contentDescription = "Prev Page")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("পূর্বের পৃষ্ঠা", fontSize = 13.sp)
                    }

                    Text(
                        text = "পৃষ্ঠা ${currentPageIndex + 1} / $maxPages",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    OutlinedButton(
                        onClick = { viewModel.nextPage() },
                        enabled = currentPageIndex < maxPages - 1,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("পরবর্তী পৃষ্ঠা", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(imageVector = Icons.Default.NavigateNext, contentDescription = "Next Page")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(themeColors.background)
                .padding(innerPadding)
        ) {
            val pageText = currentArticle?.pages?.getOrNull(currentPageIndex) ?: ""
            
            if (pageText.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Reader text viewport
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = themeColors.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            ReaderPageContent(
                                pageText = pageText,
                                fontSize = fontSizePreference,
                                textColor = themeColors.text,
                                onWordHeld = { word, sentence ->
                                    viewModel.performWordLookup(word, sentence)
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(180.dp)) // Extra space to scroll past the bottom popup card
                }
            }

            // Floating Word Lookup Overlay Bottom Card
            AnimatedVisibility(
                visible = activeWord != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Header with word and TTS speak button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = activeWord ?: "",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        tts?.speak(activeWord, TextToSpeech.QUEUE_FLUSH, null, null)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.VolumeUp,
                                        contentDescription = "Speak word",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.clearActiveLookup() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.Gray
                                )
                            }
                        }

                        Divider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = Color.LightGray.copy(alpha = 0.5f)
                        )

                        // Body Content state transitions
                        if (isAnalyzing) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "জেমিনি AI বিশ্লেষণ করছে...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (errorMsg != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = errorMsg ?: "",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        viewModel.performWordLookup(activeWord ?: "", activeSentence ?: "")
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("আবার চেষ্টা করুন")
                                }
                            }
                        } else if (analysisResult != null) {
                            LookupResultBody(result = analysisResult!!)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LookupResultBody(result: GeminiAnalysisResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Bengali Meaning Badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = result.meaningBengali,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Contextual Explanation (Bengali)
        Column {
            Text(
                text = "১. বাক্যে ব্যবহার ও অর্থ (Contextual Meaning):",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = result.contextualMeaning,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp)
            )
        }

        // Grammar rules
        Column {
            Text(
                text = "২. ব্যাকরণগত ব্যাখ্যা (Grammar Rules):",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = result.grammarRules,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp)
            )
        }

        // English Example with Bengali Meaning
        Column {
            Text(
                text = "৩. আরেকটি উদাহরণ বাক্য (Similar Example):",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = result.engExample,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp)
            )
            Text(
                text = result.banglaExample,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 1.dp, start = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderPageContent(
    pageText: String,
    fontSize: Float,
    textColor: Color,
    onWordHeld: (word: String, sentence: String) -> Unit
) {
    // Split text by lines to construct paragraphs
    val paragraphs = remember(pageText) {
        pageText.split("\n").filter { it.trim().isNotEmpty() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        paragraphs.forEach { paraText ->
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.Center
            ) {
                val words = remember(paraText) { paraText.split(" ") }
                var charOffset = 0
                
                words.forEach { rawWord ->
                    if (rawWord.isNotEmpty()) {
                        val clean = rawWord.filter { it.isLetterOrDigit() || it == '-' || it == '\'' }
                        val currentOffset = charOffset
                        
                        WordText(
                            word = rawWord,
                            cleanWord = clean,
                            onWordHeld = {
                                val sentence = getSentenceContainingWord(pageText, rawWord, currentOffset)
                                onWordHeld(clean, sentence)
                            },
                            fontSizeSp = fontSize,
                            textColor = textColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    charOffset += rawWord.length + 1
                }
            }
        }
    }
}

@Composable
fun WordText(
    word: String,
    cleanWord: String,
    onWordHeld: () -> Unit,
    fontSizeSp: Float,
    textColor: Color
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var progress by remember { mutableStateOf(0f) }

    // When held down, animates progress from 0f to 1f over 2.0 seconds
    LaunchedEffect(isPressed) {
        if (isPressed && cleanWord.isNotEmpty()) {
            val startTime = System.currentTimeMillis()
            val duration = 2000L // 2 seconds hold duration as requested!
            while (progress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                progress = (elapsed.toFloat() / duration).coerceAtMost(1f)
                if (progress >= 1f) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onWordHeld()
                    break
                }
                delay(16) // smooth 60fps updates
            }
        } else {
            progress = 0f
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (progress > 0f) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f + (progress * 0.28f))
                } else Color.Transparent
            )
            .padding(horizontal = 2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 1.dp)
        ) {
            Text(
                text = word,
                fontSize = fontSizeSp.sp,
                fontFamily = FontFamily.Serif,
                color = textColor
            )
            
            // Render smooth horizontal loading line under the word during active hold
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

/**
 * Strips page-text boundary indexes to retrieve sentence structure.
 */
fun getSentenceContainingWord(pageText: String, word: String, wordStartIndex: Int): String {
    if (pageText.isEmpty() || word.isEmpty()) return ""
    
    var start = wordStartIndex
    while (start > 0) {
        val char = pageText[start - 1]
        if (char == '.' || char == '!' || char == '?' || char == '\n') {
            break
        }
        start--
    }
    
    var end = wordStartIndex + word.length
    while (end < pageText.length) {
        val char = pageText[end]
        if (char == '.' || char == '!' || char == '?') {
            end++
            break
        }
        if (char == '\n') {
            break
        }
        end++
    }
    
    val sentence = pageText.substring(start, end).trim()
    return if (sentence.length > 5) sentence else pageText.take(150)
}

@Composable
fun QuickSettingsDialog(
    currentTheme: String,
    currentFontSize: Float,
    onThemeChanged: (String) -> Unit,
    onFontSizeChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("রিডার কনফিগারেশন", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Font Size Slider
                Column {
                    Text(text = "লেখা বড়/ছোট করুন: ${currentFontSize.toInt()}sp", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = currentFontSize,
                        onValueChange = { onFontSizeChanged(it) },
                        valueRange = 14f..28f,
                        steps = 6
                    )
                }

                // Theme selection
                Column {
                    Text(text = "রিডার থিম সিলেক্ট করুন:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ThemeButton(name = "Sepia", current = currentTheme, onClick = { onThemeChanged("Sepia") }, modifier = Modifier.weight(1f))
                        ThemeButton(name = "Light", current = currentTheme, onClick = { onThemeChanged("Light") }, modifier = Modifier.weight(1f))
                        ThemeButton(name = "Night", current = currentTheme, onClick = { onThemeChanged("Night") }, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("বন্ধ করুন")
            }
        }
    )
}

@Composable
fun ThemeButton(name: String, current: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isSelected = current == name
    OutlinedButton(
        onClick = { onClick() },
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f)
        ),
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(text = name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

data class ReaderColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val primary: Color
)
