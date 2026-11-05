# NEXUS — Personal Finance Management System

A problem-first, scenario-driven Android personal finance application built with **Kotlin (100%)**, **Jetpack Compose (Material Design 3)**, **MVVM Architecture**, **Firebase** (Authentication & Cloud Firestore), **Room Local Cache**, **Google Gemini AI**, and a **Real-Time Bank SMS Automation Engine**. 

Specifically engineered to resolve real-world financial tracking friction based on the scenario of Mr. Kavindu Silva.

Developed for **SE3092: Platform Based Development (Assignment 01)**  
Faculty of Computing — Sri Lanka Institute of Information Technology (SLIIT)  
Year 3, Semester 2 (2026)

---

## 👥 Group Members

| Student Name | Student ID |
|---|---|
| **K A M K Kaluarachchi** | `IT23619258` |
| **G P W Jayasekara** | `IT23750838` |
| **I N Hewawitharana** | `IT23585126` |

---

## 📌 Problem Scenario & System Vision

Traditional budgeting applications fail users like Kavindu Silva—a 25-year-old junior software engineer in Colombo earning variable income across four channels, spending impulsively across fragmented accounts, and struggling to save for a **LKR 490,000.00 MacBook Pro M4** despite earning over LKR 1.6M annually.

Four previous tools failed him (Google Sheets, Play Store USD-only apps, Notes app, and Bank summaries) because they were rigid, punished missed days, dropped cash/secondary accounts, and decoupled daily spending from his goal. 

**NEXUS** inverts these assumptions through core innovations:
1. **Live Bank SMS Automation Engine (Zero-Friction Tracking)**: Automatically captures bank debit (expense) and credit (income) SMS alerts from Sri Lankan banks with heads-up lock screen notifications and a 1-tap review sheet.
2. **Multi-Source Income Capture & Reconciliation (FR3–FR8)**: Replaces four-app mental calculations with validated, per-source income events (Salary, Freelance, AdSense USD, Crypto P&L).
3. **Low-Friction Expense Logging (FR9–FR13)**: 3-tap quick-add flow with aggressive defaults (date, last-used account, category) and committed-versus-discretionary tagging.
4. **Live Quantified Savings Goal (FR14–FR16)**: Translates the abstract MacBook goal into a live required monthly savings figure (~LKR 39,900/month) with trailing pace projections.
5. **AI Financial Assistant (Google Gemini AI)**: Context-aware conversational financial advisor offering spending leak analysis, savings forecasting, and intelligent budgeting recommendations.
6. **Self-Awareness Dashboard & Monthly Reports (FR17–FR20)**: Real-time visual comparison of confirmed income vs. expenses, committed vs. discretionary ratios, and ranked leak categories.

---

## 🌟 Key Application Capabilities & Innovations

