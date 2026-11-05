package com.dentrain.data

data class CatalogExercise(
    val name: String,
    val category: String
)

/**
 * Каталог стандартных упражнений по группам мышц для быстрого выбора в 1 клик.
 */
object ExerciseCatalog {
    val categories = listOf("Все", "Грудь", "Спина", "Ноги", "Плечи", "Руки", "Пресс")

    val defaultExercises = listOf(
        CatalogExercise("Жим штанги лежа", "Грудь"),
        CatalogExercise("Жим гантелей на наклонной", "Грудь"),
        CatalogExercise("Отжимания на брусьях", "Грудь"),
        CatalogExercise("Сведение в кроссовере", "Грудь"),

        CatalogExercise("Подтягивания", "Спина"),
        CatalogExercise("Тяга штанги в наклоне", "Спина"),
        CatalogExercise("Тяга верхнего блока", "Спина"),
        CatalogExercise("Становая тяга", "Спина"),

        CatalogExercise("Приседания со штангой", "Ноги"),
        CatalogExercise("Жим ногами", "Ноги"),
        CatalogExercise("Выпады с гантелями", "Ноги"),
        CatalogExercise("Румынская тяга", "Ноги"),

        CatalogExercise("Жим штанги стоя (армейский)", "Плечи"),
        CatalogExercise("Махи гантелями в стороны", "Плечи"),

        CatalogExercise("Сгибания рук на бицепс", "Руки"),
        CatalogExercise("Французский жим", "Руки"),

        CatalogExercise("Скручивания на пресс", "Пресс"),
        CatalogExercise("Подъём ног в висе", "Пресс"),
        CatalogExercise("Планка", "Пресс")
    )
}
