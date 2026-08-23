package com.backend.seeders

import com.backend.models.entities.Game
import com.backend.models.entities.User
import com.backend.models.entities.Wishlist
import com.backend.models.entities.WishlistItem
import com.backend.models.entities.WishlistMember
import com.backend.repositories.WishlistItemRepository
import com.backend.repositories.WishlistMemberRepository
import com.backend.repositories.WishlistRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class WishlistSeeder(
    private val wishlistRepository: WishlistRepository,
    private val wishlistItemRepository: WishlistItemRepository,
    private val wishlistMemberRepository: WishlistMemberRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun seed(users: List<User>, games: List<Game>) {
        logger.debug("\n\t[DEBUG] [wishlist_seeder][seed] Seeding demo wishlists")

        try {
            val (marco, anna, luca, elena) = users
            val (arkNova, seti, dune, twilight) = games

            val defaultWishlists = users.associateWith { user ->
                wishlistRepository.findAllAccessibleByUser(user.id!!)
                    .firstOrNull { it.isDefault }
                    ?: wishlistRepository.save(
                        Wishlist(name = "La mia wishlist", owner = user, isShared = false, isDefault = true)
                    )
            }

            addItemIfMissing(defaultWishlists[marco]!!, dune, marco)
            addItemIfMissing(defaultWishlists[anna]!!, arkNova, anna)
            addItemIfMissing(defaultWishlists[luca]!!, twilight, luca)
            addItemIfMissing(defaultWishlists[elena]!!, seti, elena)
            addItemIfMissing(defaultWishlists[elena]!!, arkNova, elena)

            val sharedWishlist = wishlistRepository.findAllAccessibleByUser(marco.id!!)
                .firstOrNull { it.name == "Serata del giovedì" }
                ?: wishlistRepository.save(
                    Wishlist(name = "Serata del giovedì", owner = marco, isShared = true, isDefault = false)
                )

            listOf(anna, luca).forEach { member ->
                if(!wishlistMemberRepository.existsByWishlistIdAndUserId(sharedWishlist.id!!, member.id!!))
                    wishlistMemberRepository.save(WishlistMember(wishlist = sharedWishlist, user = member))
            }

            addItemIfMissing(sharedWishlist, twilight, marco)
            addItemIfMissing(sharedWishlist, seti, anna)

            logger.info("\n\t[INFO] [wishlist_seeder][seed] Demo wishlists seeded")
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_seeder][seed] Error seeding wishlists: {}", e.message)
            throw e
        }
    }

    private fun addItemIfMissing(wishlist: Wishlist, game: Game, addedBy: User) {
        if(!wishlistItemRepository.existsByWishlistIdAndGameId(wishlist.id!!, game.id!!))
            wishlistItemRepository.save(WishlistItem(wishlist = wishlist, game = game, addedBy = addedBy))
    }
}