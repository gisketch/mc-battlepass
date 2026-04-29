package dev.gisketch.chowkingdom.battlepass

class BattlepassPassDefinition {
    var id: String = ""
    var displayName: String = ""
    var description: String = ""
    var categories: MutableList<String> = mutableListOf("general")
    var xpEvents: MutableList<BattlepassXpEventDefinition> = mutableListOf()
    var progression: MutableList<BattlepassProgressionDefinition> = mutableListOf()
}

class BattlepassXpEventDefinition {
    var event: String = ""
    var xp: Int = 0
    var filters: MutableMap<String, String> = mutableMapOf()
}

class BattlepassProgressionDefinition {
    var xp: Int = 0
    var rewards: MutableList<BattlepassRewardDefinition> = mutableListOf()
}

class BattlepassRewardDefinition {
    var type: String = "item"
    var item: String = "minecraft:air"
    var quantity: Int = 1
    var data: MutableMap<String, String> = mutableMapOf()
}