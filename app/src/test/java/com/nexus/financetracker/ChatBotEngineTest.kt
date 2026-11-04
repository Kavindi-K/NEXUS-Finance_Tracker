package com.nexus.financetracker

import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import com.nexus.financetracker.ui.chatbot.ChatBotNlpParser
import com.nexus.financetracker.ui.chatbot.FinancialAdvisorEngine
import com.nexus.financetracker.ui.chatbot.ParsedIntent
import com.nexus.financetracker.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Date

class ChatBotEngineTest {

    private lateinit var nlpParser: ChatBotNlpParser
    private lateinit var advisorEngine: FinancialAdvisorEngine
    private val testCategories = listOf(
        Category(id = "cat_food", name = "Food & Dining", classification = ExpenseClassification.DISCRETIONARY),
        Category(id = "cat_transport", name = "Transport", classification = ExpenseClassification.DISCRETIONARY),
        Category(id = "cat_groceries", name = "Groceries", classification = ExpenseClassification.DISCRETIONARY),
        Category(id = "cat_utilities", name = "Bills & Utilities", classification = ExpenseClassification.COMMITTED)
    )

    @Before
    fun setUp() {
        nlpParser = ChatBotNlpParser()
        advisorEngine = FinancialAdvisorEngine()
    }

    @Test
    fun parse_englishExpense_extractsAmountCategoryAndAccount() {
        val input = "Spent 2500 for groceries cash"
        val intent = nlpParser.parse(input, testCategories)

        assertTrue(intent is ParsedIntent.AddExpense)
        val payload = (intent as ParsedIntent.AddExpense).payload
        assertEquals(2500.0, payload.amount, 0.01)
        assertEquals("cat_groceries", payload.categoryId)
        assertEquals(ExpenseAccount.CASH, payload.account)
    }

    @Test
    fun parse_sinhalaExpense_extractsAmountAndCategory() {
        val input = "පෙට්‍රල් ගැහුවා 1500 card"
        val intent = nlpParser.parse(input, testCategories)

        assertTrue(intent is ParsedIntent.AddExpense)
        val payload = (intent as ParsedIntent.AddExpense).payload
        assertEquals(1500.0, payload.amount, 0.01)
        assertEquals("cat_transport", payload.categoryId)
        assertEquals(ExpenseAccount.CARD, payload.account)
    }

    @Test
    fun parse_incomeSalary_extractsSourceAndAmount() {
        val input = "Received salary 180000"
        val intent = nlpParser.parse(input, testCategories)

        assertTrue(intent is ParsedIntent.AddIncome)
        val payload = (intent as ParsedIntent.AddIncome).payload
        assertEquals(180000.0, payload.amount, 0.01)
        assertEquals(IncomeSource.SALARY, payload.source)
    }

    @Test
    fun parse_incomeFreelance_extractsSourceAndAmount() {
        val input = "Freelance income 45000"
        val intent = nlpParser.parse(input, testCategories)

        assertTrue(intent is ParsedIntent.AddIncome)
        val payload = (intent as ParsedIntent.AddIncome).payload
        assertEquals(45000.0, payload.amount, 0.01)
        assertEquals(IncomeSource.FREELANCE, payload.source)
    }

    @Test
    fun parse_goalCreation_extractsTitleAndTarget() {
        val input = "Save 250000 for New MacBook"
        val intent = nlpParser.parse(input, testCategories)

        assertTrue(intent is ParsedIntent.AddGoal)
        val payload = (intent as ParsedIntent.AddGoal).payload
        assertEquals(250000.0, payload.amount, 0.01)
        assertTrue(payload.title.contains("New MacBook", ignoreCase = true))
    }

    @Test
    fun parse_predictionQuery_recognizedCorrectly() {
        val intent1 = nlpParser.parse("How is my spending pace?", testCategories)
        assertTrue(intent1 is ParsedIntent.PredictionAndAdvice)

        val intent2 = nlpParser.parse("මගේ මේ මාසෙ වියදම් කොහොම වෙයිද?", testCategories)
        assertTrue(intent2 is ParsedIntent.PredictionAndAdvice)
    }

