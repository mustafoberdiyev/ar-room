package com.example.arroom

/**
 * Каталог 3D-моделей. Файлы лежат в app/src/main/assets/models/<id>.glb,
 * миниатюры — в assets/thumbs/<id>.jpg. Их скачивает scripts/fetch_models.sh
 * (модели Khronos glTF-Sample-Assets).
 *
 * size — желаемый максимальный размер модели в метрах (scaleToUnits).
 */
data class CatalogItem(
    val id: String,
    val title: String,
    val size: Float,
) {
    val modelPath get() = "models/$id.glb"
    val thumbPath get() = "thumbs/$id.jpg"
}

val CATALOG = listOf(
    CatalogItem("SheenChair", "Кресло", 0.9f),
    CatalogItem("GlamVelvetSofa", "Диван", 1.8f),
    CatalogItem("ChairDamaskPurplegold", "Стул", 0.9f),
    CatalogItem("Lantern", "Фонарь", 0.6f),
    CatalogItem("WaterBottle", "Бутылка", 0.3f),
    CatalogItem("Avocado", "Авокадо", 0.15f),
    CatalogItem("BoomBox", "Магнитола", 0.35f),
    CatalogItem("Duck", "Утка", 0.3f),
    CatalogItem("ToyCar", "Машинка", 0.4f),
    CatalogItem("DamagedHelmet", "Шлем", 0.4f),
)
