package dev.gisketch.chowkingdom.roles

data class BodyScaleChoice(val height: Double = DEFAULT_BODY_SCALE, val weight: Double = DEFAULT_BODY_SCALE)

data class FemaleGenderChoice(
    val bodyModel: String = DEFAULT_BODY_MODEL,
    val bustSize: Double = DEFAULT_FG_BUST_SIZE,
    val physics: Boolean = DEFAULT_FG_PHYSICS,
    val showInArmor: Boolean = DEFAULT_FG_SHOW_IN_ARMOR,
    val bounce: Double = DEFAULT_FG_BOUNCE,
    val floppy: Double = DEFAULT_FG_FLOPPY,
)

fun normalizeBodyScale(value: Double): Double = value.coerceIn(MIN_BODY_SCALE, MAX_BODY_SCALE)

fun normalizeBodyModel(value: String): String =
    if (value.equals(BODY_MODEL_GIRL, ignoreCase = true)) BODY_MODEL_GIRL else BODY_MODEL_BOY

fun normalizeFemaleGenderBustSize(value: Double): Double = value.coerceIn(MIN_FG_BUST_SIZE, MAX_FG_BUST_SIZE)

fun normalizeFemaleGenderBounce(value: Double): Double = value.coerceIn(MIN_FG_BOUNCE, MAX_FG_BOUNCE)

fun normalizeFemaleGenderFloppy(value: Double): Double = value.coerceIn(MIN_FG_FLOPPY, MAX_FG_FLOPPY)

const val MIN_BODY_SCALE = 0.6
const val MAX_BODY_SCALE = 1.4
const val DEFAULT_BODY_SCALE = 1.0
const val BODY_MODEL_GIRL = "girl"
const val BODY_MODEL_BOY = "boy"
const val DEFAULT_BODY_MODEL = BODY_MODEL_BOY
const val MIN_FG_BUST_SIZE = 0.0
const val MAX_FG_BUST_SIZE = 0.8
const val DEFAULT_FG_BUST_SIZE = 0.6
const val DEFAULT_FG_PHYSICS = true
const val DEFAULT_FG_SHOW_IN_ARMOR = true
const val MIN_FG_BOUNCE = 0.0
const val MAX_FG_BOUNCE = 0.5
const val DEFAULT_FG_BOUNCE = 0.333
const val MIN_FG_FLOPPY = 0.25
const val MAX_FG_FLOPPY = 1.0
const val DEFAULT_FG_FLOPPY = 0.75