    @Test
    fun parse_summaryQuery_recognizedCorrectly() {
        val intent1 = nlpParser.parse("Monthly summary", testCategories)
        assertTrue(intent1 is ParsedIntent.FinancialSummary)

        val intent2 = nlpParser.parse("සාරාංශය බලන්න", testCategories)
        assertTrue(intent2 is ParsedIntent.FinancialSummary)
    }

    @Test
    fun parse_navigationCommand_routesToCorrectScreen() {
        val intent = nlpParser.parse("Go to reports", testCategories)
        assertTrue(intent is ParsedIntent.Navigate)
        assertEquals(Screen.Reports.route, (intent as ParsedIntent.Navigate).destinationRoute)
    }

    @Test
    fun advisorEngine_generatesPredictionAndAdvice_defaultEnglish_and_sinhalaAdaptive() {
        val now = Date()
        val incomes = listOf(
            Income(id = "inc1", userId = "u1", amountLKR = 150000.0, status = IncomeStatus.CONFIRMED, dateReceived = now)
        )
        val expenses = listOf(
            Expense(id = "exp1", userId = "u1", amountLKR = 25000.0, categoryId = "cat_food", date = now),
            Expense(id = "exp2", userId = "u1", amountLKR = 10000.0, categoryId = "cat_transport", date = now)
        )
        val targetCal = Calendar.getInstance().apply { add(Calendar.MONTH, 3) }
        val goals = listOf(
            Goal(id = "g1", userId = "u1", title = "Emergency Fund", targetAmountLKR = 100000.0, targetDate = targetCal.time)
        )

        // Default English
        val englishReport = advisorEngine.generatePredictionAndAdvice(incomes, expenses, goals, testCategories, isSinhala = false)
        assertNotNull(englishReport)
        assertTrue(englishReport.contains("Monthly Financial Forecast"))
        assertTrue(englishReport.contains("LKR 35,000"))
        assertTrue(englishReport.contains("Actionable Recommendations"))

        // Sinhala Adaptive
        val sinhalaReport = advisorEngine.generatePredictionAndAdvice(incomes, expenses, goals, testCategories, isSinhala = true)
        assertNotNull(sinhalaReport)
        assertTrue(sinhalaReport.contains("මාසික මූල්‍ය අනාවැකිය"))
        assertTrue(sinhalaReport.contains("LKR 35,000"))
        assertTrue(sinhalaReport.contains("මූල්‍ය උපදෙස්"))
    }

    @Test
    fun advisorEngine_generatesSummary_defaultEnglish_and_sinhalaAdaptive() {
        val now = Date()
        val incomes = listOf(
            Income(id = "inc1", userId = "u1", amountLKR = 100000.0, status = IncomeStatus.CONFIRMED, dateReceived = now)
        )
        val expenses = listOf(
            Expense(id = "exp1", userId = "u1", amountLKR = 40000.0, categoryId = "cat_food", date = now)
        )

        // Default English
        val englishSummary = advisorEngine.generateSummary(incomes, expenses, emptyList(), testCategories, isSinhala = false)
        assertTrue(englishSummary.contains("Monthly Financial Summary"))
        assertTrue(englishSummary.contains("LKR 100,000"))
        assertTrue(englishSummary.contains("LKR 40,000"))
        assertTrue(englishSummary.contains("+LKR 60,000"))

        // Sinhala Adaptive
        val sinhalaSummary = advisorEngine.generateSummary(incomes, expenses, emptyList(), testCategories, isSinhala = true)
        assertTrue(sinhalaSummary.contains("මේ මාසයේ මූල්‍ය සාරාංශය"))
        assertTrue(sinhalaSummary.contains("LKR 100,000"))
        assertTrue(sinhalaSummary.contains("LKR 40,000"))
        assertTrue(sinhalaSummary.contains("+LKR 60,000"))
    }

    @Test
    fun parse_sinhalaFood_mapsToFoodCategory() {
        val input = "කෑම වලට 3500ක් ගියා"
        val intent = nlpParser.parse(input, testCategories)

        assertTrue(intent is ParsedIntent.AddExpense)
        val payload = (intent as ParsedIntent.AddExpense).payload
        assertEquals(3500.0, payload.amount, 0.01)
        assertEquals("cat_food", payload.categoryId)
    }

