/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.gametest

import com.google.gson.Gson
import com.google.gson.JsonArray
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.features.module.modules.world.ModuleHoleFiller
import net.ccbluex.liquidbounce.utils.block.hole.Hole
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.world.nextLocalEntityId
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.monster.cubemob.Slime
import net.minecraft.world.level.levelgen.structure.BoundingBox

/** Exercises the smart collector with two targets competing for one hotbar budget. */
class HoleFillerBudgetGameTest : FabricClientGameTest {

    private val contextType = ModuleHoleFiller::class.java.declaredClasses.single { it.simpleName == "HoleContext" }
    private val constructor = contextType.declaredConstructors.single().apply { isAccessible = true }
    private val collector = ModuleHoleFiller::class.java.getDeclaredMethod(
        "collectHolesSmart", Double::class.javaPrimitiveType, contextType, Int::class.javaPrimitiveType,
    ).apply { isAccessible = true }

    override fun runTest(context: ClientGameTestContext) {
        context.waitForClient()
        val saved = context.fromClient { ConfigSystem.serializeValueGroup(ModuleHoleFiller) }

        try {
            context.worldBuilder().create().use { world ->
                world.server.runCommand("gamemode survival @a")
                context.waitFor({ client -> client.player?.hasInfiniteMaterials() == false }, 200)
                context.onClient {
                    ModuleHoleFiller.enabled = false
                    ModuleHoleFiller.settings.getValue("Features").deserializeFrom(
                        Gson(), JsonArray().apply { add("Smart") }
                    )
                    val player = checkNotNull(mc.player)
                    val base = player.blockPosition()
                    val targets = listOf(addTarget(base.offset(3, 0, 0)), addTarget(base.offset(-4, 0, 0)))
                    val holes = listOf(
                        Hole.OneByTwo(base.offset(3, -1, 0), Direction.Axis.X),
                        Hole.OneByTwo(base.offset(-4, -1, 0), Direction.Axis.X),
                    )

                    try {
                        val limited = collect(holes, 3)
                        check(limited.size == 2) { "Three items queued ${limited.size} blocks across two targets" }
                        check(holes.any { limited == it.asList().toSet() }) { "A hole was partially budgeted" }
                        check(collect(holes, 4).size == 4) { "A sufficient budget did not cover both holes" }
                        check(collect(holes, 1).isEmpty()) { "An unaffordable hole was queued" }

                        val shared = listOf(Hole.OneByTwo(base.offset(0, -1, 0), Direction.Axis.X))
                        targets.forEach { it.setPos(base.x + 0.5, player.y, base.z + 0.5) }
                        check(collect(shared, 3) == shared.single().asList().toSet()) {
                            "Two targets charged or queued the same hole twice"
                        }

                        player.abilities.instabuild = true
                        check(collect(holes, 1).isEmpty()) { "Targets outside the hole area should not queue blocks" }
                        targets[0].setPos(base.x + 3.5, player.y, base.z + 0.5)
                        targets[1].setPos(base.x - 3.5, player.y, base.z + 0.5)
                        check(collect(holes, 1).size == 4) { "Creative mode was limited by its item count" }
                    } finally {
                        player.abilities.instabuild = false
                        val level = checkNotNull(mc.level)
                        targets.forEach { level.removeEntity(it.id, Entity.RemovalReason.DISCARDED) }
                    }
                }
            }
        } finally {
            context.onClient {
                ModuleHoleFiller.enabled = false
                ConfigSystem.deserializeValueGroup(ModuleHoleFiller, saved)
            }
        }
    }

    private fun addTarget(pos: BlockPos): Slime {
        val level = checkNotNull(mc.level)
        return Slime(EntityTypes.SLIME, level).apply {
            id = level.nextLocalEntityId()
            setPos(pos.x + 0.5, checkNotNull(mc.player).y, pos.z + 0.5)
            level.addEntity(this)
        }
    }

    private fun collect(holes: List<Hole>, budget: Int): Set<BlockPos> {
        val blocks = linkedSetOf<BlockPos>()
        val holeContext = constructor.newInstance(holes, false, BoundingBox(BlockPos.ZERO), blocks)
        collector.invoke(ModuleHoleFiller, 100.0, holeContext, budget)
        return blocks
    }

}
