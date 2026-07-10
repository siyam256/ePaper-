package com.example.pdf

data class Article(
    val title: String,
    val source: String,
    val date: String,
    val pages: List<String>
)

object PreloadedArticles {
    val list = listOf(
        Article(
            title = "Bangladesh Steps Up Renewable Energy Transition with New Policies",
            source = "The Daily Star ePaper",
            date = "July 10, 2026",
            pages = listOf(
                """
                DHAKA — In an unprecedented move to combat the adverse effects of climate change, Bangladesh has officially launched its National Green Transition Initiative. The policy, ratified in parliament yesterday, aims to generate thirty percent of the country's electricity from renewable sources by 2030. 

                Government spokespersons emphasized that this transition is not merely an environmental obligation but a critical economic imperative. Major investments are already being channeled into large-scale solar projects in the northern districts.

                Furthermore, the government has unveiled attractive tax exemptions for local enterprises that utilize sustainable energy grids, thereby encouraging micro-level integration of green solutions.
                """.trimIndent(),
                """
                Despite these optimistic strides, environmental experts remain cautiously optimistic. They warn that the primary hurdle lies in modernization of the national power grid, which is currently ill-equipped to handle the fluctuating nature of wind and solar power.

                "Infrastructure revamp is the prerequisite for a sustainable green transition," observed Dr. Salimul Huq during a symposium held in the capital. 

                Nevertheless, this legislation marks a monumental shift. By fostering foreign direct investment and simplifying bureaucracy, the administration is striving to position Bangladesh as an regional leader in ecological innovation.
                """.trimIndent()
            )
        ),
        Article(
            title = "Artificial Intelligence Redefines Modern Language Education",
            source = "Financial Express ePaper",
            date = "July 08, 2026",
            pages = listOf(
                """
                LONDON — The proliferation of large language models has fundamentally altered the landscape of educational pedagogy. Rather than adhering to conventional rote memorization, students worldwide are increasingly adopting real-time AI companions to acquire foreign languages dynamically.

                Linguists explain that the most formidable barrier to mastering English is the sheer nuance of contextual semantics. A single word can adopt entirely disparate meanings depending on the syntactic architecture of the surrounding sentence.

                Traditional dictionaries often fail to explain these local idioms or localized grammatical structures, leaving learners perplexed and demotivated.
                """.trimIndent(),
                """
                By integrating specialized LLM agents directly into reading materials, educators can now offer localized grammatical breakdowns, Bengali translations of phrases, and dynamic contextual assessments on the fly.

                "It is a paradigm shift in cognitive scaffolding," said Professor Sarah Jenkins, head of Applied Linguistics. "Learners are no longer passive recipients of information; they are active, inquisitive navigators of text."

                As real-time AI tools become more integrated into everyday mobile applications, language barriers are collapsing, ushering in an era of democratization in global academic access.
                """.trimIndent()
            )
        ),
        Article(
            title = "Global Market Volatility Triggers Fiscal Corrections",
            source = "The Economist ePaper",
            date = "July 05, 2026",
            pages = listOf(
                """
                NEW YORK — Rapid shifts in international trade corridors and escalating tariff adjustments have triggered widespread volatility across global financial markets. Stocks in tech and manufacturing sectors plunged yesterday as central banks hinted at another round of interest rate hikes to curb inflation.

                Financial analysts believe that these aggressive monetary policies are indispensable for long-term fiscal stability. However, the short-term ramifications are undeniable, particularly for emerging economies facing currency depreciation and capital outflows.

                Major corporations are already adopting defensive postures, slashing capital expenditures and pausing high-risk expansion strategies.
                """.trimIndent(),
                """
                On the contrary, some experts assert that the current market downturn represents a healthy correction of overvalued stocks, paving the way for sustainable valuation models.

                "The current turbulence should not be misconstrued as an impending recession," noted chief strategist Marcus Aurelius. "It is an adjustment cycle that will ultimately weed out speculative bubbles."

                For retail investors, the consensus advice remains steadfast: diversify portfolios, avoid panic-selling, and focus on companies with robust cash flows and low debt-to-equity ratios.
                """.trimIndent()
            )
        )
    )
}