    @Test
    fun parse_kSuffix_multipliesCorrectly() {
        val input = "Petrol 2.5k cash"
        val intent = nlpParser.parse(input, testCategories)

        assertTrue(intent is ParsedIntent.AddExpense)
        val payload = (intent as ParsedIntent.AddExpense).payload
        assertEquals(2500.0, payload.amount, 0.01)
        assertEquals("cat_transport", payload.categoryId)
        assertEquals(ExpenseAccount.CASH, payload.account)
    }

    @Test
    fun parse_navigationToExpensesAndIncome() {
        val expNav = nlpParser.parse("Go to expenses", testCategories)
        assertTrue(expNav is ParsedIntent.Navigate)
        assertEquals(Screen.Expenses.route, (expNav as ParsedIntent.Navigate).destinationRoute)

        val incNav = nlpParser.parse("Open income screen", testCategories)
        assertTrue(incNav is ParsedIntent.Navigate)
        assertEquals(Screen.Income.route, (incNav as ParsedIntent.Navigate).destinationRoute)
    }

    @Test
    fun parse_affordabilityQuery_extractsAmount() {
        val intent1 = nlpParser.parse("Can I afford 50000?", testCategories)
        assertTrue(intent1 is ParsedIntent.AffordabilityQuery)
        assertEquals(50000.0, (intent1 as ParsedIntent.AffordabilityQuery).amount, 0.01)

        val intent2 = nlpParser.parse("මට 35000ක් වියදම් කරන්න පුළුවන්ද?", testCategories)
        assertTrue(intent2 is ParsedIntent.AffordabilityQuery)
        assertEquals(35000.0, (intent2 as ParsedIntent.AffordabilityQuery).amount, 0.01)
    }

    @Test
    fun parse_financialHealthAndRunwayQueries() {
        val health = nlpParser.parse("How is my financial health?", testCategories)
        assertTrue(health is ParsedIntent.FinancialHealthQuery)

        val runway = nlpParser.parse("How long can I survive on my emergency runway?", testCategories)
        assertTrue(runway is ParsedIntent.EmergencyRunwayQuery)
    }

    @Test
    fun advisorEngine_financialHealth_evaluatesEliteSaver_likeUserKavidu() {
        val now = Date()
        val incomes = listOf(
            Income(id = "inc1", userId = "u1", amountLKR = 666859.60, status = IncomeStatus.CONFIRMED, dateReceived = now)
        )
        val expenses = listOf(
            Expense(id = "exp1", userId = "u1", amountLKR = 21300.00, categoryId = "cat_groceries", date = now)
        )

        val healthReport = advisorEngine.analyzeFinancialHealth(incomes, expenses, emptyList(), "Kavidu", isSinhala = false)
        assertTrue(healthReport.contains("Kavidu"))
        assertTrue(healthReport.contains("Elite Wealth Builder") || healthReport.contains("Strong Saver"))
        assertTrue(healthReport.contains("Emergency Runway"))

        val affordability = advisorEngine.checkAffordability(50000.0, incomes, expenses, isSinhala = false)
        assertTrue(affordability.contains("Safely Affordable"))
    }

