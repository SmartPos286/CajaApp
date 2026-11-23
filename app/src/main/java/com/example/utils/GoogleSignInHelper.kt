package com.example.utils

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.ClearCredentialStateRequest
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class GoogleSignInHelper(private val context: Context) {
    private val TAG = "GoogleSignInHelper"
    private val credentialManager = CredentialManager.create(context)

    private val webClientId: String
        get() = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            ""
        }

    suspend fun signInWithGoogle(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val signInOption = GetSignInWithGoogleOption.Builder(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val result: GetCredentialResponse = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val email = googleIdTokenCredential.id

                val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = FirebaseAuth.getInstance().signInWithCredential(firebaseCred).await()
                val user = authResult.user
                if (user != null && !user.email.isNullOrBlank()) {
                    Log.i(TAG, "Google Sign-In successful for: ${user.email}")
                    Result.success(user.email!!)
                } else {
                    Result.failure(Exception("No se pudo obtener el correo de la cuenta de Google."))
                }
            } else {
                Result.failure(Exception("Tipo de credencial de Google no reconocido."))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google Sign-In error: ${e.localizedMessage ?: e.message}")
            Result.failure(e)
        }
    }

    suspend fun attemptAutoSignIn(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = FirebaseAuth.getInstance().signInWithCredential(firebaseCred).await()
                val user = authResult.user
                if (user != null && !user.email.isNullOrBlank()) {
                    return@withContext Result.success(user.email!!)
                }
            }
            Result.failure(Exception("No se encontró cuenta autorizada para auto-inicio."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            FirebaseAuth.getInstance().signOut()
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
