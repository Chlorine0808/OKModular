package ruiseki.okmodular.common.recipe;

import java.util.List;

import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.modular.PortColor;
import ruiseki.okmodular.api.modular.PortColorGrouping;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;

/**
 * Picks which colour group a machine should run next.
 *
 * <h2>The rules</h2>
 *
 * <ol>
 * <li>Groups are tried in the order given. The first one that both matches a
 * recipe and has room for its output wins.</li>
 * <li>A group whose recipe cannot fit its output is <strong>skipped</strong>, not
 * treated as a stop.</li>
 * <li>When no group can run, the failure reported is the one from the
 * <strong>first</strong> group that had a recipe it could not run.</li>
 * <li>When nothing matched anywhere, the answer carries no recipe at all - which
 * is a different thing for the caller to report than a blocked one.</li>
 * </ol>
 *
 * Rule 2 is the point of colour groups: they are meant to behave like independent
 * little machines, so a full red output tank must not stop blue from running.
 * Before colours there was only one group, so "match, then check output, then give
 * up" was the whole story.
 *
 * <p>
 * Rule 3 exists because something has to go on the GUI when nothing runs.
 * Reporting the highest-priority colour's problem is closest to what a player was
 * trying to do.
 *
 * <h2>Why this is not on the controller</h2>
 *
 * A controller cannot be built in a unit test. This rule depends on neither the
 * world nor what is inside a recipe, so taking the recipe lookup as an argument
 * puts all of it within reach of one.
 */
public final class ColoredRecipeSearch {

    /** How the caller finds a recipe for one group's input ports. */
    public interface RecipeLookup {

        /**
         * @return a recipe those inputs satisfy, or null if none does
         */
        IModularRecipe find(List<IModularPort> inputs);
    }

    /**
     * Which group to run, or why none can.
     *
     * Three shapes: runnable ({@link #isRunnable()}), blocked (a recipe is present
     * with a reason it cannot run), and empty (no recipe at all).
     */
    public static final class Selection {

        private static final Selection NOTHING = new Selection(PortColor.NONE, null, null, null, null, false);

        private final PortColor color;
        private final IModularRecipe recipe;
        private final List<IModularPort> inputs;
        private final List<IModularPort> outputs;
        private final IPortType.Type insufficientType;
        private final boolean outputFull;

        private Selection(PortColor color, IModularRecipe recipe, List<IModularPort> inputs, List<IModularPort> outputs,
            IPortType.Type insufficientType, boolean outputFull) {
            this.color = color;
            this.recipe = recipe;
            this.inputs = inputs;
            this.outputs = outputs;
            this.insufficientType = insufficientType;
            this.outputFull = outputFull;
        }

        /** Whether this recipe can be started against these ports right now. */
        public boolean isRunnable() {
            return recipe != null && insufficientType == null && !outputFull;
        }

        /** The group's colour, or {@link PortColor#NONE} when nothing was found. */
        public PortColor getColor() {
            return color;
        }

        /** Null when no group matched anything. */
        public IModularRecipe getRecipe() {
            return recipe;
        }

        /** The winning group's input ports, to start the recipe against. */
        public List<IModularPort> getInputs() {
            return inputs;
        }

        /** The winning group's output ports, so results land on the same colour. */
        public List<IModularPort> getOutputs() {
            return outputs;
        }

        /** The kind whose output capacity was too small, or null. */
        public IPortType.Type getInsufficientType() {
            return insufficientType;
        }

        /** Whether the recipe was blocked by full output rather than by capacity. */
        public boolean isOutputFull() {
            return outputFull;
        }

        @Override
        public String toString() {
            if (recipe == null) return "no recipe";
            if (isRunnable()) return color + " -> " + recipe.getName();
            return color + " -> "
                + recipe.getName()
                + (insufficientType != null ? " (capacity: " + insufficientType + ")" : " (output full)");
        }
    }

    private ColoredRecipeSearch() {}

    public static Selection search(List<PortColorGrouping.Group> groups, RecipeLookup lookup) {
        Selection firstBlocked = null;

        for (PortColorGrouping.Group group : groups) {
            IModularRecipe recipe = lookup.find(group.getInputs());
            if (recipe == null) continue;

            IPortType.Type insufficient = recipe.checkOutputCapacity(group.getOutputs());
            if (insufficient != null) {
                if (firstBlocked == null) {
                    firstBlocked = new Selection(
                        group.getColor(),
                        recipe,
                        group.getInputs(),
                        group.getOutputs(),
                        insufficient,
                        false);
                }
                continue;
            }

            if (!recipe.canOutput(group.getOutputs())) {
                if (firstBlocked == null) {
                    firstBlocked = new Selection(
                        group.getColor(),
                        recipe,
                        group.getInputs(),
                        group.getOutputs(),
                        null,
                        true);
                }
                continue;
            }

            return new Selection(group.getColor(), recipe, group.getInputs(), group.getOutputs(), null, false);
        }

        return firstBlocked != null ? firstBlocked : Selection.NOTHING;
    }
}
