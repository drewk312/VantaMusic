
package com.audiophile.musicplayer.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.account.AccountManager
import com.audiophile.musicplayer.account.FirebaseAuthSessionManager
import com.audiophile.musicplayer.social.FriendFeed
import com.audiophile.musicplayer.social.VantaSocialManager
import kotlinx.coroutines.launch

private val avatarColors = listOf(
    Color(0xFF66D9EF), Color(0xFF93C5FD), Color(0xFFF5B971),
    Color(0xFF60D394), Color(0xFFA78BFA), Color(0xFFF472B6),
    Color(0xFFFB923C), Color(0xFF34D399)
)

private enum class AccountTab(val label: String, val icon: ImageVector) {
    Profile("Profile", Icons.Default.Person),
    Account("Account", Icons.Default.Lock),
    Social("Social", Icons.Default.Groups)
}

/**
 * Account screen with a tabbed layout so sign-in, profile, and social are no
 * longer crammed into one scrolling sheet. VANTA is free; there is no paywall.
 */
@Composable
fun AccountScreen(
    accountManager: AccountManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel? = null,
    vantaSocialManager: VantaSocialManager? = null,
    onOpenFriendProfile: (friendId: String) -> Unit = { _ -> }
) {
    val profile by accountManager.profile.collectAsState()
    val context = LocalContext.current
    val authSession = remember(accountManager) {
        FirebaseAuthSessionManager(context.applicationContext, accountManager)
    }
    val authState by authSession.state.collectAsState()
    val authScope = rememberCoroutineScope()
    DisposableEffect(authSession) {
        onDispose { authSession.close() }
    }

    var selectedTab by remember { mutableStateOf(AccountTab.Profile) }
    var displayNameInput by remember { mutableStateOf(profile.displayName) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppBackgroundTop, AppBackgroundBottom)))
            .padding(top = appTopContentPadding())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppText)
            }
            Text(
                text = "Account",
                color = AppText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        // Tab row
        AccountTabRow(
            selected = selectedTab,
            onSelect = { selectedTab = it }
        )

        // Tab content
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(240)) { it / 5 * direction } + fadeIn(tween(240)))
                    .togetherWith(slideOutHorizontally(tween(240)) { it / 5 * direction } + fadeOut(tween(240)))
            },
            label = "account_tab_content"
        ) { tab ->
            when (tab) {
                AccountTab.Profile -> ProfileTab(
                    profile = profile,
                    displayNameInput = displayNameInput,
                    onDisplayNameChange = { displayNameInput = it },
                    onCompleteOnboarding = {
                        if (displayNameInput.isNotBlank()) {
                            accountManager.completeOnboarding(displayNameInput)
                        }
                    },
                    onSignOut = {
                        authSession.signOut()
                        displayNameInput = ""
                    }
                )
                AccountTab.Account -> AccountTabContent(
                    profile = profile,
                    authState = authState,
                    onSignIn = { email, password ->
                        authScope.launch { authSession.signIn(email, password) }
                    },
                    onRegister = { email, password ->
                        authScope.launch { authSession.register(email, password) }
                    },
                    onSignOut = { authSession.signOut() }
                )
                AccountTab.Social -> SocialTab(
                    profile = profile,
                    socialManager = vantaSocialManager,
                    onOpenFriendProfile = onOpenFriendProfile,
                    onToggleShareListening = { accountManager.setShareListeningActivity(it) }
                )
            }
        }
    }
}

