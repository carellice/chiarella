package it.chiarella.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import it.chiarella.app.ui.theme.Look
import it.chiarella.app.ui.theme.Palette
import androidx.compose.ui.graphics.takeOrElse
import it.chiarella.app.ui.theme.leather
import it.chiarella.app.ui.theme.metroSwivel

/** Foglio dal basso rivestito in pelle cucita (o il ModalBottomSheet classico). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeatherSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (Look.metro) {
        // Metro: pannello piatto a spigoli vivi, senza maniglia.
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = Palette.Surface,
            shape = RectangleShape,
            tonalElevation = 0.dp,
            dragHandle = { Spacer(Modifier.height(24.dp)) },
            content = content,
        )
        return
    }
    if (!Look.skeuo) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = Palette.Surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.25f)) },
            content = content,
        )
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        shape = RectangleShape,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .leather(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), elevation = 0.dp)
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 18.dp), contentAlignment = Alignment.Center) {
                // Linguetta incisa nella pelle.
                Box(
                    Modifier.width(42.dp).height(5.dp).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color(0x33FFE6C0))))
                )
            }
            content()
        }
    }
}

/** Finestra di dialogo in pelle (o l'AlertDialog classico), con la stessa firma essenziale di AlertDialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeatherAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = Color.Unspecified,
) {
    if (!Look.skeuo && !Look.metro) {
        AlertDialog(
            onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier,
            dismissButton = dismissButton, title = title, text = text,
            containerColor = containerColor.takeOrElse { Palette.SurfaceHigh },
        )
        return
    }
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (Look.metro) Modifier.metroSwivel().background(Palette.SurfaceHigh) else Modifier.leather(RoundedCornerShape(26.dp), elevation = 24.dp))
                .padding(start = 26.dp, end = 18.dp, top = 26.dp, bottom = 14.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides Palette.Text) {
                title?.let {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall) { it() }
                    Spacer(Modifier.height(14.dp))
                }
                text?.let {
                    CompositionLocalProvider(LocalContentColor provides Palette.TextMuted) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { Box(Modifier.padding(end = 8.dp)) { it() } }
                    }
                    Spacer(Modifier.height(18.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    dismissButton?.invoke()
                    Spacer(Modifier.size(4.dp))
                    confirmButton()
                }
            }
        }
    }
}
