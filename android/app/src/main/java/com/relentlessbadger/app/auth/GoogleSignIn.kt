package com.relentlessbadger.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

object GoogleSignIn {

    /**
     * Runs the Credential Manager Google Sign-In flow and returns the Google
     * ID token to exchange with the backend. Must be called with an Activity
     * context so the account picker can attach to a window.
     */
    suspend fun getIdToken(activityContext: Context, webClientId: String): String {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val credential = CredentialManager.create(activityContext)
            .getCredential(activityContext, request)
            .credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Unexpected credential type: ${credential.type}")
    }
}
