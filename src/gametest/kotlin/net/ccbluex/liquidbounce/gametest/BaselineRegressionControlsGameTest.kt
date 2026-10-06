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

import net.ccbluex.liquidbounce.utils.client.logger
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

/** Fork-only negative controls: each regression must fail for its intended reason. */
class BaselineRegressionControlsGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        expectFailure(context, AutoRodRequirementsGameTest(), "health below MinHealth: expected false, got true")
        expectFailure(context, PortalPositionGameTest(), null)
        expectFailure(context, HoleFillerBudgetGameTest(), "Three items queued 4 blocks across two targets")
        expectFailure(context, TeleportDisconnectGameTest(), "A pending teleport survived disconnect")
        logger.info("NEGATIVE CONTROLS: confirmed all four regressions on unmodified production sources")
    }

    private fun expectFailure(context: ClientGameTestContext, test: FabricClientGameTest, message: String?) {
        val failure = runCatching { test.runTest(context) }.exceptionOrNull()
        checkNotNull(failure) { "${test.javaClass.simpleName} unexpectedly passed without its fix" }
        val causes = generateSequence(failure) { it.cause }.toList()
        check(causes.any { if (message == null) it is NullPointerException else it.message == message }) {
            "${test.javaClass.simpleName} failed for an unexpected reason: $failure"
        }
        logger.info("NEGATIVE CONTROL CONFIRMED: ${test.javaClass.simpleName}", failure)
    }

}
