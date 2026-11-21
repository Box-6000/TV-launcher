package com.launch.installer

enum class Phase {
    Scanning,
    Connecting,
    WaitAllow,
    Review,
    Working,
    Done,
    Failed,
}

enum class DeviceKind {
    Ready,
    NeedsDebug,
    EncryptedDebug,
    OtherSystem,
}

enum class TvKind {
    Tv,
    Box,
    Fire,
    Phone,
    Unknown,
}

data class FoundTv(
    val host: String,
    val port: Int?,
    val tlsPort: Int?,
    val name: String,
    val detail: String,
    val kind: DeviceKind,
    val hint: String,
)

data class Suggestion(
    val packageName: String,
    val title: String,
    val reason: String,
    val checked: Boolean,
    val alreadyOff: Boolean,
    val recommended: Boolean,
)

data class TvProfile(
    val host: String,
    val port: Int,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val androidRelease: String,
    val sdk: Int,
    val serial: String,
    val kind: TvKind,
    val brandTitle: String,
    val previousHome: String?,
    val note: String,
    val launcherVersion: String,
)

data class Milestone(
    val title: String,
    val detail: String,
    val done: Boolean,
    val warn: Boolean,
)

data class RestorePoint(
    val serial: String,
    val host: String,
    val previousHome: String?,
    val disabled: List<String>,
)

data class UiState(
    val phase: Phase = Phase.Scanning,
    val status: String = "Ищу телевизоры в сети…",
    val devices: List<FoundTv> = emptyList(),
    val profile: TvProfile? = null,
    val suggestions: List<Suggestion> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val error: String? = null,
    val log: List<String> = emptyList(),
    val confirmUnknown: Boolean = false,
    val restore: RestorePoint? = null,
    val wifiMissing: Boolean = false,
    val bundledVersion: String = "",
)
