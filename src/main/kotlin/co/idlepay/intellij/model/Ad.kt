package co.idlepay.intellij.model

/**
 * Wire types mirrored from upstream src/types.ts and src/api.ts.
 * Only the fields this client actually consumes are modelled.
 */

data class AdStyle(
    val textColorHex: String? = null,
    val badgeColorHex: String? = null,
    val bold: Boolean = true,
)

data class Ad(
    val id: String,
    val campaignId: String,
    val variantId: String? = null,
    val text: String,
    val url: String? = null,
    val cpmMicroUsd: Long = 0,
    val style: AdStyle? = null,
    val logoUrl: String? = null,
)

data class DeveloperEarnings(
    val developerId: String,
    val todayMicroUsd: Long,
    val monthMicroUsd: Long,
    val lifetimeMicroUsd: Long,
    val impressionCount: Long,
) {
    companion object {
        /** micro-USD → "$X.XX", matching microToUsd() upstream. */
        fun microToUsd(micro: Long): String = "$" + String.format("%.2f", micro / 1_000_000.0)
    }
}

data class DeveloperProfile(
    val developerId: String,
    val connected: Boolean,
    val login: String? = null,
)
