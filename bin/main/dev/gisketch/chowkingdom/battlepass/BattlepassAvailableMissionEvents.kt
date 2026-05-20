package dev.gisketch.chowkingdom.battlepass

object BattlepassAvailableMissionEvents {
    private val descriptions = linkedMapOf(
        "minecraft:monster_killed" to "Defeat {goal} Monsters",
        "minecraft:entity_killed" to "Defeat {goal} Targets",
        "minecraft:crop_harvested" to "Harvest {goal} Crops",
        "minecraft:animal_bred" to "Breed {goal} Animals",
        "minecraft:villager_traded" to "Trade with Villagers {goal} Times",
        "minecraft:fish_caught" to "Catch {goal} Fish",
        "minecraft:blocks_traveled" to "Travel {goal} Blocks",
        "minecraft:travel_on_foot" to "Travel {goal} Blocks On Foot",
        "cobblemon:pokemon_mount_traveled" to "Travel {goal} Blocks On Pokemon",
        "cobblemon:pokemon_mount_land_traveled" to "Travel {goal} Blocks On Land Pokemon",
        "cobblemon:pokemon_mount_flying_traveled" to "Travel {goal} Blocks On Flying Pokemon",
        "minecraft:item_crafted" to "Craft {goal} Items",
        "minecraft:item_smelted" to "Smelt {goal} Items",
        "minecraft:item_eaten" to "Eat {goal} Foods",
        "farmersdelight:food_created" to "Create {goal} Farmer's Delight Foods",
        "farmersdelight:craft_food_created" to "Craft {goal} Farmer's Delight Foods",
        "farmersdelight:cook_food_created" to "Cook {goal} Farmer's Delight Foods",
        "farmersdelight:smelt_food_created" to "Smelt {goal} Farmer's Delight Foods",
        "cobblemon:pokedex_scanned" to "Scan {goal} Pokedex Entries",
        "cobblemon:pokemon_caught" to "Catch {goal} Pokemon",
        "cobblemon:pokemon_sent_out" to "Send Out {goal} Pokemon",
        "cobblemon:pokemon_friendship_maxed" to "Max Friendship with {goal} Pokemon",
        "cobblemon:scan_kanto_pokemon" to "Scan {goal} Kanto Pokemon",
        "cobblemon:catch_kanto_pokemon" to "Catch {goal} Kanto Pokemon",
        "cobblemon:scan_johto_pokemon" to "Scan {goal} Johto Pokemon",
        "cobblemon:catch_johto_pokemon" to "Catch {goal} Johto Pokemon",
        "cobblemon:scan_hoenn_pokemon" to "Scan {goal} Hoenn Pokemon",
        "cobblemon:catch_hoenn_pokemon" to "Catch {goal} Hoenn Pokemon",
        "cobblemon:scan_sinnoh_pokemon" to "Scan {goal} Sinnoh Pokemon",
        "cobblemon:catch_sinnoh_pokemon" to "Catch {goal} Sinnoh Pokemon",
        "cobblemon:scan_unova_pokemon" to "Scan {goal} Unova Pokemon",
        "cobblemon:catch_unova_pokemon" to "Catch {goal} Unova Pokemon",
        "cobblemon:scan_kalos_pokemon" to "Scan {goal} Kalos Pokemon",
        "cobblemon:catch_kalos_pokemon" to "Catch {goal} Kalos Pokemon",
        "cobblemon:scan_alola_pokemon" to "Scan {goal} Alola Pokemon",
        "cobblemon:catch_alola_pokemon" to "Catch {goal} Alola Pokemon",
        "cobblemon:scan_galar_pokemon" to "Scan {goal} Galar Pokemon",
        "cobblemon:catch_galar_pokemon" to "Catch {goal} Galar Pokemon",
        "cobblemon:scan_paldea_pokemon" to "Scan {goal} Paldea Pokemon",
        "cobblemon:catch_paldea_pokemon" to "Catch {goal} Paldea Pokemon",
        "quality_food:iron_quality_crop_harvested" to "Harvest {goal} Iron Quality Crops",
        "quality_food:gold_quality_crop_harvested" to "Harvest {goal} Gold Quality Crops",
        "quality_food:diamond_quality_crop_harvested" to "Harvest {goal} Diamond Quality Crops",
        "quality_food:iron_quality_food_cooked" to "Cook {goal} Iron Quality Foods",
        "quality_food:gold_quality_food_cooked" to "Cook {goal} Gold Quality Foods",
        "quality_food:diamond_quality_food_cooked" to "Cook {goal} Diamond Quality Foods",
        "quality_food:iron_quality_food_eaten" to "Eat {goal} Iron Quality Foods",
        "quality_food:gold_quality_food_eaten" to "Eat {goal} Gold Quality Foods",
        "quality_food:diamond_quality_food_eaten" to "Eat {goal} Diamond Quality Foods",
        "minecraft:quality_food_smelted" to "Smelt {goal} Quality Foods",
        "farmersdelight:quality_food_cooked" to "Cook {goal} Quality Farmer's Delight Foods",
        "farmersdelight:cutting_board_used" to "Use a Cutting Board {goal} Times",
        "farmersdelight:cutting_board_outputs" to "Make {goal} Cutting Board Outputs",
        "farmersdelight:knife_used" to "Use a Knife {goal} Times",
        "farmersdelight:cooking_pot_meal_cooked" to "Cook {goal} Cooking Pot Meals",
        "farmersdelight:feast_served" to "Serve {goal} Feast Portions",
        "farmersdelight:wild_crop_harvested" to "Harvest {goal} Wild Crops",
        "farmersdelight:meal_eaten" to "Eat {goal} Farmer's Delight Meals",
        "gisketchs_chowkingdom_mod:shipping_bin_iron_quality_food_sold" to "Ship {goal} Iron Quality Foods",
        "gisketchs_chowkingdom_mod:shipping_bin_gold_quality_food_sold" to "Ship {goal} Gold Quality Foods",
        "gisketchs_chowkingdom_mod:shipping_bin_diamond_quality_food_sold" to "Ship {goal} Diamond Quality Foods",
        "gisketchs_chowkingdom_mod:shipping_bin_value_sold" to "Ship {goal} Chowcoins Worth",
        "gisketchs_chowkingdom_mod:shipping_bin_quality_food_value_sold" to "Ship {goal} Quality Chowcoins Worth",
        "gisketchs_chowkingdom_mod:shop_value_sold" to "Sell {goal} Chowcoins Worth of Items",
        "gisketchs_chowkingdom_mod:shop_value_bought" to "Buy {goal} Chowcoins Worth of Items",
        "gisketchs_chowkingdom_mod:npc_friendship_level_reached" to "Reach NPC Friendship Level {goal}",
        "gisketchs_chowkingdom_mod:npc_quest_completed" to "Complete {goal} NPC Quests",
        "gisketchs_chowkingdom_mod:npc_quiz_answered_correctly" to "Answer {goal} NPC Quizzes Correctly",
        "gisketchs_chowkingdom_mod:npc_pokemon_battle_won" to "Win {goal} NPC Pokemon Battles",
        "gisketchs_chowkingdom_mod:npc_sparring_won" to "Win {goal} NPC Sparring Matches",
        "gisketchs_chowkingdom_mod:boss_first_clear" to "First-Clear {goal} Bosses",
        "gisketchs_chowkingdom_mod:biome_discovered" to "Discover {goal} Biomes",
        "gisketchs_chowkingdom_mod:structure_discovered" to "Discover {goal} Structures",
        "gisketchs_chowkingdom_mod:gym_battle_attempted" to "Attempt {goal} Gym Battles",
        "gisketchs_chowkingdom_mod:gym_battle_won" to "Win {goal} Gym Battles",
        "gisketchs_chowkingdom_mod:gym_badge_earned" to "Earn {goal} Gym Badges",
        "gisketchs_chowkingdom_mod:gym_leader_defeated" to "Defeat {goal} Gym Leaders",
        "gisketchs_chowkingdom_mod:league_completed" to "Complete {goal} Pokemon Leagues",
        "gisketchs_chowkingdom_mod:teammate_revived" to "Revive {goal} Teammates",
    )

    fun ids(): List<String> = (descriptions.keys + cobblemonAliases()).distinct().sorted()

    fun description(eventId: String): String = descriptions[eventId] ?: eventIdToDescription(eventId)

    private fun eventIdToDescription(eventId: String): String = eventId
        .substringAfter(':')
        .replace('_', ' ')
        .replaceFirstChar { character -> character.uppercase() }
        .replace(Regex("\\bgoal\\b"), "{goal}")

    private fun cobblemonAliases(): List<String> {
        val types = listOf("normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy")
        val categories = listOf("legendary", "mythical", "starter")
        return types.flatMap { type ->
            listOf("cobblemon:catch_${type}_type", "cobblemon:send_out_${type}_type", "cobblemon:max_friendship_${type}_type")
        } + categories.flatMap { category ->
            listOf("cobblemon:catch_${category}_pokemon", "cobblemon:send_out_${category}_pokemon", "cobblemon:max_friendship_${category}_pokemon")
        }
    }
}
