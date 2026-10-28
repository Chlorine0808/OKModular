package ruiseki.okmodular.api.recipe.decorator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * Forge fills the vanilla blocks' harvest tools (pickaxe 3 for obsidian, 1 for iron ore) in
 * ForgeHooks' static initialiser. On a fresh dedicated server nothing may have loaded ForgeHooks
 * yet - no player has mined - so getHarvestTool returned null for every block and the
 * harvestLevel gate let obsidian and iron ore through (in-game selftest s3.6-too-hard and
 * s3.6-iron-no-level: tool=null, lvl=-1 for stone and obsidian). The gate has to make Forge fill
 * the table before it reads it. Tests cannot bootstrap Forge's blocks, so this pins the source.
 */
class HarvestToolTableTest {

    @Test
    @DisplayName("硬さの判定は Forge の道具表を初期化してから読む")
    void levelGateInitialisesForgesToolTable() {
        String body = JavaSource.methodBody(
            JavaSource.stripComments(
                JavaSource.read(JavaSource.MAIN.resolve("api/recipe/decorator/HarvestBlockDecorator.java"))),
            "private boolean levelAllows(");
        assertTrue(body.contains("ForgeHooks"), "levelAllows must touch ForgeHooks before getHarvestTool");
    }
}
