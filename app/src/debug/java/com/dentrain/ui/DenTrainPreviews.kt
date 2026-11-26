package com.dentrain.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dentrain.data.RecordEntity
import com.dentrain.ui.components.GradientButton
import com.dentrain.ui.components.RecordCelebrationContent
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
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            RecordCelebrationContent(
                exerciseName = "Жим штанги лёжа",
                weightText = "82,5",
                repsText = "5",
                previous = RecordEntity(exerciseName = "Жим штанги лёжа", weight = 80.5, reps = 5, dateEpochDay = 0),
                onDismiss = {}
            )
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
