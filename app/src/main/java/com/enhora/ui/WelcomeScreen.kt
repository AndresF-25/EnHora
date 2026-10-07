package com.enhora.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.enhora.ui.theme.GradientScreen

/** Primera apertura: pide el nombre, que queda guardado como usuario. */
@Composable
fun WelcomeScreen(onContinue: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val canContinue = name.isNotBlank()
    GradientScreen {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Bienvenido a", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "EnHora", style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "¿Cómo te llamas?", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 32.dp, bottom = 12.dp),
            )
            TextField(
                value = name,
                onValueChange = { name = it.take(30) },
                label = { Text("Tu nombre") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (canContinue) onContinue(name.trim()) }),
                shape = FieldShape,
                colors = glassFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onContinue(name.trim()) },
                enabled = canContinue,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text("Continuar") }
        }
    }
}
