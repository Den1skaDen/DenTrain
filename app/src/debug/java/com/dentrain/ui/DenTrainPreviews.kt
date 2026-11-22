package com.dentrain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import com.dentrain.ui.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dentrain.data.RecordEntity
import com.dentrain.ui.components.GradientButton
import com.dentrain.ui.components.ScreenHeader
import com.dentrain.ui.components.SectionTitle
import com.dentrain.ui.components.ZCard
import com.dentrain.ui.components.rememberMetricNumberWidth
import com.dentrain.ui.records.RecordCard
import com.dentrain.ui.theme.DenTrainTheme
import java.time.LocalDate

@Preview(showBackground = true)
@Composable
fun RecordCelebrationPreview() {
    DenTrainTheme {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp).background(Color.Black.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFD700).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.EmojiEvents,
                            contentDescription = "Рекорд",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(52.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "RECORD! 🏆",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "НОВЫЙ ЛИЧНЫЙ РЕКОРД",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(16.dp)
                    ) {
                        Text(
                            "Жим штанги лежа",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "100 кг × 10 повторений",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    GradientButton(
                        "Ура! Запомнить рекорд",
                        onClick = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecordsScreenPreview() {
    DenTrainTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GradientButton("Добавить рекорд", {}, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
            RecordCard(
                weightWidth = rememberMetricNumberWidth(3, 26.sp, FontWeight.ExtraBold, 26.sp),
                repsWidth = rememberMetricNumberWidth(2, 26.sp, FontWeight.ExtraBold, 26.sp),
                record = RecordEntity(
                    id = 1,
                    exerciseName = "Жим штанги лёжа",
                    weight = 80.0,
                    reps = 8,
                    dateEpochDay = LocalDate.of(2024, 6, 14).toEpochDay(),
                    comment = "",
                    isFavorite = true
                ),
                onEdit = {},
                onDelete = {},
                onFavoriteToggle = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WorkoutHomeScreenPreview() {
    DenTrainTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScreenHeader("Тренировка", "DenTrain") {}
            GradientButton("Создать тренировку", {}, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
            SectionTitle("Запланированные")
            ZCard {
                Text("Силовая тренировка", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Грудь, Трицепс", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
