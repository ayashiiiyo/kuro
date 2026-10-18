package com.ayashii.kuro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.ayashii.kuro.data.auth.UserAccount
import com.ayashii.kuro.ui.theme.DarkBorder
import com.ayashii.kuro.ui.theme.DarkCard
import com.ayashii.kuro.ui.theme.MutedText
import com.ayashii.kuro.ui.theme.PureBlack
import com.ayashii.kuro.ui.theme.PureWhite
import com.ayashii.kuro.ui.theme.SubtleGray
import com.ayashii.kuro.ui.theme.Typography

@Composable
fun AccountDialog(
    account: UserAccount?,
    onDismiss: () -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSyncLikedClick: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(DarkCard)
                .border(1.dp, DarkBorder, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Account",
                        style = Typography.titleLarge,
                        color = PureWhite
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SubtleGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (account != null) {
                    // Logged In View
                    if (account.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = account.avatarUrl,
                            contentDescription = "User Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .border(2.dp, PureWhite, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(PureBlack)
                                .border(2.dp, PureWhite, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = account.name.take(1).uppercase(),
                                style = Typography.headlineMedium,
                                color = PureWhite
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = account.name,
                        style = Typography.titleLarge,
                        color = PureWhite
                    )

                    if (account.channelHandle.isNotBlank() || account.email.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (account.channelHandle.isNotBlank()) account.channelHandle else account.email,
                            style = Typography.bodyMedium,
                            color = MutedText
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Connected Status Badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(PureBlack)
                            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Connected to YouTube",
                            style = Typography.labelSmall,
                            color = PureWhite,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action: Sync Liked Songs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(PureBlack)
                            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                            .clickable { onSyncLikedClick(); onDismiss() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Sync Liked Songs",
                                style = Typography.titleMedium,
                                color = PureWhite
                            )
                            Text(
                                text = "Import your liked YouTube music to Library",
                                style = Typography.bodyMedium,
                                color = MutedText,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Log Out Button
                    Button(
                        onClick = { onLogoutClick(); onDismiss() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PureBlack,
                            contentColor = PureWhite
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = null,
                            tint = MutedText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Log Out",
                            style = Typography.bodyLarge,
                            color = PureWhite
                        )
                    }
                } else {
                    // Not Logged In View
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(PureBlack)
                            .border(1.dp, DarkBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = SubtleGray,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Connect YouTube Account",
                        style = Typography.titleLarge,
                        color = PureWhite
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Sign in to YouTube via Google to sync your library, unlock your liked music, and stream without restrictions.",
                        style = Typography.bodyMedium,
                        color = MutedText,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { onLoginClick(); onDismiss() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PureWhite,
                            contentColor = PureBlack
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            text = "Sign In with Google",
                            style = Typography.titleMedium,
                            color = PureBlack
                        )
                    }
                }
            }
        }
    }
}
