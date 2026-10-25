package com.willian.mp3pro.ui.skins

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.willian.mp3pro.player.PlayerUi

/** Visuais do player. A escolha do usuário fica salva no aparelho. */
enum class PlayerSkin(val id: String, val label: String) {
    VINIL("vinil", "Toca-discos de vinil"),
    CASSETE("cassete", "Toca-fitas cassete"),
    ROLO("rolo", "Gravador de rolo"),
    CLASSICO("classico", "Clássico");

    companion object {
        fun fromId(id: String): PlayerSkin = entries.firstOrNull { it.id == id } ?: VINIL
    }
}

/** Mostra a skin escolhida. As skins retrô têm teclas que funcionam (anterior, play/pausa, próxima). */
@Composable
fun SkinStage(
    skin: PlayerSkin,
    ui: PlayerUi,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (skin) {
        PlayerSkin.VINIL -> TurntableSkin(ui, onToggle, modifier)
        PlayerSkin.CASSETE -> CassetteSkin(ui, onPrevious, onToggle, onNext, modifier)
        PlayerSkin.ROLO -> ReelDeckSkin(ui, onPrevious, onToggle, onNext, modifier)
        PlayerSkin.CLASSICO -> ClassicSkin(modifier)
    }
}

@Composable
private fun ClassicSkin(modifier: Modifier) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0891B2), Color(0xFF6D28D9)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.MusicNote, contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(96.dp)
        )
    }
}

/** Tecla de aparelho antigo: quando [down] é verdadeiro ela fica "afundada", como a tecla PLAY. */
@Composable
internal fun RetroKey(
    icon: ImageVector,
    description: String,
    down: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    val colors = if (down) listOf(Color(0xFF8A8E95), Color(0xFFB0B4BA))
    else listOf(Color(0xFFE3E5E8), Color(0xFF9CA1A8))
    Box(
        modifier
            .offset(y = if (down) 2.dp else 0.dp)
            .shadow(if (down) 1.dp else 5.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(colors))
            .border(1.dp, Color(0xFF3A3D42), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color(0xFF22252A))
    }
}
