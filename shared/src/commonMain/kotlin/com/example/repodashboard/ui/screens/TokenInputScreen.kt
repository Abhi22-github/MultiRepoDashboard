package com.example.repodashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repodashboard.ui.theme.FeatherIcons
import com.example.repodashboard.ui.theme.LocalAppColors
import com.example.repodashboard.ui.theme.LocalBodyFont
import com.example.repodashboard.ui.theme.LocalDisplayFont

@Composable
fun TokenInputScreen(
    onTokenSaved: (String) -> Unit,
    isLoading: Boolean = false,
    error: String? = null
) {
    val c       = LocalAppColors.current
    val body    = LocalBodyFont.current
    val display = LocalDisplayFont.current

    var token     by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                if (c.isDark) listOf(Color(0xFF0A1730), Color(0xFF0F1420), Color(0xFF0E0E0E))
                else listOf(Color(0xFF3B82F6), Color(0xFF2563EB), Color(0xFF1E3A8A))
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .shadow(24.dp, RoundedCornerShape(20.dp), clip = false)
                .clip(RoundedCornerShape(20.dp))
                .background(c.Surface)
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(52.dp).background(c.Primary, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(FeatherIcons.GitBranch, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("PR Dashboard", fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Connect your GitHub account to get started", fontFamily = body, fontSize = 13.sp, color = c.TextSecondary)

            Spacer(Modifier.height(28.dp)); HorizontalDivider(color = c.Border); Spacer(Modifier.height(24.dp))

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Personal Access Token", fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = c.TextPrimary)
                OutlinedTextField(
                    value = token, onValueChange = { token = it },
                    placeholder = { Text("ghp_xxxxxxxxxxxxxxxxxxxx", fontFamily = Mono, fontSize = 13.sp, color = c.TextMuted) },
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { showToken = !showToken }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(if (showToken) "Hide" else "Show", fontFamily = body, fontSize = 11.sp, color = c.TextSecondary)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.Primary, unfocusedBorderColor = c.Border,
                        focusedContainerColor = c.Surface, unfocusedContainerColor = c.Surface,
                        focusedTextColor = c.TextPrimary, unfocusedTextColor = c.TextPrimary
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 14.sp)
                )
                if (error != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.ErrorLight).padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(FeatherIcons.AlertTriangle, null, tint = c.Error, modifier = Modifier.size(14.dp))
                        Text(error, fontFamily = body, fontSize = 13.sp, color = c.Error)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { if (token.isNotBlank()) onTokenSaved(token.trim()) },
                enabled = token.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.Primary)
            ) {
                if (isLoading) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Text("Validating…", fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                    }
                } else {
                    Text("Connect to GitHub  →", fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(FeatherIcons.Lock, null, tint = c.TextMuted, modifier = Modifier.size(12.dp))
                Text("Stored only in your browser — never sent to any server", fontFamily = body, fontSize = 11.sp, color = c.TextMuted)
            }
        }
    }
}

private val Mono = androidx.compose.ui.text.font.FontFamily.Monospace
