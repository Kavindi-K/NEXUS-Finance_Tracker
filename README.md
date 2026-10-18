# Nexus Finance Tracker

A personal finance management Android application built with **Kotlin**, **Jetpack Compose**, **MVVM architecture**, and **Firebase**, designed to address real-world financial tracking friction based on the scenario of Mr. Kavindu Silva.

Developed for **SE3092: Platform Based Development (Assignment 01)**  
Faculty of Computing — Year 3, Semester 2 (2026)

---

## 👥 Group Members & Contribution Details

| Student ID | Student Name | Assigned Role & Module |
|---|---|---|
| **IT23585126** | **I N Hewawitharana** | **Member 1**: Auth, Core Navigation, & Low-Friction Expense Engine |
| **IT23619258** | **K A M K Kaluarachchi** | **Member 2**: Multi-Source Income & Multi-Currency Engine |
| **IT23750838** | **G P W Jayasekara** | **Member 3**: Savings Goal (MacBook Fund), Budgeting & Financial Analytics |

---

## 📌 Project Overview

Traditional budgeting tools fail users who have irregular multi-source income (salary, freelance, AdSense in USD, crypto) and fragmented, impulsive expenses (rides, delivery, casual dining, subscriptions). 

**Nexus Finance Tracker** specifically solves the four fundamental friction points identified in Kavindu Silva's scenario:
1. **Multi-Source Income Invisibility**: Aggregates disparate channels (salary, freelance milestones, USD AdSense, and cryptocurrency net position).
2. **Expense Invisibility & Friction**: Enables effortless quick-entry and categorises spending into **Committed** vs. **Discretionary** costs.
3. **The Unmoving Savings Goal**: Grounded financial planning for the **LKR 490,000 MacBook Pro M4** target within 12 months.
4. **Offline Resilience & Security**: Room database local cache backed by real-time Firebase Firestore synchronisation and user-scoped security rules.

---

## 📋 Comprehensive Scope Division Across 3 Members

The full assignment scope has been architecturally decoupled into three self-contained modules, allowing independent development while maintaining strict MVVM and clean architecture boundaries:

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           NEXUS FINANCE TRACKER SCOPE                           │
├─────────────────────────┬───────────────────────────┬───────────────────────────┤
│        MEMBER 1         │         MEMBER 2          │         MEMBER 3          │