### 📱 1. Real-Time Bank SMS Automation Engine
- **Outbound Expense Capture**: Parses card purchases, POS debits, bill payments, and cash withdrawals from major Sri Lankan banks (**Commercial Bank, Sampath Bank, HNB, BOC, People's Bank, Seylan Bank, NTB/FriMi, DFCC, Cargills, Pan Asia**).
- **Inbound Income Capture**: Identifies salary credits, freelance transfers (Upwork, Fiverr), CEFT remittances, and general deposits.
- **Lock Screen & Killed App Resiliency**: Uses an Android `BroadcastReceiver` combined with a native SMS inbox catch-up scanner (`BankSmsInboxScanner`) ensuring alerts arriving while the phone is locked or the app is killed are never lost.
- **Deterministic Deduplication**: Employs content-based hashing to prevent duplicate alerts between background broadcast delivery and catch-up queries.
- **Unreviewed Alerts Bottom Sheet**: Displays detected transactions in a clean Material 3 modal sheet with **"Accept & Categorize"** or **"Reject"** actions and badge counts on both Income and Expenses screens.
- **User Privacy & Control**: Backed by a dedicated **"Auto-Detect Bank SMS"** toggle switch in Settings with timestamp anchoring so SMS received while disabled are never retroactively scanned.

### 🌐 2. Real-Time Exchange Rate API
- Live currency exchange rate fetching for multi-currency incomes (USD, EUR, GBP to LKR).
- Guarantees historical transactions retain their exact converted rate while new entries reflect real-time market rates.

### 🔒 3. Forgot Password, Biometric Security Vault & Screen Privacy
- **Forgot Password Workflow**: Firebase Auth secure password reset email flow.
- **Biometric App Lock**: Fingerprint and Face Unlock support via Android `BiometricPrompt` with secure PIN fallback.
- **Privacy Screen Blurring**: Obscures sensitive financial balances and screen contents whenever the user switches away from the app.

### 🔔 4. Smart Notification System
- Lock-screen heads-up notifications for real-time detected bank transactions.
- Automated daily and weekly reminders for pending bill schedules and review alerts.

### 🤖 5. Google Gemini AI Financial Advisor
- Floating interactive chatbot with custom 3D robot avatar accessible directly from the home dashboard.
- Analyzes actual user spending data to identify hidden financial leaks.
- Answers natural language questions regarding budget feasibility and goal completion.
- Configurable Gemini API Key and model selection in App Settings.

### 🎨 6. App Preferences & Personalization Engine
- **Material Design 3 Dynamic Theming**: Follows System appearance or user-selected **Dark** / **Light** mode.
- **Base Currency Selection**: Choose between LKR, USD, EUR, and GBP.
- **Default Expense Account Preference**: Customize whether the 3-tap quick-add defaults to **Card**, **Cash**, or **Secondary**.
- **SMS Detection Toggle**: Dedicated switch to enable/disable automated bank SMS detection with time-anchored scanning.
- **Cache Management**: One-tap Room cache clearing and Firestore resync.

### 📊 7. Dedicated Monthly Intelligence & Reports Screen
- In-depth monthly financial reports with cash flow breakdown and visual spending charts.
- Historical trend analytics comparing income versus lifestyle expenses.
- Visual leak radar categorizing discretionary expenses.

### 🎯 8. Goal Contribution Tracking
- Manual and automated contribution allocations toward the MacBook Pro M4 savings fund.
- Unspent monthly surplus rollover directly credited toward goal milestones.

---

## 🏛️ Architecture Overview

The system strictly follows **MVVM (Model-View-ViewModel)** with **Unidirectional Data Flow (UDF)** and **Clean Architecture**:

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                                          UI LAYER                                           │
│   DashboardScreen │ IncomeScreen │ ExpenseScreen │ GoalScreen │ ReportsScreen               │
│   ChatBotScreen   │ SettingsScreen │ CategoriesScreen │ RecurringScreen │ AuthScreen        │
└──────────────────────────────────────────────▲──────────────────────────────────────────────┘
                                               │ UI State (StateFlow)
                                               │ User Actions (Events)
┌──────────────────────────────────────────────▼─────────────────────────────────────────────┐
│                                       VIEWMODEL LAYER                                       │
│   DashboardViewModel │ IncomeViewModel │ ExpenseViewModel │ GoalViewModel                  │
│   ReportsViewModel   │ SettingsViewModel │ ChatViewModel  │ AuthViewModel                  │
└──────────────────────────────────────────────▲─────────────────────────────────────────────┘
                                               │ Domain Models (Kotlin Flow)
┌──────────────────────────────────────────────▼─────────────────────────────────────────────┐
│                                      REPOSITORY LAYER                                       │
│   AuthRepository │ IncomeRepository │ ExpenseRepository │ GoalRepository                    │
│   CategoryRepository │ RecurringRepository │ BankExpenseManager │ BankDepositManager        │
└──────────────────────────────────────────────▲─────────────────────────────────────────────┘
                                               │
                       ┌───────────────────────┴───────────────────────┐
                       │                                               │
┌──────────────────────▼──────────────────────┐ ┌──────────────────────▼──────────────────────┐
│         CLOUD FIRESTORE (System of Record)   │ │           ROOM DATABASE (Offline Cache)     │
│   users, incomes, expenses, goals,           │ │   Local SQLite tables mirroring Firestore   │
│   categories, recurringItems                 │ │   for cold-start offline accessibility     │
└─────────────────────────────────────────────┘ └─────────────────────────────────────────────┘
```

---

## 🗄️ Database Design (Cloud Firestore)

All documents are stored in **flat top-level collections linked by `userId`** for optimal querying and constant depth:

```
Cloud Firestore
├── users/{uid}               # User authentication profiles & preferences
├── incomes/{incomeId}        # Inbound income records (confirmed & pending)
├── expenses/{expenseId}      # Outbound expense logs (with account & classification)
├── goals/{goalId}            # Savings goals (target amount, opening balance, deadline)
├── categories/{categoryId}   # Dynamic user categories (with color, icon, merge target)
└── recurringItems/{itemId}   # Recurring payment commitments & automated schedules
```

---

## 📋 Comprehensive Requirements & Extended Capabilities Traceability (FR1–FR23, EXT1–EXT8)

| ID | Requirement Specification |
|---|---|
| **FR1** | 5-field registration (Name, Email, Phone normalized to +94, Password) & Firestore sync |
| **FR2** | Session persistence across app restarts with automatic auth restoration |
| **FR3** | Multi-source income logging (Salary, Freelance, AdSense, Crypto) |
| **FR4** | Multi-currency handling storing original, exchange rate, and converted LKR |
| **FR5** | Monthly income computed strictly from confirmed entries (never self-estimates) |
| **FR6** | Crypto tracked as running net P&L (gains minus losses) |
| **FR7** | Edit saved income entries with recalculation and audit trail |
| **FR8** | Freelance payments logged as "pending" and excluded from earned totals until "confirmed" |
| **FR9** | 3-tap rapid expense entry capturing amount, category, account, and committed flag |
| **FR10** | Pre-fill category and account with most recently used values |
| **FR11** | First-class recording of Cash and Secondary accounts (not card only) |
| **FR12** | Dynamic user-editable categories with batched merge and reassignment |
| **FR13** | Back-dated and bulk-add expense entry without streak penalties |
| **FR14** | Quantified savings goal setup (MacBook Pro M4 LKR 490k, LKR 11.2k opening savings) |
| **FR15** | Dynamic recalculation of required monthly savings rate on every confirmed entry |
| **FR16** | Pace-based projected completion date flagging target deadline misses |
| **FR17** | Real-time visual goal progress bar (percentage complete and remaining balance) |
| **FR18** | Dashboard showing monthly cash flow and committed vs. discretionary ratio |
| **FR19** | Real-time category-wise spending chart highlighting leaks |
| **FR20** | Reconciled actual monthly income aggregated across all four sources |
| **FR21** | Room offline caching ensuring core screens render without internet connectivity |
| **FR22** | Explicit empty-state UI with zero crashes or blank screens on empty datasets |
| **FR23** | Recurring payment schedules requiring user confirmation before posting as an expense |
| **EXT1** | **Automatic Bank SMS Automation Engine** (Inbound & Outbound SMS Parsing, Lock screen catch-up scanner) |
| **EXT2** | **Real-Time Exchange Rate API** (Live USD/LKR currency conversion) |
| **EXT3** | **Forgot Password, Biometric Security Vault & Screen Privacy** (Fingerprint/PIN/Blur) |
| **EXT4** | **Smart Notifications System** (Heads-up lock screen alerts & reminders) |
| **EXT5** | **Google Gemini AI Financial Advisor** (Interactive 3D Chatbot & reasoning engine) |
| **EXT6** | **App Preferences & Personalization Engine** (Themes, Currency, Default Account, SMS toggle, Cache) |
| **EXT7** | **Dedicated Monthly Intelligence & Reports Screen** (Historical cash flow & analytics) |
| **EXT8** | **Goal Contribution Tracking** (Surplus rollover & contribution allocations) |

---

## 🗂️ Scope Division & Member Responsibilities

| Student Name & ID | Assigned Module & Scope | Key Responsibilities |
|---|---|---|
| **K A M K Kaluarachchi**<br>`IT23619258` | Authentication, Multi-Source Income System & SMS Automation | • Firebase Authentication setup, security rules & user profile (`users/{uid}`)<br>• Forgot Password recovery workflow<br>• 4-channel income logging (Salary, Freelance pending/confirmed, AdSense USD, Crypto net P&L)<br>• Real-Time Exchange Rate API (Live USD/LKR currency conversion)<br>• Income Overview & monthly reconciliation<br>• Automatic Bank SMS Automation Engine (Debit & Credit SMS regex parsing, background `BroadcastReceiver`, native inbox catch-up scanner, and unreviewed alerts bottom sheet)<br>• Biometric Security Vault & Screen Privacy (Fingerprint/Face unlock, PIN fallback, background screen obscuring)<br>• Smart Notifications System (Lock-screen heads-up transaction alerts & scheduled reminders) |
| **G P W Jayasekara**<br>`IT23750838` | Low-Friction Expense, Schedule Management & AI Integration | • 3-tap quick-add expense flow with pre-filled defaults & keypad<br>• Multi-account recording (Card, Cash, and Secondary accounts)<br>• Committed vs. Discretionary expense classification<br>• Dynamic Categories management (create, custom icon/color, rename, batched merge)<br>• Recurring payments schedule (Rent, Gym, Subscriptions) with user confirmation before posting<br>• Room Database offline-first caching & Firestore synchronization<br>• Google Gemini AI Financial Advisor (Interactive conversational chatbot with custom 3D avatar & financial reasoning)<br>• App Preferences & Personalization Engine (Material 3 themes, Base Currency, SMS toggle, Default Account, Cache clearing) |
| **I N Hewawitharana**<br>`IT23585126` | Quantified Savings Goal, Real-Time Analytics & Reports | • App navigation scaffold (Bottom Bar, Top Bar, FAB) & theme architecture<br>• MacBook Pro M4 quantified goal tracking (LKR 490,000.00 target, LKR 11,200.00 opening balance)<br>• Dynamic recalculation of required monthly savings rate on every confirmed entry<br>• Trailing pace projection & target deadline miss flagging<br>• Real-time Material 3 home dashboard (Cash flow, committed vs. discretionary ratios, goal progress bar)<br>• Real-time category-wise spending charts & leak identification<br>• Dedicated Monthly Intelligence & Reports Screen (Historical cash flow analysis, visual spending leak radar, category trends)<br>• Goal Contribution Tracking (Monthly surplus rollover & dedicated savings contribution allocations) |

---

## 🛠️ Technology Stack

| Layer / Concern | Technology Used | Version / Configuration |
|---|---|---|
| **Language** | Kotlin | 100% Kotlin (Zero Java), Kotlin DSL (`build.gradle.kts`) |
| **UI Framework** | Jetpack Compose | Material Design 3, Single-Activity architecture |
| **Architecture** | MVVM + Clean Architecture | Unidirectional Data Flow, StateFlow, Coroutines |
| **Dependency Injection** | Hilt (Dagger) | Factory and constructor injection |
| **Database (Cloud)** | Firebase Cloud Firestore | 6 top-level collections, user-isolated security rules |
| **Database (Local)** | Android Room (SQLite) | Offline-first synchronization |
| **Authentication** | Firebase Authentication | Email/Password provider with session restoration & reset |
| **AI Integration** | Google Gemini API | Generative AI financial assistant |
| **SMS Processing** | Android Telephony API | `BroadcastReceiver` + `content://sms/inbox` ContentProvider |
| **Security** | Android Biometrics API | `BiometricPrompt` + Keystore encryption |
| **Target SDKs** | Android SDK | `minSdk = 26` (Android 8.0 Oreo), `targetSdk = 36`, `compileSdk = 37` |

---

## 🚀 Setup & Build Instructions

### Prerequisites
1. **Android Studio** Ladybug (2024.2+) or newer
2. **JDK 17 or JDK 21**
3. **Android Device or Emulator** running API 26+ (Android 8.0 Oreo or higher)

### Installation & Run
1. **Clone the repository:**
   ```bash
   git clone https://github.com/Kavindi-K/NEXUS-Finance_Tracker.git
   cd NEXUS-Finance_Tracker
   ```

2. **Firebase Configuration:**
   - Place your `google-services.json` inside the `app/` folder (Package name: `com.nexus.financetracker`).
   - In Firebase Console:
     - Enable **Firebase Authentication** (Email/Password).
     - Initialize **Cloud Firestore** and deploy the security rules from `firestore.rules`.

3. **Build the Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Run Unit Tests:**
   ```bash
   ./gradlew testDebugUnitTest
   ```

5. **Install on Device/Emulator:**
   ```bash
   ./gradlew installDebug
   ```

---

## 🌿 Git Branching Strategy & Parallel Workflow

The development follows an integration branch model to enable clean parallel work:

```
main (Production / Stable Release APK)
  └── development (Shared Team Integration Branch)
        ├── feature/auth-*
        ├── feature/income-*
        ├── feature/expense-*
        ├── feature/categories-recurring
        ├── feature/offline-cache
        ├── feature/goal-*
        ├── feature/dashboard-analytics
        ├── feature/ai-chatbot
        └── feature/bank-sms-settings-toggle
```

---

## 📄 License & Academic Integrity

This project is developed solely for academic evaluation under **SE3092: Platform Based Development** at the **Sri Lanka Institute of Information Technology (SLIIT)**. All rights reserved.
