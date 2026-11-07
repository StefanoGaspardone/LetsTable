package com.backend.unit.services

import com.backend.models.dtos.BggThingItemXml
import com.backend.models.dtos.BggThingLinkXml
import com.backend.models.entities.Category
import com.backend.models.entities.Mechanic
import com.backend.repositories.CategoryRepository
import com.backend.repositories.MechanicRepository
import com.backend.services.GameTermService
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.dao.DataIntegrityViolationException
import java.util.UUID

@ExtendWith(MockKExtension::class)
class GameTermServiceTest {

    @MockK
    private lateinit var mechanicRepository: MechanicRepository

    @MockK
    private lateinit var categoryRepository: CategoryRepository

    private lateinit var gameTermService: GameTermService

    private val savedMechanics: CapturingSlot<Iterable<Mechanic>> = slot()
    private val savedCategories: CapturingSlot<Iterable<Category>> = slot()

    @BeforeEach
    fun setUp() {
        gameTermService = GameTermService(mechanicRepository, categoryRepository)

        every { mechanicRepository.findAllByBggIdIn(any()) } returns emptyList()
        every { categoryRepository.findAllByBggIdIn(any()) } returns emptyList()
        every { mechanicRepository.saveAll(capture(savedMechanics)) } answers {
            savedMechanics.captured.onEach { if(it.id == null) it.id = UUID.randomUUID() }.toList()
        }
        every { categoryRepository.saveAll(capture(savedCategories)) } answers {
            savedCategories.captured.onEach { if(it.id == null) it.id = UUID.randomUUID() }.toList()
        }
    }

    private fun mechanicLink(id: Long, name: String) = BggThingLinkXml(type = "boardgamemechanic", id = id.toString(), value = name)

    private fun categoryLink(id: Long, name: String) = BggThingLinkXml(type = "boardgamecategory", id = id.toString(), value = name)

    private fun details(id: Long, vararg links: BggThingLinkXml) = BggThingItemXml(id = id, links = links.toList())

    private fun mechanic(bggId: Long, name: String, nameIt: String? = null) = Mechanic(id = UUID.randomUUID(), bggId = bggId, name = name, nameIt = nameIt)

    private fun category(bggId: Long, name: String, nameIt: String? = null) = Category(id = UUID.randomUUID(), bggId = bggId, name = name, nameIt = nameIt)

