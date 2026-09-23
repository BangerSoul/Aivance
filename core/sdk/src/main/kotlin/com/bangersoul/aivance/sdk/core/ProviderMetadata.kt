package com.bangersoul.aivance.sdk.core

/**
 * Defines the functional category of a provider.
 */
enum class ProviderType {
    AI,
    JOB,
    ENRICHMENT
}

/**
 * Describes a configuration field required by a provider.
 */
data class ConfigField(
    val key: String,
    val label: String,
    val isRequired: Boolean = true,
    val isSensitive: Boolean = false,
    val hint: String? = null,
    val fieldType: FieldType = FieldType.TEXT
)

enum class FieldType {
    TEXT,
    PASSWORD,
    NUMBER,
    DROPDOWN
}

/**
 * Metadata describing an AI or Job provider.
 *
 * @property id Unique identifier for the provider.
 * @property name Human-readable name of the provider.
 * @property type The functional category (AI or Job).
 * @property version Version string of the provider implementation.
 * @property description Brief description of what the provider does.
 * @property icon Optional icon resource name.
 * @property author The creator of the provider.
 * @property configFields List of fields required to configure this provider.
 * @property supportedModels List of models supported (if applicable).
 */
data class ProviderMetadata(
    val id: String,
    val name: String,
    val type: ProviderType,
    val version: String,
    val description: String,
    val icon: String? = null,
    val author: String,
    val isLocal: Boolean = false,
    val configFields: List<ConfigField> = emptyList(),
    val supportedModels: List<String> = emptyList()
)