├─────────────────────────┼───────────────────────────┼───────────────────────────┤
│ • Auth & User Profile   │ • Multi-Source Income     │ • Savings Goal Engine     │
│ • Navigation & Theming  │ • Multi-Currency (USD/LKR)│ • Pacing & Dynamic Rates  │
│ • Low-Friction Expenses │ • Freelance Invoices      │ • Committed/Discretionary │
│ • Subscriptions Detector│ • Crypto Running Position │ • Dashboard & Charts      │
└─────────────────────────┴───────────────────────────┴───────────────────────────┘
```

---

### 🔹 Member 1: IT23585126 – I N Hewawitharana
**Focus**: *Authentication, App Foundation & Low-Friction Expense Management*

#### 1. Core Architecture & Authentication
- **Firebase Authentication**: User registration, login, password reset, session persistence, and logout flow.
- **Root Navigation & Scaffold**: Single-activity `AppNavGraph`, type-safe routes, bottom navigation bar, and top app bar.
- **Design System & Theme**: Material Design 3 theming (dark/light palettes, typography, responsive padding).

#### 2. Expense Logging & Friction Reduction (Solving Kavindu's Expense Invisibility)
- **Fast Expense Entry**: Quick-input composable designed for 5-second entry (PickMe, UberEats, cash withdrawals, casual dining).
- **Spending Categorisation**: Classification into **Committed Expenses** (Rent LKR 34k, utilities, gym, debt) vs. **Discretionary Expenses** (Coffee LKR 24k shock, entertainment, food delivery).
- **Recurring Subscription Tracker**: Auto-detection and reminders for periodic debits (gym membership, SaaS subscriptions) to eliminate forgotten recurring charges.
- **Local & Remote Persistence**: Room `ExpenseDao`, Room Entity, and Firestore expense collection with sync status.

---

### 🔹 Member 2: IT23619258 – K A M K Kaluarachchi
**Focus**: *Multi-Source Income Architecture & Multi-Currency Engine*

#### 1. Irregular Income Stream Modeling (Solving Kavindu's Income Problem)
- **Salary Tracking**: Handles variable monthly compensation (LKR 120,000–140,000 base + performance component) arriving on the 25th with payslip notes.
- **Freelance Milestone Tracker**: Tracks individual React/WordPress client projects (LKR 20,000–90,000), outstanding invoices, and payment reconciliation to prevent forgotten billings.
- **Multi-Currency Engine (USD to LKR)**: Google AdSense revenue tracker with currency conversion at prevailing transaction rates (LKR 3,500–19,000/mo).
- **Crypto Investment Ledger**: Binance asset tracker (USDT, ETH, altcoins) calculating running net positions, recorded gains, and losses.

#### 2. Income Aggregation & Validation
- **Actual vs. Estimated Income Comparison**: Calculates true realized monthly earnings vs. estimated figures (addressing Kavindu's vague "160k–210k" estimate).
- **Local & Remote Persistence**: Room `IncomeDao`, Firestore multi-source income collections, and repository-level currency transformations.

---

### 🔹 Member 3: IT23750838 – G P W Jayasekara
**Focus**: *Savings Goals (MacBook Pro Fund), Budgeting & Financial Analytics Dashboard*

#### 1. Quantified Goal Progress Engine (Solving "The Goal That Does Not Move")
- **Target Goal Tracking**: Dedicated MacBook Pro M4 fund tracker (target: LKR 490,000.00 within 12 months, starting from LKR 11,200.00).
- **Dynamic Required Savings Rate**: Computes the exact monthly savings required based on elapsed time and actual net income.
- **Behavior-Linked Pacing**: Translates daily discretionary spending habits directly into goal timeline delays (making the invisible visible and motivating).

#### 2. Visual Analytics & Real-Time Dashboard
- **Financial Health Dashboard**: Real-time snapshot combining total net position, active month cash flow, and goal milestone bars.
- **Visual Analytics**: Interactive Material 3 charts showing:
  - **Committed vs. Discretionary Spending Ratio** (showing proportion of earnings consumed by fixed costs).
  - **Category Breakdown** (highlighting outlier spending such as LKR 24k coffee/dining).
- **Real-Time Integration**: Firestore real-time snapshot listeners with offline fallback to Room local database.

---

## 🛠️ Technology Stack & Compliance

| Layer / Concern | Required Technology | Compliance in Project |
|---|---|---|
| **Language** | Kotlin (100%) | Zero Java files in application layer; Kotlin DSL for Gradle scripts |
| **UI Framework** | Jetpack Compose | Material Design 3 components only; zero XML layouts |
| **Architecture** | MVVM | Strict separation: UI → ViewModel → Repository → Model |
| **State Management** | StateFlow / Coroutines | Exposes read-only `StateFlow`; zero raw mutable state in UI |
| **Dependency Injection** | Hilt (Dagger) | Factory and constructor injection; no manual ViewModel instantiation |
| **Database** | Firebase Firestore | Multi-currency modeling, real-time snapshot listeners, user-scoped rules |
| **Local Caching** | Room Database | Offline-first persistence; UI never blocked on network failure |
| **Authentication** | Firebase Authentication | Email/password provider (Google Sign-In ready) |
| **Navigation** | Navigation Compose | Single-Activity architecture with type-safe routes |
| **SDK Compatibility** | Min SDK 26, Target SDK 34+ | Configured with `minSdk = 26`, `targetSdk = 36`, `compileSdk = 37` |

---

## 📂 Package & File Structure

```
com.nexus.financetracker/
├── FinanceApp.kt                   # Application class (@HiltAndroidApp)
├── MainActivity.kt                 # Single Activity entry point
│
├── domain/                         # Domain Layer (Enterprise Business Rules)
│   └── model/
│       ├── Transaction.kt          # Income & Expense models + classifications
│       ├── Goal.kt                 # Savings goal model (MacBook fund)
│       └── Budget.kt               # Category budget model
│
├── data/                           # Data Layer (Repositories & Data Sources)
│   ├── local/                      # Room Offline Persistence
│   │   ├── database/AppDatabase.kt
│   │   ├── dao/                    # TransactionDao, GoalDao
│   │   └── entity/                 # TransactionEntity, GoalEntity
│   ├── remote/                     # Firebase Remote Data Sources
│   │   ├── TransactionRemoteDataSource.kt
│   │   └── GoalRemoteDataSource.kt
│   └── repository/                 # Repository Implementations
│       ├── TransactionRepository.kt & Impl.kt
│       └── GoalRepository.kt & Impl.kt
│
├── di/                             # Dependency Injection (Hilt Modules)
│   ├── DatabaseModule.kt           # Room instances injection
│   ├── FirebaseModule.kt           # FirebaseAuth & Firestore injection
│   └── RepositoryModule.kt         # Interface bindings injection
│
└── ui/                             # Presentation Layer (Jetpack Compose)
    ├── navigation/                 # AppNavGraph, Screen routes
    ├── theme/                      # Color, Theme, Typography (Material 3)
    ├── auth/                       # Member 1: LoginScreen, RegisterScreen, AuthViewModel
    ├── dashboard/                  # Member 3: DashboardScreen, DashboardViewModel
    └── goal/                       # Member 3: GoalListScreen, GoalViewModel
```

---

## 🚀 Setup & Build Instructions

### Prerequisites
1. **Android Studio** Ladybug / Meerkat (2024.2 or newer)
2. **JDK 21** (JetBrains Runtime recommended)
3. **Android SDK Platform 37** (Auto-downloaded by Gradle)

### Build Steps
1. **Clone the repository:**
   ```bash
   git clone https://github.com/Kavindi-K/NEXUS-Finance_Tracker.git
   cd NEXUS-Finance_Tracker
   ```
2. **Firebase Configuration:**
   - Place `google-services.json` inside the `app/` directory (package: `com.nexus.financetracker`).
   - In Firebase Console:
     - Enable **Email/Password** authentication.
     - Create a **Cloud Firestore** database.
     - Publish the rules defined in `firestore.rules`.
3. **Build the Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
4. **Run on Device or Emulator:**
   - Launch an emulator or connect a device running **Android 8.0 (API 26)** or higher.
   - Run the application via Android Studio or `./gradlew installDebug`.
