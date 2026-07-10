package com.example.ui

import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.GeminiAnalysisResult
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel) {
    val context = LocalContext.current
    val currentArticle by viewModel.currentArticle.collectAsState()
    val currentPageIndex by viewModel.currentPageIndex.collectAsState()
    val themePreference by viewModel.readerTheme.collectAsState()
    val fontSizePreference by viewModel.readerFontSize.collectAsState()
    val currentPdfUri by viewModel.currentPdfUri.collectAsState()
    val isPdfMode = currentPdfUri != null && currentArticle?.pages?.firstOrNull() == "PDF_VIEW"

    // Gemini Lookup States
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val activeWord by viewModel.activeLookupWord.collectAsState()
    val activeSentence by viewModel.activeLookupSentence.collectAsState()
    val errorMsg by viewModel.analysisError.collectAsState()

    // Manual input query matching the selection
    var manualSearchQuery by remember { mutableStateOf("") }

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

    // Clipboard listener to automatically perform lookup when user highlights & copies text
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }
    var lastCopiedText by remember { mutableStateOf("") }

    DisposableEffect(context) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            val clip = clipboardManager?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()?.trim()
                if (!text.isNullOrBlank() && text != lastCopiedText) {
                    lastCopiedText = text
                    manualSearchQuery = text
                    // Instantly trigger dictionary lookup on copy event!
                    viewModel.performWordLookup(text, text)
                }
            }
        }
        clipboardManager?.addPrimaryClipChangedListener(listener)
        onDispose {
            clipboardManager?.removePrimaryClipChangedListener(listener)
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
            if (!isPdfMode) {
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
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(themeColors.background)
                .padding(innerPadding)
        ) {
            val pageText = currentArticle?.pages?.getOrNull(currentPageIndex) ?: ""
            
            if (isPdfMode && currentPdfUri != null) {
                PdfWebViewReader(
                    pdfUri = currentPdfUri!!,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (pageText.isEmpty()) {
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
                    // Modern selection & manual lookup input block
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = themeColors.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = themeColors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "জেমিনি AI অনুবাদ ও ব্যাখ্যা",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = themeColors.primary
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = manualSearchQuery,
                                onValueChange = { manualSearchQuery = it },
                                placeholder = { Text("যেকোনো ইংরেজি শব্দ বা বাক্য পেস্ট করুন...", fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = themeColors.background,
                                    unfocusedContainerColor = themeColors.background,
                                    focusedBorderColor = themeColors.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                trailingIcon = {
                                    if (manualSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = {
                                            viewModel.performWordLookup(manualSearchQuery, manualSearchQuery)
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = "Search text with Gemini",
                                                tint = themeColors.primary
                                            )
                                        }
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "💡 টিপস: নিচের পৃষ্ঠা থেকে যেকোনো অংশ সিলেক্ট করে 'Copy' করলেই সেটি স্বয়ংক্রিয়ভাবে জেমিনি AI দিয়ে অনুবাদ হয়ে যাবে!",
                                fontSize = 11.sp,
                                color = themeColors.text.copy(alpha = 0.7f),
                                lineHeight = 16.sp
                                )
                        }
                    }

                    // Main selectable PDF page content block
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
                                textColor = themeColors.text
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(260.dp)) // Extra space to scroll past the bottom popup card
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
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
            .heightIn(max = 240.dp)
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

@Composable
fun ReaderPageContent(
    pageText: String,
    fontSize: Float,
    textColor: Color
) {
    SelectionContainer {
        Text(
            text = pageText,
            fontSize = fontSize.sp,
            fontFamily = FontFamily.Serif,
            color = textColor,
            lineHeight = (fontSize * 1.5f).sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
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

@Composable
fun PdfWebViewReader(
    pdfUri: Uri,
    viewModel: ReaderViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val pdfHtmlTemplate = remember(pdfUri) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
          <title>PDF Viewer</title>
          <!-- Load PDF.js -->
          <script src="https://cdnjs.cloudflare.com/ajax/libs/pdf.js/2.16.105/pdf.min.js"></script>
          <!-- Load PDF.js CSS for text layer styling -->
          <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/pdf.js/2.16.105/pdf_viewer.min.css">
          <style>
            body {
              margin: 0;
              padding: 10px;
              background-color: #F3F4F9;
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            }
            #viewer-container {
              display: flex;
              flex-direction: column;
              gap: 16px;
              align-items: center;
              padding-bottom: 240px; /* Leave generous space at the bottom for lookup drawer overlay */
            }
            .page-container {
              position: relative;
              background-color: white;
              box-shadow: 0 4px 10px rgba(0, 0, 0, 0.15);
              border-radius: 8px;
              overflow: hidden;
              margin-bottom: 8px;
            }
            canvas {
              display: block;
              width: 100% !important;
              height: auto !important;
            }
            .textLayer {
              position: absolute;
              left: 0;
              top: 0;
              right: 0;
              bottom: 0;
              overflow: hidden;
              opacity: 0.35;
              line-height: 1.0;
            }
            .textLayer > span {
              color: transparent;
              position: absolute;
              white-space: pre;
              cursor: text;
              transform-origin: 0% 0%;
            }
            ::selection {
              background: rgba(0, 97, 164, 0.3) !important;
              color: transparent !important;
            }
            .loading-banner {
              position: fixed;
              top: 50%;
              left: 50%;
              transform: translate(-50%, -50%);
              font-size: 16px;
              font-weight: bold;
              color: #555;
              background: white;
              padding: 16px 24px;
              border-radius: 12px;
              box-shadow: 0 4px 12px rgba(0,0,0,0.1);
              z-index: 1000;
              text-align: center;
            }
          </style>
        </head>
        <body>
          <div id="loading" class="loading-banner">পিডিএফ ফাইল লোড হচ্ছে...</div>
          <div id="viewer-container"></div>

          <script>
            pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/2.16.105/pdf.worker.min.js';

            async function loadStreamedPdf() {
              const loadingEl = document.getElementById('loading');
              
              try {
                const totalSize = AndroidStream.getPdfSize();
                if (totalSize === 0) {
                  loadingEl.innerText = "পিডিএফ ফাইলটি খালি বা পড়া যায়নি।";
                  return;
                }

                const chunkSize = 512 * 1024; // 512KB
                const totalBytes = new Uint8Array(totalSize);
                let offset = 0;

                while (offset < totalSize) {
                  const base64Chunk = AndroidStream.getPdfBase64Chunk(offset, chunkSize);
                  if (!base64Chunk) break;
                  
                  const binaryString = window.atob(base64Chunk);
                  for (let i = 0; i < binaryString.length; i++) {
                    totalBytes[offset + i] = binaryString.charCodeAt(i);
                  }
                  offset += binaryString.length;
                  
                  const percent = Math.min(100, Math.round((offset / totalSize) * 100));
                  loadingEl.innerText = "পিডিএফ লোড হচ্ছে... (" + percent + "%)";
                }

                loadingEl.innerText = "পৃষ্ঠাগুলো তৈরি করা হচ্ছে...";
                const loadingTask = pdfjsLib.getDocument({data: totalBytes});
                
                loadingTask.promise.then(function(pdf) {
                  loadingEl.style.display = 'none';
                  
                  // Render pages sequentially for performance
                  let renderPromise = Promise.resolve();
                  for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
                    renderPromise = renderPromise.then(() => renderPage(pdf, pageNum));
                  }
                }, function(error) {
                  loadingEl.innerText = "পিডিএফ খুলতে ব্যর্থ হয়েছে: " + error.message;
                });

              } catch (err) {
                loadingEl.innerText = "ত্রুটি ঘটেছে: " + err.message;
              }
            }

            function renderPage(pdf, pageNum) {
              return pdf.getPage(pageNum).then(function(page) {
                const scale = 1.5;
                const viewport = page.getViewport({scale: scale});

                const pageContainer = document.createElement('div');
                pageContainer.className = 'page-container';
                pageContainer.style.width = '100%';
                pageContainer.style.maxWidth = viewport.width + 'px';
                
                const aspectRatio = viewport.height / viewport.width;
                pageContainer.style.aspectRatio = (1 / aspectRatio).toString();

                const canvas = document.createElement('canvas');
                const context = canvas.getContext('2d');
                canvas.height = viewport.height;
                canvas.width = viewport.width;

                const textLayerDiv = document.createElement('div');
                textLayerDiv.className = 'textLayer';

                pageContainer.appendChild(canvas);
                pageContainer.appendChild(textLayerDiv);
                document.getElementById('viewer-container').appendChild(pageContainer);

                const renderContext = {
                  canvasContext: context,
                  viewport: viewport
                };
                
                return page.render(renderContext).promise.then(function() {
                  return page.getTextContent();
                }).then(function(textContent) {
                  return pdfjsLib.renderTextLayer({
                    textContent: textContent,
                    container: textLayerDiv,
                    viewport: viewport,
                    textDivs: []
                  }).promise;
                });
              });
            }

            // Capture text selection with delay
            let selectionTimeout = null;
            document.addEventListener('selectionchange', () => {
              if (selectionTimeout) clearTimeout(selectionTimeout);
              
              selectionTimeout = setTimeout(() => {
                const selection = window.getSelection();
                const selectedText = selection.toString().trim();
                if (selectedText.length > 0) {
                  let sentenceContext = selectedText;
                  try {
                    const anchorNode = selection.anchorNode;
                    if (anchorNode) {
                      const parentEl = anchorNode.parentNode;
                      if (parentEl) {
                        const fullText = parentEl.innerText || parentEl.textContent;
                        if (fullText && fullText.length > selectedText.length) {
                          sentenceContext = fullText;
                        }
                      }
                    }
                  } catch(e) {}
                  
                  if (window.Android) {
                    window.Android.onTextSelected(selectedText, sentenceContext);
                  }
                }
              }, 1500); // 1.5 - 2 seconds delay
            });

            // Handle immediate triggers on touch/mouse release
            function handleSelectionTrigger() {
              const selection = window.getSelection();
              const selectedText = selection.toString().trim();
              if (selectedText.length > 0) {
                let sentenceContext = selectedText;
                try {
                  const anchorNode = selection.anchorNode;
                  if (anchorNode) {
                    const parentEl = anchorNode.parentNode;
                    if (parentEl) {
                      const fullText = parentEl.innerText || parentEl.textContent;
                      if (fullText && fullText.length > selectedText.length) {
                        sentenceContext = fullText;
                      }
                    }
                  }
                } catch(e) {}
                
                if (window.Android) {
                  window.Android.onTextSelected(selectedText, sentenceContext);
                }
              }
            }

            document.addEventListener('touchend', () => setTimeout(handleSelectionTrigger, 600));
            document.addEventListener('mouseup', () => setTimeout(handleSelectionTrigger, 600));

            window.onload = function() {
              loadStreamedPdf();
            };
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.domStorageEnabled = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.setSupportZoom(true)
                
                // Add stream javascript interface
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun getPdfBase64Chunk(offset: Int, limit: Int): String {
                        return try {
                            context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                                inputStream.skip(offset.toLong())
                                val buffer = ByteArray(limit)
                                val bytesRead = inputStream.read(buffer)
                                if (bytesRead > 0) {
                                    val actualBytes = if (bytesRead < limit) buffer.copyOf(bytesRead) else buffer
                                    android.util.Base64.encodeToString(actualBytes, android.util.Base64.NO_WRAP)
                                } else {
                                    ""
                                }
                            } ?: ""
                        } catch (e: Exception) {
                            android.util.Log.e("PdfStream", "Error reading chunk", e)
                            ""
                        }
                    }
                    
                    @android.webkit.JavascriptInterface
                    fun getPdfSize(): Int {
                        return try {
                            var size: Long = 0
                            context.contentResolver.query(pdfUri, null, null, null, null)?.use { cursor ->
                                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                if (sizeIndex != -1 && cursor.moveToFirst()) {
                                    size = cursor.getLong(sizeIndex)
                                }
                            }
                            if (size == 0L) {
                                context.contentResolver.openInputStream(pdfUri)?.use {
                                    size = it.available().toLong()
                                }
                            }
                            size.toInt()
                        } catch (e: Exception) {
                            android.util.Log.e("PdfStream", "Error getting size", e)
                            0
                        }
                    }
                }, "AndroidStream")

                // Add text selected javascript interface
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onTextSelected(text: String, contextSentence: String) {
                        viewModel.performWordLookup(text, contextSentence)
                    }
                }, "Android")

                // Load inline HTML template content
                loadDataWithBaseURL("https://mozilla.github.io/pdf.js/", pdfHtmlTemplate, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier
    )
}
