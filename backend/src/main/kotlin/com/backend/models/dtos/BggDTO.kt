package com.backend.models.dtos

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "items")
data class BggSearchResponseXml(
    @field:JacksonXmlProperty(localName = "item")
    @field:JacksonXmlElementWrapper(useWrapping = false)
    val items: List<BggSearchItemXml> = emptyList(),
)

data class BggSearchItemXml(
    @field:JacksonXmlProperty(isAttribute = true)
    val id: Long,

    @field:JacksonXmlProperty(localName = "name")
    val name: BggNameXml? = null,

    @field:JacksonXmlProperty(localName = "yearpublished")
    val yearPublished: BggValueXml? = null,
)

data class BggNameXml(
    @field:JacksonXmlProperty(isAttribute = true)
    val value: String,
)

data class BggValueXml(
    @field:JacksonXmlProperty(isAttribute = true)
    val value: String,
)

@JacksonXmlRootElement(localName = "items")
data class BggThingResponseXml(
    @field:JacksonXmlProperty(localName = "item")
    @field:JacksonXmlElementWrapper(useWrapping = false)
    val items: List<BggThingItemXml> = emptyList(),
)

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
) {
    fun primaryName(): String? = names.firstOrNull { it.type == "primary" }?.value ?: names.firstOrNull()?.value
}

data class BggThingNameXml(
    @field:JacksonXmlProperty(isAttribute = true)
    val type: String,

    @field:JacksonXmlProperty(isAttribute = true)
    val value: String,
)

@JacksonXmlRootElement(localName = "items")
data class BggHotResponseXml(
    @field:JacksonXmlProperty(localName = "item")
    @field:JacksonXmlElementWrapper(useWrapping = false)
    val items: List<BggHotItemXml> = emptyList(),
)

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