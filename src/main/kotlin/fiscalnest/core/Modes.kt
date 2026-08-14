package fiscalnest.core

/**
 * Defines how a cushion top-up value is interpreted.
 */
enum class TopupMode {
    PERCENT_OF_TARGET,
    PERCENT_OF_REMAINDER
}

/**
 * Defines how the piggy bank target is interpreted.
 */
enum class PiggyBankMode {
    PERCENT_OF_REMAINDER,
    FIXED_AMOUNT
}
