package dev.gisketch.chowkingdom.battlepass

import com.google.gson.annotations.SerializedName

class BattlepassPassDefinition {
    var id: String = ""
    var displayName: String = ""
    var description: String = ""
    var titleTexture: String = ""
    var titleTextureWidth: Int = 0
    var titleTextureHeight: Int = 0
    var categories: MutableList<String> = mutableListOf("general")
    var xpEvents: MutableList<BattlepassXpEventDefinition> = mutableListOf()
    @SerializedName("permanent_events")
    var permanentEvents: MutableList<BattlepassXpEventDefinition> = mutableListOf()
    @SerializedName("daily_events")
    var dailyEvents: BattlepassRotatingMissionDefinition = BattlepassRotatingMissionDefinition()
    @SerializedName("weekly_events")
    var weeklyEvents: BattlepassRotatingMissionDefinition = BattlepassRotatingMissionDefinition()
    var progression: MutableList<BattlepassProgressionDefinition> = mutableListOf()
}

class BattlepassXpEventDefinition {
    var id: String = ""
    var event: String = ""
    var type: String = "repeating"
    @SerializedName("event_desc")
    var eventDesc: String = ""
    var xp: Int = 0
    var progress: Int = 0
    @SerializedName("progress_goals")
    var progressGoals: MutableList<Int> = mutableListOf()
    @SerializedName("progress_xp")
    var progressXp: MutableList<Int> = mutableListOf()
    @SerializedName("xp_cap")
    var xpCap: Int = 0
    var filters: MutableMap<String, String> = mutableMapOf()
    @SerializedName("rotation_group")
    var rotationGroup: String = ""
}

class BattlepassRotatingMissionDefinition {
    var count: Int = 0
    var events: MutableList<BattlepassXpEventDefinition> = mutableListOf()
    @SerializedName("time_zone")
    var timeZone: String = "GMT+8"
    @SerializedName("reset_hour")
    var resetHour: Int = 5
    @SerializedName("reset_minute")
    var resetMinute: Int = 0
    @SerializedName("reset_on_day")
    var resetOnDay: String = "Sunday"
}

class BattlepassProgressionDefinition {
    var xp: Int = 0
    var rewards: MutableList<BattlepassRewardDefinition> = mutableListOf()
}

class BattlepassRewardDefinition {
    var type: String = "item"
    var item: String = "minecraft:air"
    var quantity: Int = 1
    @SerializedName("is_prominent")
    var isProminent: Boolean = false
    var data: MutableMap<String, String> = mutableMapOf()
}