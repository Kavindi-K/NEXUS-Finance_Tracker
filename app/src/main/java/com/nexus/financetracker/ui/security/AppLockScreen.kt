package com.nexus.financetracker.ui.security

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexus.financetracker.R
import com.nexus.financetracker.data.local.SecurityManager

private val LockNavyTop = Color(0xFF072044)
private val LockNavyBottom = Color(0xFF020B18)
private val KeypadBg = Color(0xFF132D56)

@Composable
fun AppLockScreen(
    securityManager: SecurityManager,
    viewModel: AppLockViewModel = hiltViewModel(),
    onUnlockSuccess: () -> Unit,
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // Navigate to dashboard when unlocked
    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) {
            onUnlockSuccess()
        }
    }

    // Trigger biometric prompt on open if enabled & available
    LaunchedEffect(Unit) {
        if (uiState.isBiometricAvailable && activity != null) {
            securityManager.authenticateWithBiometrics(
                activity = activity,
                onSuccess = {
                    viewModel.onBiometricSuccess()
                },
                onError = { /* Ignored; user can use PIN or re-tap biometric */ },
                onCancelOrUsePin = { /* User chose to use PIN keypad */ }
            )
        }
    }

    // Shake animation on incorrect PIN
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(uiState.shakeErrorKey) {
        if (uiState.shakeErrorKey > 0) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    -25f at 50
                    25f at 100
                    -20f at 150
                    20f at 200
                    -10f at 250
                    10f at 300
                    0f at 400
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(LockNavyTop, LockNavyBottom)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header & Branding
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "NEXUS Logo",
                    modifier = Modifier.height(42.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Security Unlock",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (uiState.isPinSet) {
                        "Enter your 4-digit PIN or use Biometrics"
                    } else {
                        "Unlock with Biometrics or device screen lock"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            }

            // PIN Dots (if PIN is set)
            if (uiState.isPinSet) {
                Row(
                    modifier = Modifier
                        .offset(x = shakeOffset.value.dp)
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < uiState.enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) Color.White else Color.Transparent)
                                .border(
                                    width = 2.dp,
                                    color = if (isFilled) Color.White else Color(0xFF475569),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                // Error message
                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage!!,
                        color = Color(0xFFF87171),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(28.dp))
                }

                // Numeric Keypad
                NumericKeypad(
                    onDigitClick = { viewModel.onDigit(it) },
                    onDeleteClick = { viewModel.onDelete() },
                    showBiometricButton = uiState.isBiometricAvailable,
                    onBiometricClick = {
                        activity?.let {
                            securityManager.authenticateWithBiometrics(
                                activity = it,
                                onSuccess = { viewModel.onBiometricSuccess() },
                                onError = { /* Dialog displayed */ }
                            )
                        }
                    }
                )
            } else {
                // If user hasn't set a custom 4-digit PIN, allow one-tap Biometric unlock
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = KeypadBg,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .clickable {
                                activity?.let {
                                    securityManager.authenticateWithBiometrics(
                                        activity = it,
                                        onSuccess = { viewModel.onBiometricSuccess() },
                                        onError = { /* Dialog error */ }
                                    )
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric Unlock",
                                tint = Color.White,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            activity?.let {
                                securityManager.authenticateWithBiometrics(
                                    activity = it,
                                    onSuccess = { viewModel.onBiometricSuccess() },
                                    onError = { /* Handled */ }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(50.dp)
                    ) {
                        Text(
                            text = "Tap to Unlock",
                            color = LockNavyTop,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Forgot PIN / Sign Out option
            TextButton(
                onClick = {
                    viewModel.signOut(onComplete = onSignOut)
                },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Sign Out of NEXUS",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun NumericKeypad(
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    showBiometricButton: Boolean,
    onBiometricClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(0.85f),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        )

        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (digit in row) {
                    KeypadButton(
                        text = digit,
                        onClick = { onDigitClick(digit) }
                    )
                }
            }
        }

        // Bottom row: Biometric / Empty, 0, Backspace
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBiometricButton) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBiometricClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Biometric prompt",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(72.dp))
            }

            KeypadButton(
                text = "0",
                onClick = { onDigitClick("0") }
            )

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDeleteClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Delete digit",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = KeypadBg.copy(alpha = 0.8f),
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