    @Test
    fun parse_newWorkflows_categoryRecurringAndSettings() {
        // Add Category
        val catIntent = nlpParser.parse("Add category Gym with 5000 budget", testCategories)
        assertTrue(catIntent is ParsedIntent.AddCategory)
        val catPayload = (catIntent as ParsedIntent.AddCategory).payload
        assertTrue(catPayload.title.contains("Gym", ignoreCase = true))
        assertEquals(5000.0, catPayload.estimatedAmount ?: 0.0, 0.01)

        // Add Recurring Bill
        val billIntent = nlpParser.parse("Add recurring bill Netflix 2200", testCategories)
        assertTrue(billIntent is ParsedIntent.AddRecurringBill)
        val billPayload = (billIntent as ParsedIntent.AddRecurringBill).payload
        assertTrue(billPayload.title.contains("Netflix", ignoreCase = true))
        assertEquals(2200.0, billPayload.amount, 0.01)

        // Clear Alerts
        val clearIntent = nlpParser.parse("Clear all notifications", testCategories)
        assertTrue(clearIntent is ParsedIntent.ClearAlerts)

        // Settings
        val darkIntent = nlpParser.parse("Enable dark mode", testCategories)
        assertTrue(darkIntent is ParsedIntent.ToggleSetting)
        assertEquals("theme_dark", (darkIntent as ParsedIntent.ToggleSetting).settingKey)
        assertTrue(darkIntent.enable)

        // App Explanation
        val offlineTopic = nlpParser.parse("How does offline sync work without internet?", testCategories)
        assertTrue(offlineTopic is ParsedIntent.AppExplanationQuery)

        // Category Spending Query
        val foodQuery = nlpParser.parse("How much did I spend on food?", testCategories)
        assertTrue(foodQuery is ParsedIntent.CategorySpendingQuery)

        // Fallback to intelligent consultation instead of rigid Unknown
        val consult = nlpParser.parse("How can I invest wisely in 2026?", testCategories)
        assertTrue(consult is ParsedIntent.GeneralConsultation)
    }

    @Test
    fun advisorEngine_intelligentConsultationAndAppKnowledge() {
        val offlineExplanation = advisorEngine.explainAppFeature(com.nexus.financetracker.ui.chatbot.AppTopic.OFFLINE_SYNC, isSinhala = false)
        assertTrue(offlineExplanation.contains("Offline-First"))
        assertTrue(offlineExplanation.contains("Room SQLite"))

        val offlineSin = advisorEngine.explainAppFeature(com.nexus.financetracker.ui.chatbot.AppTopic.OFFLINE_SYNC, isSinhala = true)
        assertTrue(offlineSin.contains("Offline-First"))

        val consultation = advisorEngine.provideGeneralConsultation(
            "How can I save more money?",
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            isSinhala = false
        )
        assertTrue(consultation.contains("50/30/20"))
    }

    @Test
    fun advisorEngine_howIRich_returnsWealthBlueprint() {
        val now = Date()
        val incomes = listOf(
            Income(id = "inc1", userId = "u1", amountLKR = 666859.60, status = IncomeStatus.CONFIRMED, dateReceived = now)
        )
        val expenses = listOf(
            Expense(id = "exp1", userId = "u1", amountLKR = 21300.00, categoryId = "cat_groceries", date = now)
        )

        val reply = advisorEngine.provideGeneralConsultation(
            "how I rich",
            incomes,
            expenses,
            emptyList(),
            emptyList(),
            isSinhala = false
        )
        assertTrue(reply.contains("Wealth Blueprint"))
        assertTrue(reply.contains("Elite Wealth-Building"))
        assertTrue(reply.contains("Deploy Surplus Capital"))
    }

    @Test
    fun advisorEngine_whereIsMoneyGoing_returnsExpenseBreakdown() {
        val now = Date()
        val incomes = listOf(
            Income(id = "inc1", userId = "u1", amountLKR = 150000.0, status = IncomeStatus.CONFIRMED, dateReceived = now)
        )
        val expenses = listOf(
            Expense(id = "exp1", userId = "u1", amountLKR = 25000.0, categoryId = "cat_food", date = now),
            Expense(id = "exp2", userId = "u1", amountLKR = 10000.0, categoryId = "cat_transport", date = now)
        )

        val replyEng = advisorEngine.provideGeneralConsultation(
            "Where is my money going?",
            incomes,
            expenses,
            emptyList(),
            testCategories,
            isSinhala = false
        )
        assertTrue(replyEng.contains("Expense Breakdown"))
        assertTrue(replyEng.contains("Food & Dining"))
        assertTrue(replyEng.contains("25,000.00"))

        val replySin = advisorEngine.provideGeneralConsultation(
            "මගේ වියදම් ගියේ කොහෙද?",
            incomes,
            expenses,
            emptyList(),
            testCategories,
            isSinhala = true
        )
        assertTrue(replySin.contains("වියදම් විශ්ලේෂණය"))
        assertTrue(replySin.contains("Food & Dining"))
    }
}