@Composable
private fun AccountTabRow(
    selected: AccountTab,
    onSelect: (AccountTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppSurface)
            .border(0.5.dp, AppOutline, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        AccountTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) AppAccent.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable(onClick = { onSelect(tab) })
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = if (isSelected) AppAccent else AppTextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tab.label,
                    color = if (isSelected) AppAccent else AppTextMuted,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ProfileTab(
    profile: AccountManager.UserProfile,
    displayNameInput: String,
    onDisplayNameChange: (String) -> Unit,
    onCompleteOnboarding: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        if (profile.isCloudAuthenticated) {
            SignedInHeader(profile = profile)
            Text(
                text = "Music services are managed securely in Settings.",
                color = AppTextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onSignOut,
                colors = ButtonDefaults.textButtonColors(contentColor = AppDestructive)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sign out", fontWeight = FontWeight.SemiBold)
            }
        } else {
            AnonymousOnboarding(
                displayNameInput = displayNameInput,
                onDisplayNameChange = onDisplayNameChange,
                onComplete = onCompleteOnboarding
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SignedInHeader(profile: AccountManager.UserProfile) {
    val avatarColor = remember(profile.avatarSeed) {
        val index = profile.avatarSeed.hashCode().let { (it and Int.MAX_VALUE) % avatarColors.size }
        avatarColors[if (index < 0) 0 else index]
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(avatarColor.copy(alpha = 0.18f))
                .border(2.dp, avatarColor.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = profile.avatarSeed.ifBlank { "??" },
                color = avatarColor,
                fontSize = 30.sp,
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
    }
}

@Composable
private fun AnonymousOnboarding(
    displayNameInput: String,
    onDisplayNameChange: (String) -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Welcome to VANTA",
            color = AppText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Set a display name to personalize your experience.\nYou can sign in later to sync across devices.",
            color = AppTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
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
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onComplete,
            enabled = displayNameInput.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = AppBackgroundTop),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Text(
            text = "No account required. Your library stays on this device.",
            color = AppTextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AccountTabContent(
    profile: AccountManager.UserProfile,
    authState: FirebaseAuthSessionManager.State,
    onSignIn: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (profile.isCloudAuthenticated) {
            SignedInHeader(profile = profile)
            Text(
                "You are signed in with ${profile.authProvider?.replaceFirstChar { it.uppercaseChar() } ?: "cloud"}.",
                color = AppTextSecondary,
                fontSize = 14.sp
            )
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = AppSurfaceRaised, contentColor = AppDestructive),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sign out")
            }
        } else {
            Text(
                "Sign in or create an account to sync your library across devices.",
                color = AppTextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            FirebaseEmailSignIn(
                state = authState,
                onSignIn = onSignIn,
                onRegister = onRegister
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FirebaseEmailSignIn(
    state: FirebaseAuthSessionManager.State,
    onSignIn: (String, String) -> Unit,
    onRegister: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(true) } // true = sign in, false = create account

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (state) {
            FirebaseAuthSessionManager.State.Unavailable -> Text(
                "Cloud sign-in needs this app's Firebase configuration.",
                color = AppTextSecondary,
                fontSize = 13.sp
            )
            else -> {
                Text(
                    if (mode) "Sign in with email" else "Create your account",
                    color = AppText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email", color = AppTextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppText,
                        unfocusedTextColor = AppText,
                        cursorColor = AppAccent,
                        focusedBorderColor = AppAccent,
                        unfocusedBorderColor = AppSurfaceRaised,
                        focusedContainerColor = AppSurface,
                        unfocusedContainerColor = AppSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", color = AppTextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (email.isNotBlank() && password.length >= 6) {
                            if (mode) onSignIn(email, password) else onRegister(email, password)
                        }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppText,
                        unfocusedTextColor = AppText,
                        cursorColor = AppAccent,
                        focusedBorderColor = AppAccent,
                        unfocusedBorderColor = AppSurfaceRaised,
                        focusedContainerColor = AppSurface,
                        unfocusedContainerColor = AppSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (state is FirebaseAuthSessionManager.State.Failed) {
                    Text(state.message, color = AppDestructive, fontSize = 13.sp)
                }
                Button(
                    onClick = { if (mode) onSignIn(email, password) else onRegister(email, password) },
                    enabled = email.isNotBlank() && password.length >= 6,
                    colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = AppBackgroundTop),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (mode) "Sign in" else "Create account", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (mode) "Need an account? " else "Already have one? ",
                        color = AppTextMuted,
                        fontSize = 13.sp
                    )
                    TextButton(onClick = { mode = !mode }) {
                        Text(if (mode) "Create account" else "Sign in", color = AppAccent, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialTab(
    profile: AccountManager.UserProfile,
    socialManager: VantaSocialManager?,
    onOpenFriendProfile: (friendId: String) -> Unit,
    onToggleShareListening: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (socialManager != null) {
            FriendsSection(
                profile = profile,
                socialManager = socialManager,
                onOpenFriendProfile = onOpenFriendProfile,
                onToggleShareListening = onToggleShareListening
            )
        } else {
            EmptySocialState()
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun EmptySocialState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Groups, contentDescription = null, tint = AppTextMuted, modifier = Modifier.size(48.dp))
        Text("Social features are unavailable", color = AppText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("Make sure the app finished startup and try again.", color = AppTextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FriendsSection(
    profile: AccountManager.UserProfile,
    socialManager: VantaSocialManager,
    onOpenFriendProfile: (friendId: String) -> Unit = { _ -> },
    onToggleShareListening: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val feed by socialManager.feed.collectAsState(initial = FriendFeed())
    val friendCode = remember { socialManager.friendCode() }
    var friendCodeInput by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

    Text(
        text = "Friends",
        color = AppText,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth()
    )

    VantaCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Friend code row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppSurface)
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("VANTA Friend Code", friendCode))
                        copied = true
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = AppAccentSoft, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Your Friend Code", color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(friendCode, color = AppTextMuted, fontSize = 12.sp)
                }
                Text(
                    text = if (copied) "Copied" else "Copy",
                    color = AppAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            SyncToggle(
                title = "Share Listening Activity",
                subtitle = "Let friends see what you're playing",
                checked = profile.shareListeningActivity,
                onCheckedChange = onToggleShareListening
            )

            // Add friend
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = friendCodeInput,
                    onValueChange = { friendCodeInput = it },
                    label = { Text("Friend code", color = AppTextMuted, fontSize = 12.sp) },
                    placeholder = { Text("vanta_...", color = AppTextMuted.copy(alpha = 0.5f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (friendCodeInput.isNotBlank()) {
                            socialManager.addFriendByHandle(friendCodeInput)
                            friendCodeInput = ""
                        }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppText,
                        unfocusedTextColor = AppText,
                        cursorColor = AppAccent,
                        focusedBorderColor = AppAccent,
                        unfocusedBorderColor = AppSurfaceRaised,
                        focusedContainerColor = AppSurface,
                        unfocusedContainerColor = AppSurface
                    ),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (friendCodeInput.isNotBlank()) {
                            socialManager.addFriendByHandle(friendCodeInput)
                            friendCodeInput = ""
                        }
                    },
                    enabled = friendCodeInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = AppBackgroundTop),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Add", fontWeight = FontWeight.SemiBold)
                }
            }

            // Friends list
            if (feed.friends.isEmpty()) {
                Text(
                    "Add friends to see what they're listening to.",
                    color = AppTextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                feed.friends.forEach { friend ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppSurface)
                            .clickable { onOpenFriendProfile(friend.id) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(AppSurfaceRaised)
                                .border(1.dp, AppAccent.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                friend.avatarSeed.take(2).uppercase(),
                                color = AppAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(friend.displayName, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(friend.id, color = AppTextMuted, fontSize = 11.sp, maxLines = 1)
                        }
                        Text(
                            text = "Remove",
                            color = AppDestructive.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { socialManager.removeFriend(friend.id) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
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
    if (!isLast) {
        HorizontalDivider(color = AppOutline, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 14.dp))
    }
}
