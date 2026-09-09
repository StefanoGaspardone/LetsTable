package com.backend.models.dtos

import com.backend.models.entities.ExpansionRef
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "items")
@JsonIgnoreProperties(ignoreUnknown = true)
data class BggSearchResponseXml(
    @field:JacksonXmlProperty(localName = "item")
    @field:JacksonXmlElementWrapper(useWrapping = false)
    val items: List<BggSearchItemXml> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BggSearchItemXml(
    @field:JacksonXmlProperty(isAttribute = true)
    val id: Long,

    @field:JacksonXmlProperty(localName = "name")
    val name: BggNameXml? = null,

    @field:JacksonXmlProperty(localName = "yearpublished")
    val yearPublished: BggValueXml? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BggNameXml(
    @field:JacksonXmlProperty(isAttribute = true)
    val value: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BggValueXml(
    @field:JacksonXmlProperty(isAttribute = true)
    val value: String,
)

@JacksonXmlRootElement(localName = "items")
@JsonIgnoreProperties(ignoreUnknown = true)
data class BggThingResponseXml(
    @field:JacksonXmlProperty(localName = "item")
    @field:JacksonXmlElementWrapper(useWrapping = false)
    val items: List<BggThingItemXml> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BggThingItemXml(
    @field:JacksonXmlProperty(isAttribute = true)
    val id: Long,

    @field:JacksonXmlProperty(localName = "thumbnail")
    val thumbnail: String? = null,

    @field:JacksonXmlProperty(localName = "image")
    val image: String? = null,

    @field:JacksonXmlProperty(localName = "name")
    @field:JacksonXmlElementWrapper(useWrapping = false)
    val names: List<BggThingNameXml> = emptyList(),

    @field:JacksonXmlProperty(localName = "description")
    val description: String? = null,

    @field:JacksonXmlProperty(localName = "yearpublished")
    val yearPublished: BggValueXml? = null,

    @field:JacksonXmlProperty(localName = "minplayers")
    val minPlayers: BggValueXml? = null,

    @field:JacksonXmlProperty(localName = "maxplayers")
    val maxPlayers: BggValueXml? = null,

    @field:JacksonXmlProperty(localName = "playingtime")
    val playingTime: BggValueXml? = null,

    @field:JacksonXmlProperty(localName = "minage")
    val minAge: BggValueXml? = null,

    @field:JacksonXmlProperty(localName = "minplaytime")
    val minPlayTime: BggValueXml? = null,

    @field:JacksonXmlProperty(localName = "maxplaytime")
    val maxPlayTime: BggValueXml? = null,

    @field:JacksonXmlProperty(localName = "link")
    @field:JacksonXmlElementWrapper(useWrapping = false)
    val links: List<BggThingLinkXml> = emptyList(),

    @field:JacksonXmlProperty(isAttribute = true)
    val type: String? = null,

    @field:JacksonXmlProperty(localName = "poll-summary")
    @field:JacksonXmlElementWrapper(useWrapping = false)
    val pollSummaries: List<BggThingPollSummaryXml> = emptyList(),

    @field:JacksonXmlProperty(localName = "statistics")
    val statistics: BggThingStatisticsXml? = null,
) {
    fun primaryName(): String? = names.firstOrNull { it.type == "primary" }?.value ?: names.firstOrNull()?.value

    fun expansionRefs(): List<ExpansionRef> =
        links.filter { it.type == "boardgameexpansion" }
            .mapNotNull { link ->
                link.id.toLongOrNull()?.let { id -> ExpansionRef(id, link.value) }
            }

    fun pollSummaryValue(name: String): String? =
        pollSummaries.firstOrNull { it.name == "suggested_numplayers" }
            ?.results
            ?.firstOrNull { it.name == name }
            ?.value

    fun baseGameRef(): ExpansionRef? =
        links.firstOrNull { it.type == "boardgameexpansion" && it.inbound == true }
            ?.let { link -> link.id.toLongOrNull()?.let { id -> ExpansionRef(id, link.value) } }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class BggThingNameXml(
    @field:JacksonXmlProperty(isAttribute = true)
    val type: String,

    @field:JacksonXmlProperty(isAttribute = true)
    val value: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BggThingLinkXml(
    @field:JacksonXmlProperty(isAttribute = true)
    val type: String,

    @field:JacksonXmlProperty(isAttribute = true)
    val id: String,

    @field:JacksonXmlProperty(isAttribute = true)
    val value: String,

    @field:JacksonXmlProperty(isAttribute = true)
    val inbound: Boolean? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BggThingPollSummaryXml(
    @field:JacksonXmlProperty(isAttribute = true)
    val name: String,

    @field:JacksonXmlProperty(localName = "result")
    @field:JacksonXmlElementWrapper(useWrapping = false)
    val results: List<BggThingPollSummaryResultXml> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BggThingPollSummaryResultXml(
    @field:JacksonXmlProperty(isAttribute = true)
    val name: String,

    @field:JacksonXmlProperty(isAttribute = true)
    val value: String,
)

@JacksonXmlRootElement(localName = "items")
@JsonIgnoreProperties(ignoreUnknown = true)
data class BggHotResponseXml(
    @field:JacksonXmlProperty(localName = "item")
    @field:JacksonXmlElementWrapper(useWrapping = false)
    val items: List<BggHotItemXml> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BggHotItemXml(
    @field:JacksonXmlProperty(isAttribute = true)
    val id: Long,

    @field:JacksonXmlProperty(isAttribute = true)
    val rank: Int,

    @field:JacksonXmlProperty(localName = "thumbnail")
    val thumbnail: BggValueXml? = null,

    @field:JacksonXmlProperty(localName = "name")
    val name: BggValueXml? = null,

    @field:JacksonXmlProperty(localName = "yearpublished")
    val yearPublished: BggValueXml? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BggThingStatisticsXml(
    @field:JacksonXmlProperty(localName = "ratings")
    val ratings: BggThingRatingsXml? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BggThingRatingsXml(
    @field:JacksonXmlProperty(localName = "averageweight")
    val averageWeight: BggValueXml? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CardSetsByGameResponse(
    val cardSets: List<CardSetJson> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CardSetJson(
    val cardTypes: List<CardTypeJson> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CardTypeJson(
    val name: String? = null,
    val height: String? = null,
    val width: String? = null,
    val quantity: String? = null,

    @field:JsonProperty("quantity_note")
    val quantityNote: String? = null,
)