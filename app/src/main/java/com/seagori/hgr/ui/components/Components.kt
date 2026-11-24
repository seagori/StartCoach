package com.seagori.hgr.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seagori.hgr.domain.maskApiKey
import com.seagori.hgr.ui.theme.Accent
import com.seagori.hgr.ui.theme.AccentDeep
import com.seagori.hgr.ui.theme.AccentLight
import com.seagori.hgr.ui.theme.Card
import com.seagori.hgr.ui.theme.Ink
import com.seagori.hgr.ui.theme.Line
import com.seagori.hgr.ui.theme.Muted
import com.seagori.hgr.ui.theme.Soft

/** 공통 카드 스타일: 라운드 + 배경 + 테두리 */
fun Modifier.card(radius: Dp = 18.dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(Card)
    .border(1.dp, Line, RoundedCornerShape(radius))

/**
 * API 키 입력란에서 앞 3자와 뒤 2자만 보이게 한다.
 * 예: sk-1234...7890DS -> sk-...-DS
 */
class PartialMaskVisualTransformation(
    private val prefix: Int = 3,
    private val suffix: Int = 2
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.length <= prefix + suffix) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val visible = maskApiKey(raw)
        val maskEnd = visible.length - suffix
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= prefix -> offset
                offset >= raw.length - suffix -> maskEnd + (offset - (raw.length - suffix))
                else -> maskEnd
            }

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= prefix -> offset
                offset >= maskEnd -> (raw.length - suffix) + (offset - maskEnd)
                else -> prefix
            }
        }
        return TransformedText(AnnotatedString(visible), mapping)
    }
}

@Composable
fun GoalCardList(
    title: String,
    items: List<GoalRow>,
    emptyText: String? = null,
    onClick: (GoalRow) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .card(22.dp)
            .padding(horizontal = 18.dp, vertical = 20.dp)
    ) {
        Text(
            text = "◎   $title",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink
        )
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty() && emptyText != null) {
            Text(emptyText, fontSize = 14.sp, color = Muted)
        }
        items.forEach { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(item) }
                    .padding(vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = Muted
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    item.subtitle,
                    fontSize = 12.sp,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(9.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Soft)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(item.progress.coerceIn(0f, 1f))
                            .height(7.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Accent)
                    )
                }
            }
        }
    }
}

data class GoalRow(
    val title: String,
    val subtitle: String,
    val progress: Float
)

@Composable
fun ScreenHeader(title: String, subtitle: String? = null) {
    Column {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Card,
            labelColor = Muted,
            selectedContainerColor = AccentLight,
            selectedLabelColor = AccentDeep
        ),
        border = BorderStroke(1.dp, if (selected) Accent else Line)
    )
}

@Composable
fun OptionItem(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) AccentLight else Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .border(1.8.dp, if (selected) Accent else Muted, CircleShape)
                .background(if (selected) Accent else Card),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Card)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 15.sp, color = Ink)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(57.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentDeep,
            contentColor = Card,
            disabledContainerColor = Soft,
            disabledContentColor = Muted
        )
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(55.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Soft,
            contentColor = Ink,
            disabledContainerColor = Soft,
            disabledContentColor = Muted
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = AccentDeep
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SmallPill(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Soft)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = AccentDeep,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun OptionList(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Line, RoundedCornerShape(18.dp)),
        verticalArrangement = Arrangement.Top
    ) {
        content()
    }
}

/** 라이선스 전문을 스크롤·선택 가능한 다이얼로그로 보여준다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LicenseDialog(
    title: String,
    sections: List<Pair<String, String>>,
    closeLabel: String,
    onDismiss: () -> Unit
) {
    var selectedIndex by remember(sections) { mutableStateOf(0) }
    val scrollState = rememberScrollState()
    LaunchedEffect(selectedIndex) {
        scrollState.scrollTo(0)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        titleContentColor = Ink,
        textContentColor = Ink,
        tonalElevation = 0.dp,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (sections.size > 1) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sections.forEachIndexed { idx, (label, _) ->
                            AppFilterChip(
                                selected = selectedIndex == idx,
                                onClick = { selectedIndex = idx },
                                label = label
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                val body = sections.getOrNull(selectedIndex)?.second.orEmpty()
                SelectionContainer {
                    Text(
                        text = body,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = Ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Soft)
                            .verticalScroll(scrollState)
                            .padding(14.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(closeLabel, color = AccentDeep) }
        }
    )
}
