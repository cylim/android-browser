package app.jrny.browser.ui

/** The two window layouts (SPEC, "Layout"). The choice depends on window width, never on device type. */
enum class BrowserLayout {
    /** A vertical Sidebar with the Tabs and the Profile switcher. */
    Sidebar,

    /** An address field along the bottom edge. */
    BottomBar;

    companion object {
        /** Windows at least this wide, in dp, get the [Sidebar]. */
        const val SIDEBAR_MIN_WIDTH_DP = 840f

        fun forWindowWidth(widthDp: Float): BrowserLayout =
            if (widthDp >= SIDEBAR_MIN_WIDTH_DP) Sidebar else BottomBar
    }
}
