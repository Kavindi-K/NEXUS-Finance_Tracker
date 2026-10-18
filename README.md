# NEXUS — Personal Finance Management System

A problem-first, scenario-driven Android personal finance application built with **Kotlin (100%)**, **Jetpack Compose (Material Design 3)**, **MVVM Architecture**, **Firebase** (Authentication & Cloud Firestore), and **Room Local Cache**, specifically engineered to resolve real-world financial tracking friction based on the scenario of Mr. Kavindu Silva.

Developed for **SE3092: Platform Based Development (Assignment 01)**  
Faculty of Computing — Sri Lanka Institute of Information Technology (SLIIT)  
Year 3, Semester 2 (2026)

---

## 👥 Group Members & Scope Division

| Student ID | Student Name | Assigned Module & Scope | Key Responsibilities |
|---|---|---|---|
| **IT23619258** | **K A M K Kaluarachchi** | **Member 1**: Authentication & Multi-Source Income System | Firebase Auth setup, security rules, user profile (`users/{uid}`), 4 income streams (Salary, Freelance pending/confirmed, AdSense USD currency conversion, Crypto running net P&L), Income Overview & monthly reconciliation. |
| **IT23750838** | **G P W Jayasekara** | **Member 2**: Low-Friction Expense & Schedule Management | 3-tap quick-add expense flow, Card/Cash/Secondary accounts, Committed vs. Discretionary classification, dynamic Categories (add, rename, batched merge), Recurring payments schedule (Rent, Gym, Subscriptions), Room offline caching. |
| **IT23585126** | **I N Hewawitharana** | **Member 3**: Quantified Savings Goal & Real-Time Analytics | App navigation scaffold (Bottom Bar, Top Bar, FAB), MacBook Pro M4 quantified goal tracking, dynamic required monthly savings rate recalculation, trailing pace projection, real-time Material 3 dashboard, and category spending charts. |

---

## 📌 Problem Scenario & System Vision

Traditional budgeting applications fail users like Kavindu Silva—a 25-year-old junior software engineer in Colombo earning variable income across four channels, spending impulsively across fragmented accounts, and struggling to save for a **LKR 490,000.00 MacBook Pro M4** despite earning over LKR 1.6M annually.

Four previous tools failed him (Google Sheets, Play Store USD-only apps, Notes app, and Bank summaries) because they were rigid, punished missed days, dropped cash/secondary accounts, and decoupled daily spending from his goal. 

**NEXUS** inverts these assumptions through four core capabilities:
1. **Multi-Source Income Capture & Reconciliation (FR3–FR8)**: Replaces four-app mental calculations with validated, per-source income events.
2. **Low-Friction Expense Logging (FR9–FR13)**: 3-tap quick-add flow with aggressive defaults (date, last-used account, category) and committed-versus-discretionary tagging.
3. **Live Quantified Savings Goal (FR14–FR16)**: Translates the abstract MacBook goal into a live required monthly savings figure (~LKR 39,900/month) with trailing pace projections.
4. **Week-One Self-Awareness Dashboard (FR17–FR20)**: Real-time visual comparison of confirmed income vs. expenses, committed vs. discretionary ratios, and ranked leak categories (exposing the LKR 24,000 coffee/dining shock).

---

## 🏛️ MVVM Architecture Overview

The system strictly adheres to **unidirectional data flow** with four distinct layers: **UI $\rightarrow$ ViewModel $\rightarrow$ Repository $\rightarrow$ Data Sources**, with shared domain models:

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                                          UI LAYER                                           │
│   DashboardScreen │ IncomeOverviewScreen │ QuickAddExpenseScreen │ GoalSetupScreen          │
│                   CategoriesRecurringScreen │ AuthScreen (Login & Register)                 │
└──────────────────────────────────────────────▲──────────────────────────────────────────────┘
                                               │ UI State (StateFlow)
                                               │ User Actions (Events)
┌──────────────────────────────────────────────▼─────────────────────────────────────────────┐
│                                       VIEWMODEL LAYER                                       │
│   DashboardViewModel │ IncomeViewModel │ ExpenseViewModel │ GoalViewModel                  │
│                      ManageViewModel   │ AuthViewModel                                      │
└──────────────────────────────────────────────▲──────────────────────────────────────────────┘
                                               │ Domain Models (Kotlin Flow)
┌──────────────────────────────────────────────▼─────────────────────────────────────────────┐
│                                      REPOSITORY LAYER                                       │
│   AuthRepository │ IncomeRepository │ ExpenseRepository │ GoalRepository                    │
│                  CategoryRepository │ RecurringItemRepository                               │
└──────────────────────────────────────────────▲──────────────────────────────────────────────┘
                                               │
                       ┌───────────────────────┴───────────────────────┐
                       │                                               │
