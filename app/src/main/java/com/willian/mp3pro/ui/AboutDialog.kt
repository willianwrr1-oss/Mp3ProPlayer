package com.willian.mp3pro.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.willian.mp3pro.BuildConfig

private const val DEVELOPER = "Willian R Rocha"
private const val CONTACT = "willian.wrr1@gmail.com"

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sobre") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "MP3 Pro Player",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Player de músicas com equalizador de 6 faixas, efeitos de loudness e " +
                        "surround, pasta compartilhada do OneDrive, músicas offline e Android Auto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                InfoRow("Versão", BuildConfig.VERSION_NAME)
                InfoRow("Última atualização", BuildConfig.BUILD_DATE)
                InfoRow("Compatibilidade", "Android 13 ou superior")

                HorizontalDivider()

                InfoRow("Desenvolvido por", DEVELOPER)
                InfoRow("Contato", CONTACT)
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:$CONTACT")
                            putExtra(Intent.EXTRA_SUBJECT, "MP3 Pro Player ${BuildConfig.VERSION_NAME}")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            // Sem aplicativo de e-mail instalado: o endereço já está na tela.
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Enviar e-mail") }

                HorizontalDivider()

                Text(
                    "Proteção de dados",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "O aplicativo não coleta nem envia dados pessoais. A internet é usada apenas " +
                        "para listar e baixar as músicas da pasta do OneDrive que você configurou. " +
                        "Músicas baixadas, ajustes do equalizador e preferências ficam somente " +
                        "neste aparelho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.width(132.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