    @Nested
    @DisplayName("resolve")
    inner class ResolveTests {

        @Test
        fun `should not query anything when there are no details`() {
            val terms = gameTermService.resolve(emptyList())

            assertThat(terms.mechanics).isEmpty()
            assertThat(terms.categories).isEmpty()
            verify(exactly = 0) { mechanicRepository.findAllByBggIdIn(any()) }
            verify(exactly = 0) { categoryRepository.findAllByBggIdIn(any()) }
        }

        @Test
        fun `should not query anything when the games have no mechanics or categories`() {
            gameTermService.resolve(listOf(details(1L, BggThingLinkXml(type = "boardgamedesigner", id = "5", value = "Uwe Rosenberg"))))

            verify(exactly = 0) { mechanicRepository.findAllByBggIdIn(any()) }
            verify(exactly = 0) { categoryRepository.findAllByBggIdIn(any()) }
        }

        @Test
        fun `should create the mechanics that do not exist yet`() {
            val terms = gameTermService.resolve(listOf(details(1L, mechanicLink(2082L, "Worker Placement"), mechanicLink(2041L, "Open Drafting"))))

            assertThat(savedMechanics.captured.map { it.bggId to it.name }).containsExactly(2082L to "Worker Placement", 2041L to "Open Drafting")
            assertThat(terms.mechanics.keys).containsExactlyInAnyOrder(2082L, 2041L)
            assertThat(terms.mechanics[2082L]?.id).isNotNull()
        }

        @Test
        fun `should reuse existing mechanics without saving them`() {
            val existing = mechanic(2082L, "Worker Placement", "Piazzamento lavoratori")
            every { mechanicRepository.findAllByBggIdIn(listOf(2082L)) } returns listOf(existing)

            val terms = gameTermService.resolve(listOf(details(1L, mechanicLink(2082L, "Worker Placement"))))

            assertThat(terms.mechanics[2082L]).isSameAs(existing)
            verify(exactly = 0) { mechanicRepository.saveAll(any<Iterable<Mechanic>>()) }
        }

        @Test
        fun `should rename a mechanic renamed on BGG and keep its Italian name`() {
            val existing = mechanic(2082L, "Worker Placment", "Piazzamento lavoratori")
            every { mechanicRepository.findAllByBggIdIn(listOf(2082L)) } returns listOf(existing)

            val terms = gameTermService.resolve(listOf(details(1L, mechanicLink(2082L, "Worker Placement"))))

            assertThat(savedMechanics.captured.single()).isSameAs(existing)
            assertThat(existing.name).isEqualTo("Worker Placement")
            assertThat(existing.nameIt).isEqualTo("Piazzamento lavoratori")
            assertThat(terms.mechanics[2082L]).isSameAs(existing)
        }

        @Test
        fun `should combine existing and new mechanics in the result`() {
            val existing = mechanic(2082L, "Worker Placement")
            every { mechanicRepository.findAllByBggIdIn(listOf(2082L, 2041L)) } returns listOf(existing)

            val terms = gameTermService.resolve(listOf(details(1L, mechanicLink(2082L, "Worker Placement"), mechanicLink(2041L, "Open Drafting"))))

            assertThat(savedMechanics.captured.map { it.bggId }).containsExactly(2041L)
            assertThat(terms.mechanics.keys).containsExactlyInAnyOrder(2082L, 2041L)
            assertThat(terms.mechanics[2082L]).isSameAs(existing)
        }

        @Test
        fun `should resolve a term shared by several games only once`() {
            gameTermService.resolve(
                listOf(
                    details(1L, mechanicLink(2082L, "Worker Placement")),
                    details(2L, mechanicLink(2082L, "Worker Placement"), mechanicLink(2041L, "Open Drafting")),
                )
            )

            verify(exactly = 1) { mechanicRepository.findAllByBggIdIn(listOf(2082L, 2041L)) }
            assertThat(savedMechanics.captured.map { it.bggId }).containsExactly(2082L, 2041L)
        }

        @Test
        fun `should reload the mechanics when another request inserted them concurrently`() {
            val concurrent = mechanic(2082L, "Worker Placement")
            every { mechanicRepository.findAllByBggIdIn(listOf(2082L)) } returnsMany listOf(emptyList(), listOf(concurrent))
            every { mechanicRepository.saveAll(any<Iterable<Mechanic>>()) } throws DataIntegrityViolationException("duplicate key value violates unique constraint")

            val terms = gameTermService.resolve(listOf(details(1L, mechanicLink(2082L, "Worker Placement"))))

            assertThat(terms.mechanics[2082L]).isSameAs(concurrent)
        }

        @Test
        fun `should create, reuse and reload categories the same way`() {
            val existing = category(1089L, "Animals", "Animali")
            every { categoryRepository.findAllByBggIdIn(listOf(1089L, 1084L)) } returns listOf(existing)

            val terms = gameTermService.resolve(listOf(details(1L, categoryLink(1089L, "Animals"), categoryLink(1084L, "Environmental"))))

            assertThat(savedCategories.captured.map { it.bggId to it.name }).containsExactly(1084L to "Environmental")
            assertThat(terms.categories[1089L]).isSameAs(existing)
            assertThat(terms.categories.keys).containsExactlyInAnyOrder(1089L, 1084L)
        }

        @Test
        fun `should reload the categories when another request inserted them concurrently`() {
            val concurrent = category(1089L, "Animals")
            every { categoryRepository.findAllByBggIdIn(listOf(1089L)) } returnsMany listOf(emptyList(), listOf(concurrent))
            every { categoryRepository.saveAll(any<Iterable<Category>>()) } throws DataIntegrityViolationException("duplicate key value violates unique constraint")

            val terms = gameTermService.resolve(listOf(details(1L, categoryLink(1089L, "Animals"))))

            assertThat(terms.categories[1089L]).isSameAs(concurrent)
        }

        @Test
        fun `should keep mechanics and categories separate`() {
            val terms = gameTermService.resolve(listOf(details(1L, mechanicLink(100L, "Same id as category"), categoryLink(100L, "Same id as mechanic"))))

            assertThat(terms.mechanics[100L]?.name).isEqualTo("Same id as category")
            assertThat(terms.categories[100L]?.name).isEqualTo("Same id as mechanic")
        }

        @Test
        fun `should propagate other persistence errors`() {
            every { mechanicRepository.saveAll(any<Iterable<Mechanic>>()) } throws RuntimeException("DB down")

            assertThatThrownBy { gameTermService.resolve(listOf(details(1L, mechanicLink(2082L, "Worker Placement")))) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB down")
        }
    }

    @Nested
    @DisplayName("getMechanics")
    inner class GetMechanicsTests {

        @Test
        fun `should prefer the Italian name and sort case-insensitively by the displayed name`() {
            every { mechanicRepository.findAll() } returns listOf(
                mechanic(2082L, "Worker Placement", "piazzamento lavoratori"),
                mechanic(2041L, "Open Drafting", "Draft aperto"),
                mechanic(2080L, "Area Majority / Influence"),
            )

            val result = gameTermService.getMechanics()

            assertThat(result.map { it.name }).containsExactly("Area Majority / Influence", "Draft aperto", "piazzamento lavoratori")
            assertThat(result.map { it.bggId }).containsExactly(2080L, 2041L, 2082L)
        }

        @Test
        fun `should return an empty list when there are no mechanics`() {
            every { mechanicRepository.findAll() } returns emptyList()

            assertThat(gameTermService.getMechanics()).isEmpty()
        }

        @Test
        fun `should rethrow repository errors`() {
            every { mechanicRepository.findAll() } throws RuntimeException("DB down")

            assertThatThrownBy { gameTermService.getMechanics() }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB down")
        }
    }

    @Nested
    @DisplayName("getCategories")
    inner class GetCategoriesTests {

        @Test
        fun `should prefer the Italian name and sort case-insensitively by the displayed name`() {
            every { categoryRepository.findAll() } returns listOf(
                category(1021L, "Economic", "economico"),
                category(1089L, "Animals", "Animali"),
                category(1084L, "Environmental"),
            )

            val result = gameTermService.getCategories()

            assertThat(result.map { it.name }).containsExactly("Animali", "economico", "Environmental")
        }

        @Test
        fun `should return an empty list when there are no categories`() {
            every { categoryRepository.findAll() } returns emptyList()

            assertThat(gameTermService.getCategories()).isEmpty()
        }

        @Test
        fun `should rethrow repository errors`() {
            every { categoryRepository.findAll() } throws RuntimeException("DB down")

            assertThatThrownBy { gameTermService.getCategories() }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB down")
        }
    }
}