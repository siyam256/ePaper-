package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import com.example.BuildConfig

// --- Gemini API Data Classes for Moshi ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "responseMimeType") val responseMimeType: String? = null,
    @Json(name = "temperature") val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content?
)

@JsonClass(generateAdapter = true)
data class GeminiAnalysisResult(
    @Json(name = "meaningBengali") val meaningBengali: String,
    @Json(name = "contextualMeaning") val contextualMeaning: String,
    @Json(name = "grammarRules") val grammarRules: String,
    @Json(name = "engExample") val engExample: String,
    @Json(name = "banglaExample") val banglaExample: String
)

// --- Retrofit Client Service ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiService {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshiInstance: Moshi = Moshi.Builder().build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshiInstance))
        .build()

    private val apiService: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    /**
     * Executes the call to Gemini to analyze a given word or phrase in context.
     * Fallback to BuildConfig key if the user-entered custom key is not present.
     */
    suspend fun analyzeText(
        word: String,
        contextSentence: String,
        customApiKey: String
    ): GeminiAnalysisResult {
        // Resolve API key
        val keyToUse = customApiKey.trim().ifEmpty {
            BuildConfig.GEMINI_API_KEY
        }

        if (keyToUse.isEmpty()) {
            throw IllegalStateException("API Key is missing! Please configure it in Settings or add GEMINI_API_KEY as a secret.")
        }

        val prompt = "Word/Phrase to analyze: '$word'\nSentence/Context: '$contextSentence'"

        val systemInstructionText = """
            You are an expert English teacher fluent in Bengali. Your job is to analyze the given English word or phrase in its specific sentence context and return a valid JSON object in Bengali. 
            The JSON object must EXACTLY follow this structure:
            {
              "meaningBengali": "বাংলা অর্থ (যেমন: 'অনিবার্য', 'পরিবর্তনশীল')",
              "contextualMeaning": "এই বাক্যে শব্দটি কী অর্থে ব্যবহার করা হয়েছে এবং এর গভীর অর্থ কী (বিশদ ব্যাখ্যা বাংলা ভাষায়)",
              "grammarRules": "শব্দটির ব্যাকরণগত ব্যাখ্যা (Parts of speech, এবং কোনো বিশেষ গ্রামার রুল বা গঠন যা এখানে প্রযোজ্য)",
              "engExample": "Another natural, distinct English sentence using this word",
              "banglaExample": "বাংলা অনুবাদ (নতুন ইংরেজি বাক্যটির সঠিক অনুবাদ)"
            }
            Do not include any markdown format tags like ```json or anything. Just raw, pure JSON text. Return ONLY the JSON object.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = systemInstructionText))
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            )
        )

        val response = apiService.generateContent(keyToUse, request)
        val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Received an empty response from Gemini.")

        // Parse the JSON response
        val adapter = moshiInstance.adapter(GeminiAnalysisResult::class.java)
        return adapter.fromJson(jsonText)
            ?: throw IllegalStateException("Failed to parse Gemini response: $jsonText")
    }
}