┌──────────────────────▼──────────────────────┐ ┌──────────────────────▼──────────────────────┐
│         CLOUD FIRESTORE (System of Record)   │ │           ROOM DATABASE (Offline Cache)     │
│   users, incomes, expenses, goals,           │ │   Local SQLite tables mirroring Firestore   │
│   categories, recurringItems                 │ │   for cold-start offline accessibility     │
└─────────────────────────────────────────────┘ └─────────────────────────────────────────────┘
```

### 1. Six UI Screens & ViewModels
* **`DashboardScreen` (`DashboardViewModel`)**: Combines income, expenses, goal progress, and category names in a single atomic `combine()` flow.
* **`IncomeOverviewScreen` (`IncomeViewModel`)**: Shows earned vs. pending income, multi-currency conversions, and crypto net P&L.
* **`QuickAddExpenseScreen` (`ExpenseViewModel`)**: 3-tap rapid input with keypad and pre-filled defaults.
* **`GoalSetupScreen` (`GoalViewModel`)**: MacBook fund configuration, dynamic required savings, and pace calculations.
* **`CategoriesRecurringScreen` (`ManageViewModel`)**: Category renaming, batched merging, and subscription payment confirmations.
* **`AuthScreen` (`AuthViewModel`)**: Clean email/password sign-in and validated 5-field registration (+94 phone normalization).

### 2. Six Domain Models
* **`UserProfile`**: `uid`, `displayName`, `email`, `phoneNumber`, `createdAt`
* **`Income`**: `id`, `userId`, `source` (salary/freelance/adsense/crypto), `status` (confirmed/pending), `amountOriginal`, `currency`, `exchangeRate`, `amountLKR`, `dateReceived`, `expectedDate`, `note`, `createdAt`, `updatedAt`
* **`Expense`**: `id`, `userId`, `amountLKR`, `categoryId`, `account` (card/cash/secondary), `classification` (committed/discretionary), `note`, `date`, `recurringItemId`, `createdAt`
* **`Goal`**: `id`, `userId`, `title`, `targetAmountLKR`, `openingSavingsLKR`, `startDate`, `targetDate`, `isActive`, `createdAt`
* **`Category`**: `id`, `userId`, `name`, `mergingInto`, `createdAt`
* **`RecurringItem`**: `id`, `userId`, `title`, `amountLKR`, `categoryId`, `account`, `classification`, `frequency` (weekly/monthly/yearly), `nextDueDate`, `isActive`, `createdAt`

---

## 🗄️ Firestore Database Design (6 Top-Level Collections)

To optimize query indexing, maintain constant repository path depth, and avoid subcollection query limitations, NEXUS organizes all data into **six flat collections linked by `userId`**:

```
Cloud Firestore
├── users/{uid}               # User profiles (created on registration)
├── incomes/{incomeId}        # All income events (confirmed & pending)
├── expenses/{expenseId}      # Direct expense logs (LKR base currency)
├── goals/{goalId}            # Savings goals (single active goal displayed)
├── categories/{categoryId}   # Dynamic user categories (with mergingInto marker)
└── recurringItems/{itemId}   # Recurring payment commitments & schedules
```

### Multi-Currency & Financial Accuracy (NFR9)
* **AdSense USD**: Both original USD and prevailing exchange rate are stored alongside the converted LKR (`amountLKR = amountOriginal × exchangeRate`). Historical reports never fluctuate when rates change.
* **Crypto P&L (FR6)**: Crypto entries record signed gains and losses (negative numbers for closed positions at a loss), ensuring net positions are accurate without inflating earned income.

---

## 📋 Comprehensive Requirements Traceability (FR1–FR23)

| ID | Requirement Specification | Module Owner | Implementation Status |
|---|---|---|:---:|
| **FR1** | 5-field registration (Name, Email, Phone normalized to +94, Password) & Firestore profile sync | Member 1 | ✅ Implemented |
| **FR2** | Session persistence across app restarts | Member 1 | ✅ Implemented |
| **FR3** | Multi-source income logging (Salary, Freelance, AdSense, Crypto) | Member 1 | ✅ Implemented |
| **FR4** | Multi-currency handling storing original, exchange rate, and converted LKR | Member 1 | ✅ Implemented |
| **FR5** | Monthly income computed strictly from confirmed entries (never self-estimates) | Member 1 | ✅ Implemented |
| **FR6** | Crypto tracked as running net P&L (gains minus losses) | Member 1 | ✅ Implemented |
| **FR7** | Edit saved income entries with recalculation and audit trail | Member 1 | ✅ Implemented |
| **FR8** | Freelance payments logged as "pending" and excluded from earned totals until "confirmed" | Member 1 | ✅ Implemented |
| **FR9** | 3-tap rapid expense entry capturing amount, category, account, and committed flag | Member 2 | 🔄 In Progress |
| **FR10** | Pre-fill category and account with most recently used values | Member 2 | 🔄 In Progress |
| **FR11** | First-class recording of Cash and Secondary accounts (not card only) | Member 2 | 🔄 In Progress |
| **FR12** | Dynamic user-editable categories with batched merge and reassignment | Member 2 | 🔄 In Progress |
| **FR13** | Back-dated and bulk-add expense entry without streak penalties | Member 2 | 🔄 In Progress |
| **FR14** | Quantified savings goal setup (MacBook Pro M4 LKR 490k, LKR 11.2k opening savings) | Member 3 | 🔄 In Progress |
| **FR15** | Dynamic recalculation of required monthly savings rate on every confirmed entry | Member 3 | 🔄 In Progress |
| **FR16** | Pace-based projected completion date flagging target deadline misses | Member 3 | 🔄 In Progress |
| **FR17** | Real-time visual goal progress bar (percentage complete and remaining balance) | Member 3 | 🔄 In Progress |
| **FR18** | Dashboard showing monthly cash flow and committed vs. discretionary ratio | Member 3 | 🔄 In Progress |
| **FR19** | Real-time category-wise spending chart highlighting leaks | Member 3 | 🔄 In Progress |
| **FR20** | Reconciled actual monthly income aggregated across all four sources | Member 3 | 🔄 In Progress |
| **FR21** | Room offline caching ensuring core screens render without internet connectivity | Member 2 | 🔄 In Progress |
| **FR22** | Explicit empty-state UI with zero crashes or blank screens on empty datasets | All Members | ✅ Implemented |
| **FR23** | Recurring payment schedules requiring user confirmation before posting as an expense | Member 2 | 🔄 In Progress |

---

## 🛠️ Technology Stack & Compliance

| Layer / Concern | Required Technology | Compliance in Project |
|---|---|---|
| **Language** | Kotlin (100%) | Zero Java files in application layer; Kotlin DSL (`build.gradle.kts`) |
| **UI Framework** | Jetpack Compose | Material Design 3 components, Single-Activity architecture, zero XML layouts |
| **Architecture** | MVVM | Strict separation: UI $\rightarrow$ ViewModel $\rightarrow$ Repository $\rightarrow$ Model |
| **State Management** | StateFlow / Coroutines | Sealed `UiState` (Loading, Empty, Success, Error); no mutable state leaked to UI |
| **Dependency Injection** | Hilt (Dagger) | Factory and constructor injection; no manual ViewModel instantiation |
| **Database** | Firebase Firestore | 6 flat collections, real-time snapshot listeners, user-isolated security rules |
| **Local Caching** | Room Database | Offline-first persistence; UI never blocked on network failure |
| **Authentication** | Firebase Authentication | Email/password provider with session restoration |
| **Navigation** | Navigation Compose | Single-Activity architecture with typed routes |
| **SDK Compatibility** | Min SDK 26, Target SDK 34+ | Configured with `minSdk = 26`, `targetSdk = 36`, `compileSdk = 37` |

---

## 🌿 Git Branching Strategy & Parallel Workflow

The development follows an integration branch model to enable clean parallel work:

```
main (Production / Stable Release APK)
  └── development (Shared Team Integration Branch)
        ├── feature/firestore-setup (Member 1)
        ├── feature/firestore-security (Member 1)
        ├── feature/auth-* (Member 1)
        ├── feature/income-* (Member 1)
        ├── feature/expense-* (Member 2)
        ├── feature/categories-recurring (Member 2)
        ├── feature/offline-cache (Member 2)
        ├── feature/app-nav-scaffold (Member 3)
        ├── feature/goal-* (Member 3)
        └── feature/dashboard-analytics (Member 3)
