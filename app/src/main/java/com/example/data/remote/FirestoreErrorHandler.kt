package com.example.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException

enum class OperationType {
    CREATE, UPDATE, DELETE, LIST, GET, WRITE
}

object FirestoreErrorHandler {
    private const val TAG = "FirestoreError"

    fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String): String {
        val errorMessage = when (exception) {
            is FirebaseFirestoreException -> {
                when (exception.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "Permiso denegado en Firestore para operación $operationType en path '$path'. Verifica que estés autenticado con Google y que las reglas de seguridad permitan esta operación."
                    FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                        "No autenticado. Por favor inicia sesión con Google para acceder a la base de datos en la nube."
                    FirebaseFirestoreException.Code.NOT_FOUND ->
                        "El documento o recurso solicitado no existe en el path '$path'."
                    FirebaseFirestoreException.Code.ALREADY_EXISTS ->
                        "El registro ya existe en la base de datos."
                    FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
                        "Se excedió la cuota o límites de Firestore."
                    FirebaseFirestoreException.Code.ABORTED ->
                        "La transacción concurrente fue abortada. Reintentando..."
                    else ->
                        "Error de Firestore (${exception.code}): ${exception.localizedMessage ?: exception.message}"
                }
            }
            else -> exception.localizedMessage ?: exception.message ?: "Error desconocido en operación $operationType"
        }

        val jsonLog = """
            {
              "operation": "$operationType",
              "path": "$path",
              "error": "$errorMessage",
              "timestamp": ${System.currentTimeMillis()}
            }
        """.trimIndent()

        Log.e(TAG, "Firestore Error JSON:\n$jsonLog", exception)
        return errorMessage
    }
}
