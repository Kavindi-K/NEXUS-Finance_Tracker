package com.nexus.financetracker.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexus.financetracker.R

private val BrandNavy = Color(0xFF072044)

/**
 * Register Screen.
 * Features top branded navy banner matching the splash screen
 * and an elevated white card with the registration form.
 */
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            viewModel.resetState()
            onRegisterSuccess()
        }
    }

    var fullName   by remember { mutableStateOf("") }
    var email      by remember { mutableStateOf("") }
    var phone      by remember { mutableStateOf("") }
    var password   by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandNavy)
    ) {
        // --- 1. Top Branded Navy Banner ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 24.dp, bottom = 32.dp, start = 28.dp, end = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.nexus_splash_logo),
                contentDescription = "Nexus Brand Logo",
                modifier = Modifier
                    .fillMaxWidth(0.85f),
                contentScale = ContentScale.FillWidth
            )
        }

        // --- 2. Bottom White Form Sheet ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(26.dp))

                Text(
                    text = "CREATE ACCOUNT",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        localError = null
                    },
                    label = { Text("Full Name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    enabled = !uiState.isLoading
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        localError = null
                    },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !uiState.isLoading
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        localError = null
                    },
                    label = { Text("Phone Number") },
                    placeholder = { Text("+94778654657, 0116754378, or 748796654") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    enabled = !uiState.isLoading
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        localError = null
                    },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !uiState.isLoading
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPwd,
                    onValueChange = {
                        confirmPwd = it
                        localError = null
                    },
                    label = { Text("Confirm Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !uiState.isLoading
                )
                Spacer(Modifier.height(8.dp))

                // Local validation errors take priority over server errors
                val displayError = localError ?: uiState.errorMessage
                displayError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        localError = viewModel.validateRegistration(
                            fullName, email, phone, password, confirmPwd
                        )
                        if (localError == null) {
                            viewModel.register(fullName, email, phone, password)
                        }
                    },
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandNavy,
                        contentColor = Color.White
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Creating account…", color = Color.White)
                    } else {
                        Text(
                            text = "Register",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                TextButton(
                    onClick = onNavigateToLogin,
                    enabled = !uiState.isLoading
                ) {
                    Text(
                        text = "Already have an account? Sign In",
                        color = BrandNavy,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
