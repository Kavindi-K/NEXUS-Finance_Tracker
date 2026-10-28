package com.nexus.financetracker.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.nexus.financetracker.R

private val BrandNavy = Color(0xFF072044)

/**
 * Sign In Screen.
 * Features top branded navy banner matching the splash screen
 * and an elevated white card with the sign-in form.
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState
    val context = LocalContext.current

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            viewModel.resetState()
            onLoginSuccess()
        }
    }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val webClientId = remember {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) context.getString(resId) else "859568003787-fq8pqgk0tcpkge8u7butjcf61qa4ba9q.apps.googleusercontent.com"
    }

    val gso = remember(webClientId) {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember(gso) { GoogleSignIn.getClient(context, gso) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (!idToken.isNullOrBlank()) {
                viewModel.signInWithGoogle(idToken)
            } else {
                localError = "Could not retrieve Google ID Token"
            }
        } catch (e: ApiException) {
            if (e.statusCode != 12501 && e.statusCode != GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                localError = "Google sign-in failed (${e.statusCode}): ${e.localizedMessage ?: "Unknown error"}"
            }
        } catch (e: Exception) {
            localError = e.localizedMessage ?: "Google sign-in failed"
        }
    }

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
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(28.dp))

                Text(
                    text = "SIGN IN",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(28.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        localError = null
                    },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !uiState.isLoading
                )
                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        localError = null
                    },
                    label = { Text("Password") },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val icon = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        val description = if (isPasswordVisible) "Hide password" else "Show password"
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(imageVector = icon, contentDescription = description)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !uiState.isLoading
                )
                Spacer(Modifier.height(4.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    TextButton(
                        onClick = onNavigateToForgotPassword,
                        enabled = !uiState.isLoading,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Forgot Password?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                // Display local validation error first; fallback to Firebase error
                val displayError = localError ?: uiState.errorMessage
                displayError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(6.dp))

                Button(
                    onClick = {
                        localError = viewModel.validateLogin(email, password)
                        if (localError == null) {
                            viewModel.login(email, password)
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
                        Text("Signing in…", color = Color.White)
                    } else {
                        Text(
                            text = "Sign In",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // --- OR Divider ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                    Text(
                        text = "  OR  ",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.SemiBold
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                }

                // --- Google Sign-In Button ---
                OutlinedButton(
                    onClick = {
                        localError = null
                        googleSignInClient.signOut().addOnCompleteListener {
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        }
                    },
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1E293B)
                    )
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_google_logo),
                        contentDescription = "Google Logo",
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Continue with Google",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B)
                    )
                }

                Spacer(Modifier.height(14.dp))

                TextButton(
                    onClick = onNavigateToRegister,
                    enabled = !uiState.isLoading
                ) {
                    Text(
                        text = "Don't have an account? Register",
                        color = BrandNavy,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
