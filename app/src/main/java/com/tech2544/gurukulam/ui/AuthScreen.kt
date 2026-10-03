package com.tech2544.gurukulam.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.tech2544.gurukulam.AppContainer
import com.tech2544.gurukulam.BuildConfig
import com.tech2544.gurukulam.R
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

@Composable
fun AuthScreen(c: AppContainer) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.logo_full), null, Modifier.width(260.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.app_name), color = GK.Cream, fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.tagline), color = GK.Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))

        if (busy) {
            CircularProgressIndicator(color = GK.Red)
        } else {
            Text(
                stringResource(R.string.continue_google),
                color = Color.Black, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(GK.Cream)
                    .clickable {
                        busy = true
                        scope.launch {
                            try {
                                googleSignIn(ctx, c)
                            } catch (e: NoCredentialException) {
                                ctx.toast(R.string.no_google_account)
                            } catch (e: Exception) {
                                ctx.toast(R.string.login_error)
                            } finally {
                                busy = false
                            }
                        }
                    }
                    .padding(vertical = 16.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.login_note), color = GK.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

/** Google Credential Manager + nonce (replay attack se bachav). Token Supabase ko jata hai. */
private suspend fun googleSignIn(ctx: android.content.Context, c: AppContainer) {
    val raw = UUID.randomUUID().toString()
    val hashed = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        .joinToString("") { "%02x".format(it) }
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .setNonce(hashed)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val result = CredentialManager.create(ctx).getCredential(ctx, request)
    val cred = result.credential
    if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val g = GoogleIdTokenCredential.createFrom(cred.data)
        c.supa.signInWithGoogle(g.idToken, raw)
    } else {
        error("Unsupported credential")
    }
}
