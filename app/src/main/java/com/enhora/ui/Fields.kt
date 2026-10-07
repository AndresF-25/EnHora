package com.enhora.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.enhora.ui.theme.LocalGradient
import androidx.compose.ui.unit.dp

/** Forma de los campos y botones de cristal (pantalla de recordatorio, bienvenida). */
internal val FieldShape = RoundedCornerShape(20.dp)

/** Texto oscuro del tono del tema, para leer sobre superficies blancas. */
@Composable
internal fun inkColor(): Color = lerp(LocalGradient.current.bottom, Color.Black, 0.35f)

/** Campos de texto de cristal, como el calendario: fondo translúcido, sin contorno (al enfocar se aclara). */
@Composable
internal fun glassFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.White.copy(alpha = 0.24f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.14f),
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Color.White,
    focusedLabelColor = Color.White,
    unfocusedLabelColor = Color.White.copy(alpha = 0.75f),
)
