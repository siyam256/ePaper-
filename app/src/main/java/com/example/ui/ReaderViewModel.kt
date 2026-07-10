package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeminiAnalysisResult
import com.example.data.GeminiService
import com.example.data.LookupItem
import com.example.data.PreferencesManager
import com.example.pdf.Article
import com.example.pdf.PdfTextExtractor
import com.example.pdf.PreloadedArticles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ReaderViewModel(context: Context) : ViewModel() {
    private val prefsManager = PreferencesManager(context)

    // --- Preferences States ---
    private val _apiKey = MutableStateFlow(prefsManager.getApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _readerTheme = MutableStateFlow(prefsManager.getTheme())
    val readerTheme: StateFlow<String> = _readerTheme.asStateFlow()

    private val _readerFontSize = MutableStateFlow(prefsManager.getFontSize())
    val readerFontSize: StateFlow<Float> = _readerFontSize.asStateFlow()

    private val _history = MutableStateFlow(prefsManager.getLookupHistory())
    val history: StateFlow<List<LookupItem>> = _history.asStateFlow()

    // --- Navigation & Content States ---
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _currentArticle = MutableStateFlow<Article?>(null)
    val currentArticle: StateFlow<Article?> = _currentArticle.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    // --- PDF Processing States ---
    private val _isExtractingPdf = MutableStateFlow(false)
    val isExtractingPdf: StateFlow<Boolean> = _isExtractingPdf.asStateFlow()

    private val _pdfName = MutableStateFlow<String?>(null)
    val pdfName: StateFlow<String?> = _pdfName.asStateFlow()

    private val _currentPdfUri = MutableStateFlow<Uri?>(null)
    val currentPdfUri: StateFlow<Uri?> = _currentPdfUri.asStateFlow()

    // --- Gemini Analysis States ---
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisResult = MutableStateFlow<GeminiAnalysisResult?>(null)
    val analysisResult: StateFlow<GeminiAnalysisResult?> = _analysisResult.asStateFlow()

    private val _activeLookupWord = MutableStateFlow<String?>(null)
    val activeLookupWord: StateFlow<String?> = _activeLookupWord.asStateFlow()

    private val _activeLookupSentence = MutableStateFlow<String?>(null)
    val activeLookupSentence: StateFlow<String?> = _activeLookupSentence.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    init {
        // Automatically initialize PdfTextExtractor
        PdfTextExtractor.init(context)
    }

    // --- Actions ---

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
        // Clear active analysis when switching away from Reader
        if (screen != Screen.Reader) {
            clearActiveLookup()
        }
    }

    fun selectPreloadedArticle(article: Article) {
        _currentArticle.value = article
        _currentPageIndex.value = 0
        _pdfName.value = null
        _currentPdfUri.value = null
        navigateTo(Screen.Reader)
    }

    fun loadPdf(context: Context, uri: Uri, fileName: String) {
        viewModelScope.launch {
            _isExtractingPdf.value = true
            _analysisError.value = null
            _currentPdfUri.value = uri
            try {
                val pages = PdfTextExtractor.extractTextFromUri(context, uri)
                if (pages.isEmpty()) {
                    _analysisError.value = "পিডিএফ থেকে কোনো টেক্সট পাওয়া যায়নি।"
                } else {
                    val newArticle = Article(
                        title = fileName,
                        source = "আমার আপলোড করা পিডিএফ",
                        date = "আজ",
                        pages = pages
                    )
                    _currentArticle.value = newArticle
                    _currentPageIndex.value = 0
                    _pdfName.value = fileName
                    navigateTo(Screen.Reader)
                }
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to extract PDF", e)
                _analysisError.value = "পিডিএফ লোড করতে সমস্যা হয়েছে: ${e.localizedMessage}"
            } finally {
                _isExtractingPdf.value = false
            }
        }
    }

    fun openPdfInSystemViewer(context: Context) {
        val uri = _currentPdfUri.value ?: return
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("ReaderViewModel", "Failed to open PDF in system viewer", e)
        }
    }

    fun nextPage() {
        val maxPages = _currentArticle.value?.pages?.size ?: 0
        if (_currentPageIndex.value < maxPages - 1) {
            _currentPageIndex.value += 1
            clearActiveLookup()
        }
    }

    fun prevPage() {
        if (_currentPageIndex.value > 0) {
            _currentPageIndex.value -= 1
            clearActiveLookup()
        }
    }

    fun changeTheme(theme: String) {
        _readerTheme.value = theme
        prefsManager.saveTheme(theme)
    }

    fun changeFontSize(size: Float) {
        _readerFontSize.value = size
        prefsManager.saveFontSize(size)
    }

    fun updateApiKey(key: String) {
        _apiKey.value = key
        prefsManager.saveApiKey(key)
    }

    fun performWordLookup(word: String, contextSentence: String) {
        val cleanWord = word.trim().filter { it.isLetterOrDigit() || it == '-' || it == '\'' }
        if (cleanWord.isEmpty()) return

        _activeLookupWord.value = cleanWord
        _activeLookupSentence.value = contextSentence.trim()
        _isAnalyzing.value = true
        _analysisResult.value = null
        _analysisError.value = null

        viewModelScope.launch {
            try {
                val result = GeminiService.analyzeText(
                    word = cleanWord,
                    contextSentence = contextSentence,
                    customApiKey = _apiKey.value
                )
                _analysisResult.value = result
                
                // Add to lookup history
                val historyItem = LookupItem(
                    id = UUID.randomUUID().toString(),
                    word = cleanWord,
                    contextSentence = contextSentence,
                    timestamp = System.currentTimeMillis(),
                    meaningBengali = result.meaningBengali,
                    contextualMeaning = result.contextualMeaning,
                    grammarRules = result.grammarRules,
                    engExample = result.engExample,
                    banglaExample = result.banglaExample
                )
                prefsManager.addLookupItem(historyItem)
                _history.value = prefsManager.getLookupHistory() // Refresh history

            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Gemini Lookup error", e)
                _analysisError.value = e.localizedMessage ?: "একটি অপ্রত্যাশিত ভুল হয়েছে।"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun clearActiveLookup() {
        _activeLookupWord.value = null
        _activeLookupSentence.value = null
        _analysisResult.value = null
        _analysisError.value = null
        _isAnalyzing.value = false
    }

    fun deleteHistoryItem(id: String) {
        prefsManager.removeLookupItem(id)
        _history.value = prefsManager.getLookupHistory()
    }

    fun clearAllHistory() {
        prefsManager.clearHistory()
        _history.value = emptyList()
    }
}

sealed interface Screen {
    object Home : Screen
    object Reader : Screen
    object History : Screen
    object Settings : Screen
}
