package ruiseki.okmodular.client.nei;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * A recipe's conditions must show in NEI.
 *
 * NEI is where a player reads what a recipe asks for before building the machine. Leaving the
 * conditions out makes a recipe look runnable anywhere. Drawing needs a font renderer, so this
 * pins that the handler reads them.
 */
class NeiConditionDisplayTest {

    @Test
    void theRecipeHandlerReadsTheConditions() {
        String handler = JavaSource
            .stripComments(JavaSource.read(JavaSource.MAIN.resolve("client/nei/ModularRecipeNEIHandler.java")));
        assertTrue(handler.contains(".getConditions()"), "ModularRecipeNEIHandler never reads a recipe's conditions");
        assertTrue(handler.contains(".getDescription()"), "the conditions are read but not described");
    }
}
