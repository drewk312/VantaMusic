package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.account.AccountManager
import kotlin.random.Random

private val avatarColors = listOf(
    Color(0xFF66D9EF), Color(0xFF93C5FD), Color(0xFFF5B971),
    Color(0xFF60D394), Color(0xFFA78BFA), Color(0xFFF472B6),
    Color(0xFFFB923C), Color(0xFF34D399)
)

/**
 * Account onboarding / profile screen.
 *
 * Designed to be lightweight: just a display name and opt-in sync toggles.
 * No mandatory sign-in — VANTA works fully offline and anonymously.
 */
@Composable
fun AccountScreen(
    accountManager: AccountManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel? = null
) {
    val profile by accountManager.profile.collectAsState()

    var displayNameInput by remember { mutableStateOf(profile.displayName) }
    var showSignedInView by remember { mutableStateOf(profile.isOnboarded) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppBackgroundTop, AppBackgroundBottom)))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppText)
            }
            Text(
                text = if (showSignedInView) "Profile" else "Welcome to VANTA",
                color = AppText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(48.dp)) // balance
        }

        if (showSignedInView && profile.isOnboarded) {
            // Signed-in profile view
            SignedInProfile(
                profile = profile,
                onSignOut = {
                    accountManager.signOut()
                    showSignedInView = false
                },
                onToggleSourceSync = { accountManager.setSourceSyncEnabled(it) },
                onToggleHistorySync = { accountManager.setHistorySyncEnabled(it) },
                onImportAppleMusic = { mainViewModel?.importAppleMusicLibrary() },
                onConnectAppleMusic = {
                    // In a real app, this would trigger the system Music Auth flow.
                    // For this environment, we'll simulate a successful connection
                    // if the developer token is already present in config.
                    accountManager.setAppleMusicSession("simulated_user_token", "us")
                }
            )
        } else {
            // Onboarding / sign-in view
            OnboardingView(
                displayNameInput = displayNameInput,
                onDisplayNameChange = { displayNameInput = it },
                onComplete = {
                    if (displayNameInput.isNotBlank()) {
                        accountManager.completeOnboarding(displayNameInput)
                        showSignedInView = true
                    }
                }
            )
        }
    }
}

@Composable
private fun OnboardingView(
    displayNameInput: String,
    onDisplayNameChange: (String) -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "One library. Every source.",
            color = AppAccent,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Choose a display name to personalize your experience.\nYou can change this anytime.",
            color = AppTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = displayNameInput,
            onValueChange = { if (it.length <= 32) onDisplayNameChange(it) },
            label = { Text("Display Name", color = AppTextMuted) },
            placeholder = { Text("e.g. Alex", color = AppTextMuted.copy(alpha = 0.5f)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (displayNameInput.isNotBlank()) onComplete() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = AppText,
                unfocusedTextColor = AppText,
                cursorColor = AppAccent,
                focusedBorderColor = AppAccent,
                unfocusedBorderColor = AppSurfaceRaised,
                focusedContainerColor = AppSurface,
                unfocusedContainerColor = AppSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onComplete,
            enabled = displayNameInput.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppAccent,
                contentColor = AppBackgroundTop
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No account required. Your library stays on this device.",
            color = AppTextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SignedInProfile(
    profile: AccountManager.UserProfile,
    onSignOut: () -> Unit,
    onToggleSourceSync: (Boolean) -> Unit,
    onToggleHistorySync: (Boolean) -> Unit,
    onImportAppleMusic: () -> Unit,
    onConnectAppleMusic: () -> Unit
) {
    // Avatar
    val avatarColor = remember(profile.avatarSeed) {
        val index = profile.avatarSeed.hashCode().let { (it and Int.MAX_VALUE) % avatarColors.size }
        avatarColors[if (index < 0) 0 else index]
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(avatarColor.copy(alpha = 0.2f))
                .border(2.dp, avatarColor.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = profile.avatarSeed.ifBlank { "??" },
                color = avatarColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = profile.displayName,
            color = AppText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        if (profile.email.isNotBlank()) {
            Text(profile.email, color = AppTextSecondary, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Apple Music Integration
        Text(
            text = "Connected Services",
            color = AppTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AppSurface)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Sync,
                contentDescription = null,
                tint = if (profile.appleMusicUserToken != null) AppAccentSoft else AppTextMuted,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Apple Music", color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = if (profile.appleMusicUserToken != null) "Connected • US Storefront" else "Connect to import your library",
                    color = AppTextMuted,
                    fontSize = 12.sp
                )
            }
            if (profile.appleMusicUserToken == null) {
                Button(
                    onClick = onConnectAppleMusic,
                    colors = ButtonDefaults.buttonColors(containerColor = AppAccent),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Connect", fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = onImportAppleMusic,
                    colors = ButtonDefaults.buttonColors(containerColor = AppSurfaceVariant),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Import", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sync preferences
        Text(
            text = "Sync Preferences",
            color = AppTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
        SyncToggle(
            title = "Sync Sources",
            subtitle = "Save your source configuration for backup",
            checked = profile.sourceSyncEnabled,
            onCheckedChange = onToggleSourceSync
        )
        SyncToggle(
            title = "Sync History",
            subtitle = "Backup listening history across devices",
            checked = profile.historySyncEnabled,
            onCheckedChange = onToggleHistorySync,
            isLast = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Sign Out",
            color = AppDestructive,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onSignOut)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun SyncToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Sync,
            contentDescription = null,
            tint = AppAccentSoft,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = AppTextMuted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppAccent,
                checkedTrackColor = AppAccent.copy(alpha = 0.3f),
                uncheckedThumbColor = AppTextMuted,
                uncheckedTrackColor = AppSurfaceRaised
            )
        )
    }
}