```

### Git Collaboration Protocol:
1. **Never commit directly to `main` or `development`.**
2. Every feature is created from the latest `development` branch:
   ```bash
   git switch development
   git pull origin development
   git switch -c feature/<feature-name>
   ```
3. **Pull Request & Peer Review**:
   * Each feature must be submitted via a GitHub Pull Request to `development`.
   * Requires peer review and clean compilation (`./gradlew assembleDebug` and `./gradlew testDebugUnitTest`) before merging.
4. **Final Release**: Once all modules pass integration testing on `development`, a final release PR is merged into `main`.
5. `main` is protected and updated only for stable milestone releases.

---

## 🚀 Setup & Build Instructions

### Prerequisites
1. **Android Studio** Ladybug (2024.2+) or Meerkat
2. **JDK 17 or JDK 21**
3. **Android Device or Emulator** running API 26+ (Android 8.0 Oreo or higher)

### Installation & Run
1. **Clone the repository:**
   ```bash
   git clone https://github.com/Kavindi-K/NEXUS-Finance_Tracker.git
   cd NEXUS-Finance_Tracker
   ```
2. **Firebase Configuration:**
   * Verify `google-services.json` is placed inside the `app/` directory (Package: `com.nexus.financetracker`).
   * In Firebase Console:
     * Enable **Firebase Authentication** (Email/Password).
     * Set up **Cloud Firestore** and publish the rules defined in `firestore.rules`.
3. **Compile and Build Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
4. **Execute Unit Tests:**
   ```bash
   ./gradlew testDebugUnitTest
   ```
5. **Install on Device/Emulator:**
   ```bash
   ./gradlew installDebug
   ```
