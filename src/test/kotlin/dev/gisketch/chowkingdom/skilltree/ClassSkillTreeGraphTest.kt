package dev.gisketch.chowkingdom.skilltree

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClassSkillTreeGraphTest {
    @Test
    fun `warrior can spend five points through a legal connected path`() {
        val graph = TestSkillTreeGraph.load()
        val root = assertNotNull(graph.skillIdForDefinition("warrior_root"))
        val unlocked = linkedSetOf<String>()

        repeat(5) {
            val pointsLeft = 5 - unlocked.size
            val available = graph.availableSkillIds(root, unlocked, pointsLeft)
            assertTrue(available.isNotEmpty(), "Warrior should have an available node with $pointsLeft point(s) left after $unlocked")
            val next = available.firstOrNull { skillId -> graph.definitionId(skillId) == "warrior_boost" } ?: available.first()
            unlocked += next
        }

        assertEquals(5, unlocked.size)
    }

    @Test
    fun `warrior exclusive branch does not block unrelated boost progress`() {
        val graph = TestSkillTreeGraph.load()
        val root = assertNotNull(graph.skillIdForDefinition("warrior_root"))
        val branchA = assertNotNull(graph.skillIdForDefinition("warrior_spec_a_modifier_1"))
        val branchB = assertNotNull(graph.skillIdForDefinition("warrior_spec_b_modifier_1"))
        val unlocked = setOf(branchB)

        assertEquals("Blocked by another branch", graph.blockedReason(root, branchA, unlocked, pointsLeft = 4))
        val availableDefinitions = graph.availableSkillIds(root, unlocked, pointsLeft = 4).mapNotNull(graph::definitionId).toSet()
        assertTrue("warrior_boost" in availableDefinitions, "First warrior boost should remain reachable after choosing a branch")
    }
}

private class TestSkillTreeGraph(
    private val nodes: Map<String, TestSkillNode>,
    private val normalConnections: List<Pair<String, String>>,
    private val exclusiveConnections: List<Pair<String, String>>,
) {
    private val rootTrees: Map<String, Set<String>> = nodes.values.filter(TestSkillNode::root).associate { root ->
        root.id to connectedComponent(root.id).filter { id -> id in nodes }.toSet()
    }

    fun skillIdForDefinition(definitionId: String): String? =
        nodes.values.firstOrNull { node -> node.definitionId == definitionId }?.id

    fun definitionId(skillId: String): String? = nodes[skillId]?.definitionId

    fun availableSkillIds(rootId: String, unlockedPaidSkillIds: Set<String>, pointsLeft: Int): Set<String> =
        rootTrees[rootId].orEmpty().filter { skillId -> blockedReason(rootId, skillId, unlockedPaidSkillIds, pointsLeft).isBlank() }.toSet()

    fun blockedReason(rootId: String, skillId: String, unlockedPaidSkillIds: Set<String>, pointsLeft: Int): String {
        val node = nodes[skillId] ?: return "Unknown skill"
        val treeIds = rootTrees[rootId].orEmpty()
        val unlocked = unlockedPaidSkillIds + rootId
        if (skillId !in treeIds) return "Skill is outside this path"
        if (node.root || skillId in unlocked) return "Already unlocked"
        if (pointsLeft < 1) return "Need 1 skill point"
        if (neighbors(skillId).none { neighbor -> neighbor in unlocked }) return "Requires a connected unlocked skill"
        if (exclusiveNeighbors(skillId).any { other -> other in unlocked }) return "Blocked by another branch"
        return ""
    }

    private fun connectedComponent(root: String): List<String> {
        val seen = linkedSetOf(root)
        val queue = ArrayDeque<String>()
        queue += root
        while (queue.isNotEmpty()) {
            neighbors(queue.removeFirst()).forEach { next ->
                if (seen.add(next)) queue += next
            }
        }
        return seen.toList()
    }

    private fun neighbors(skillId: String): Set<String> =
        normalConnections.flatMap { (a, b) ->
            when (skillId) {
                a -> listOf(b)
                b -> listOf(a)
                else -> emptyList()
            }
        }.toSet()

    private fun exclusiveNeighbors(skillId: String): Set<String> =
        exclusiveConnections.flatMap { (a, b) ->
            when (skillId) {
                a -> listOf(b)
                b -> listOf(a)
                else -> emptyList()
            }
        }.toSet()

    companion object {
        private const val CATEGORY_PATH = "resourcepacks/ckdm_skill_tree_changes/data/skill_tree_rpgs/puffish_skills/categories/skill_tree_rpgs"

        fun load(): TestSkillTreeGraph {
            val skills = readJson("$CATEGORY_PATH/skills.json")
            val connections = readJson("$CATEGORY_PATH/connections.json")
            val nodes = skills.entrySet().associate { (id, value) ->
                val obj = value.asJsonObject
                id to TestSkillNode(id, obj.get("definition").asString, obj.get("root")?.asBoolean == true)
            }
            return TestSkillTreeGraph(
                nodes = nodes,
                normalConnections = connectionPairs(connections.getAsJsonObject("normal")),
                exclusiveConnections = connectionPairs(connections.getAsJsonObject("exclusive")),
            )
        }

        private fun readJson(path: String): JsonObject {
            val stream = TestSkillTreeGraph::class.java.classLoader.getResourceAsStream(path)
                ?: error("Missing test skill tree resource: $path")
            return InputStreamReader(stream).use { reader -> JsonParser.parseReader(reader).asJsonObject }
        }

        private fun connectionPairs(group: JsonObject?): List<Pair<String, String>> =
            group?.getAsJsonArray("bidirectional")?.mapNotNull { entry ->
                val pair = entry.asJsonArray
                val first = pair.getOrNull(0)?.asString ?: return@mapNotNull null
                val second = pair.getOrNull(1)?.asString ?: return@mapNotNull null
                first to second
            }.orEmpty()

        private fun com.google.gson.JsonArray.getOrNull(index: Int) =
            if (index in 0 until size()) get(index) else null
    }
}

private data class TestSkillNode(
    val id: String,
    val definitionId: String,
    val root: Boolean,
)
