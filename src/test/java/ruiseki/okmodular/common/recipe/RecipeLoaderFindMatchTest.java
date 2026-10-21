package ruiseki.okmodular.common.recipe;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.condition.ICondition;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.core.ModularRecipe;

/**
 * The machine searched for the first recipe whose inputs were there and stopped, without
 * asking whether that recipe could start. A recipe with a failing condition in front of a
 * plain one with the same inputs then blocked it for good: found every tick, refused every
 * tick. Confirmed in game on the Metal Former's anvil plate recipe at tier 1.
 */
class RecipeLoaderFindMatchTest {

    private static final String GROUP = "find_match_test";
    private static final List<IModularPort> NO_PORTS = Collections.emptyList();

    private static final ICondition NEVER = new ICondition() {

        @Override
        public boolean isMet(ConditionContext context) {
            return false;
        }

        @Override
        public String getDescription() {
            return "never";
        }

        @Override
        public void write(JsonObject json) {}
    };

    private static ModularRecipe recipe(String name, int priority, ICondition condition) {
        ModularRecipe.Builder builder = ModularRecipe.builder()
            .registryName(name)
            .recipeGroup(GROUP)
            .name(name)
            .duration(100)
            .priority(priority);
        if (condition != null) builder.addCondition(condition);
        return builder.build();
    }

    @AfterEach
    void tearDown() {
        RecipeLoader.getInstance()
            .clearGroup(GROUP);
    }

    @Test
    void aRecipeThatRefusesToStartLetsTheNextOneRun() {
        IModularRecipe refusing = recipe("refusing", 1, NEVER);
        IModularRecipe plain = recipe("plain", 0, null);
        RecipeLoader loader = RecipeLoader.getInstance();
        loader.addRecipe(GROUP, refusing);
        loader.addRecipe(GROUP, plain);
        ConditionContext context = new ConditionContext(null, 0, 0, 0);

        IModularRecipe found = loader.findMatch(new String[] { GROUP }, NO_PORTS, r -> r.canStart(context));

        assertSame(plain, found);
    }

    /** With nothing startable, the refusing recipe still comes back so the machine can say why. */
    @Test
    void withNothingStartableTheFirstInputMatchIsReturned() {
        IModularRecipe refusing = recipe("refusing", 0, NEVER);
        RecipeLoader loader = RecipeLoader.getInstance();
        loader.addRecipe(GROUP, refusing);
        ConditionContext context = new ConditionContext(null, 0, 0, 0);

        IModularRecipe found = loader.findMatch(new String[] { GROUP }, NO_PORTS, r -> r.canStart(context));

        assertSame(refusing, found);
    }
}
