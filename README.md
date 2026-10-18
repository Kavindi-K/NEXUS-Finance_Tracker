# NEXUS — Personal Finance Management System

A scenario-driven Android personal finance application built with **Kotlin (100%)**, **Jetpack Compose (Material Design 3)**, **MVVM Architecture**, and **Firebase** (Authentication & Cloud Firestore), engineered to solve real-world financial tracking friction based on the scenario of Mr. Kavindu Silva.

Developed for **SE3092: Platform Based Development (Assignment 01)**  
Faculty of Computing — Sri Lanka Institute of Information Technology (SLIIT)  
Year 3, Semester 2 (2026)

---

## 👥 Group Members & Module Allocation

| Student ID | Student Name | Role | Primary Modules & Responsibilities |
|---|---|---|---|
| **IT23619258** | **K A M K Kaluarachchi** | **Member 1** | **Authentication, Security & Income Management**: Firebase setup, Security Rules, User Profile, Multi-source Income (Salary, Freelance, AdSense USD, Crypto P&L), Currency Conversion, Income Overview & Reconciliation |
| **IT23750838** | **G P W Jayasekara (Pavan)** | **Member 2** | **Expense Tracking & Offline Persistence**: Low-friction quick add (3 taps or fewer), Card/Cash/Secondary accounts, Committed vs Discretionary classification, Categories (add/rename/merge), Recurring items schedule, Room offline caching |
| **IT23585126** | **I N Hewawitharana (Ishara)** | **Member 3** | **Savings Goals, Analytics & Dashboard**: App navigation scaffold (Bottom Bar & Top Bar), MacBook Pro M4 quantified goal tracking, dynamic required monthly savings rate, pace projection, real-time Material 3 dashboard & charts |

---

## 📌 Project Overview & Scenario Translation

Standard budgeting tools fail young professionals with irregular income and fragmented spending. **NEXUS** directly addresses the failure patterns identified in Kavindu Silva’s scenario:

1. **Irregular Multi-Source Income Capture (FR3–FR8)**:
   * **Salary**: Pre-filled actual deposit capture to remove reliance on uninspected payslips.
   * **Freelance**: Milestone-based tracking with **Pending** vs. **Confirmed** invoice states to eliminate forgotten follow-ups.
   * **AdSense (USD)**: Multi-currency logging storing original USD, exchange rate, and converted LKR at the transaction date.
   * **Crypto (Binance)**: Signed gains and losses netting into a true running P&L rather than inflating earned income.
2. **Low-Friction Expense Logging (FR9–FR13)**:
   * 3-tap quick-add flow defaulting to today's date and last-used account (Card, Cash, Secondary).
   * First-class classification into **Committed** (Rent LKR 34k, utilities, gym) vs. **Discretionary** (revealing the LKR 24k coffee-and-dining leak).
3. **Quantified Savings Goal (FR14–FR16)**:
   * Live MacBook Pro M4 tracker (Target: LKR 490,000, Horizon: 12 months, Starting: LKR 11,200).
   * Dynamically recalculated required monthly savings: $\frac{\text{target} - \text{estimated savings}}{\text{months remaining}}$ (starting at ~LKR 39,900/mo).
   * Pace-based projection flagging whether the goal is on track, ahead, or behind.
4. **Real-Time Dashboard & Categorical Insights (FR17–FR20)**:
   * Reconciled actual monthly earnings displayed alongside the user's unvalidated estimate.
   * Visual committed vs. discretionary spending ratio and ranked spending category bars.

---

## 🛠️ Technology Stack & Architectural Standards

| Layer / Concern | Required Technology | Implementation Details |
|---|---|---|
| **Language** | Kotlin (100%) | Zero Java interoperability in application code; Gradle Kotlin DSL (`.kts`) |
| **UI Toolkit** | Jetpack Compose | Material Design 3 components, Single-Activity architecture, zero XML layouts |
| **Architecture** | Clean MVVM | Strict unidirectional data flow: UI $\rightarrow$ ViewModel $\rightarrow$ Repository $\rightarrow$ Data Sources |
| **State Management** | StateFlow / Coroutines | Sealed `UiState` (Loading, Empty, Success, Error); no mutable state leaked to UI |
| **Dependency Injection** | Hilt (Dagger) | Constructor injection for ViewModels and Repositories |
| **Backend & Auth** | Firebase Authentication | Email/password authentication, session persistence, user profile synchronization |
| **Primary Database** | Cloud Firestore | 6 flat top-level collections, composite query indexes, strict user-isolated rules |
| **Local Cache** | Room Database | Offline-first persistence; UI never blocks when network is unavailable |
| **Compatibility** | Android SDK | `minSdk = 26` (Android 8.0 Oreo), `targetSdk = 34+` (Android 14) |

---

## 🗄️ Firestore Database Architecture (6 Flat Collections)

As specified in Section 5.1 of the Technical Design Document, NEXUS uses **six flat, top-level collections** keyed by `userId`:

```
Cloud Firestore
├── users/{uid}               # Profile: displayName, email, phoneNumber, createdAt
├── incomes/{incomeId}        # source, status, amountOriginal, currency, exchangeRate, amountLKR, dates
├── expenses/{expenseId}      # amountLKR, categoryId, account, classification, date, recurringItemId
├── goals/{goalId}            # title, targetAmountLKR, openingSavingsLKR, startDate, targetDate, isActive
├── categories/{categoryId}   # name, mergingInto (for batched migration), createdAt
└── recurringItems/{itemId}   # title, amountLKR, categoryId, account, frequency, nextDueDate, isActive
```

---

## 🌿 Git Branching Strategy & Team Workflow

To ensure seamless parallel work and high code quality across all 3 members:

```
main (Production / Stable Release APK)
  └── development (Team Integration Branch)
        ├── feature/firestore-setup
        ├── feature/firestore-security
        ├── feature/auth-registration
        ├── feature/income-*
        ├── feature/expense-*
        └── feature/goal-*
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

---

---

## 🚀 Setup & Build Instructions

### Prerequisites
1. **Android Studio** (Ladybug 2024.2+ or Meerkat)
2. **JDK 17 or JDK 21** configured in Android Studio Gradle settings
3. **Android Device or Emulator** running API Level 26+ (Android 8.0 Oreo or higher)

### Installation & Run
1. **Clone the repository:**
   ```bash
   git clone https://github.com/Kavindi-K/NEXUS-Finance_Tracker.git
   cd NEXUS-Finance_Tracker
   ```
2. **Firebase Setup:**
   * Ensure `google-services.json` is located in the `app/` directory.
   * In Firebase Console:
     * Enable **Firebase Authentication** with Email/Password provider.
     * Create **Cloud Firestore** in production mode.
     * Publish the security rules defined in `firestore.rules`.
3. **Compile and Build Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
4. **Run Unit Tests:**
   ```bash
   ./gradlew testDebugUnitTest
   ```
5. **Install on Connected Device/Emulator:**
   ```bash
   ./gradlew installDebug
   ```
