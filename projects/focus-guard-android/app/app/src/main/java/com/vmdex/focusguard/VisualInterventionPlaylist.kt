package com.vmdex.focusguard

data class VisualInterventionVideo(
    val id: String,
    val title: String,
    val resourceId: Int
)

data class VisualInterventionVideoSettings(
    val isGreenScreenEnabled: Boolean = true,
    val isSoundEnabled: Boolean = false,
    val zoomPercent: Int = 100,
    val greenDominanceMinPercent: Int = 12,
    val greenDominanceMaxPercent: Int = 42,
    val greenBrightnessMinPercent: Int = 22,
    val positionX: Int? = null,
    val positionY: Int? = null
)

data class VisualInterventionBulkSettings(
    val isGreenScreenEnabled: Boolean = true,
    val isSoundEnabled: Boolean = false,
    val greenDominanceMinPercent: Int = 12,
    val greenDominanceMaxPercent: Int = 42,
    val greenBrightnessMinPercent: Int = 22
)

val VisualInterventionPlaylist: List<VisualInterventionVideo> = listOf(
    VisualInterventionVideo("vi_cat_01", "01-Cat Driving Car | Green Screen Template", R.raw.vi_cat_01),
    VisualInterventionVideo("vi_cat_02", "02-Cat Jumping Up and Down | Green Screen", R.raw.vi_cat_02),
    VisualInterventionVideo("vi_cat_03", "03-oo ee a e a Cat Green Screen #shorts #meme #catviral", R.raw.vi_cat_03),
    VisualInterventionVideo("vi_cat_04", "04-Black Cat Zoning Out | Green Screen", R.raw.vi_cat_04),
    VisualInterventionVideo("vi_cat_05", "05-Chipi Chipi Chapa Chapa Cat | Green Screen", R.raw.vi_cat_05),
    VisualInterventionVideo("vi_cat_06", "06-Green Screen Cat Hits Another Cat Meme", R.raw.vi_cat_06),
    VisualInterventionVideo("vi_cat_07", "07-Green Screen Dramatic Kitten Meme | Screaming Cat Meme", R.raw.vi_cat_07),
    VisualInterventionVideo("vi_cat_08", "08-Green Screen Crunchy Cat Luna Meme", R.raw.vi_cat_08),
    VisualInterventionVideo("vi_cat_09", "09-Green Screen Cat Rizz Meme", R.raw.vi_cat_09),
    VisualInterventionVideo("vi_cat_10", "10-Green Screen Meowing Cat Meme", R.raw.vi_cat_10),
    VisualInterventionVideo("vi_cat_11", "11-Green Screen Talking Cat Meme", R.raw.vi_cat_11),
    VisualInterventionVideo("vi_cat_12", "12-Green Screen Screaming Cat Meme", R.raw.vi_cat_12),
    VisualInterventionVideo("vi_cat_13", "13-Sad Cat Meowing Meme Green Screen Chroma Key Template", R.raw.vi_cat_13),
    VisualInterventionVideo("vi_cat_14", "14-wet cat staring at camera green screen", R.raw.vi_cat_14),
    VisualInterventionVideo("vi_cat_15", "15-Cat Gags Over Yogurt meme - Green Screen", R.raw.vi_cat_15),
    VisualInterventionVideo("vi_cat_16", "16-Kitten Stare With Trumpet Music Meme | Green Screen", R.raw.vi_cat_16),
    VisualInterventionVideo("vi_cat_17", "17-Tole tole cat (Mei Mei) green screen #MurdoinkGreenScreen", R.raw.vi_cat_17),
    VisualInterventionVideo("vi_cat_18", "18-Eyebrow Cat Vine Boom - Green Screen", R.raw.vi_cat_18),
    VisualInterventionVideo("vi_cat_19", "20-Gato con Audifonos | Pantalla Verde #gato #bellakath #cat #parati #trending #fyp", R.raw.vi_cat_19),
    VisualInterventionVideo("vi_cat_20", "21-Cat staring and turning red green screen #MurdoinkGreenScreen", R.raw.vi_cat_20),
    VisualInterventionVideo("vi_cat_21", "22-sleepy cat meme (green screen, with sound)", R.raw.vi_cat_21),
    VisualInterventionVideo("vi_cat_22", "23-huh cat meme (green screen, template 2)", R.raw.vi_cat_22),
    VisualInterventionVideo("vi_cat_23", "24-Black cat filing nails meme (green screen)", R.raw.vi_cat_23),
    VisualInterventionVideo("vi_cat_24", "25-Maxwell cat meme (Spin + dance, green screen)", R.raw.vi_cat_24),
    VisualInterventionVideo("vi_cat_25", "26-angry/grumpy cat meme (green screen)", R.raw.vi_cat_25)
)

fun visualInterventionVideoById(id: String): VisualInterventionVideo? {
    return VisualInterventionPlaylist.firstOrNull { it.id == id }
}
