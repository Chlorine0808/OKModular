package ruiseki.okmodular.structure.pattern;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.expression.ConstantExpression;
import ruiseki.okmodular.api.recipe.expression.IExpression;
import ruiseki.okmodular.api.recipe.expression.SeedMixer;

/**
 * What one character of a pattern puts in a cell.
 *
 * <h2>Two questions, asked at two different times</h2>
 *
 * <b>How much</b> - the chance and the weights - is written as expressions, so it depends on
 * the machine and is answered once when the pattern is placed. <b>Which cell gets what</b> is
 * answered per cell, from the machine's evaluation seed and the cell's own coordinates.
 * <p>
 * {@link #resolve} is the line between them. It takes the machine's context and hands back a
 * {@link Resolved} holding plain numbers, which is then asked about every cell. Splitting it
 * that way is not tidiness: a sphere of radius eight is 2145 cells, and evaluating
 * {@code "tier * 0.1"} once per cell would be 2145 evaluations of a value that cannot differ
 * between them. Scattering is the coordinates' job, not the expression's.
 * <p>
 * The day a weight is allowed to read the cell it sits in - "denser toward the middle" - the
 * evaluation moves from {@link #resolve} down into {@link Resolved}, and nothing outside this
 * file has to know.
 *
 * <h2>Why this is not a block ID</h2>
 *
 * It was one, and a {@link StructurePattern.Cell} held it directly, which works only while
 * every cell is settled the moment the file is read. The geometry still is, and is still
 * cached per facing; only the contents moved.
 *
 * <h2>Why the coordinates have to be mixed in</h2>
 *
 * The evaluation seed is fixed for a whole run, which is what makes a run reproducible - and
 * it means a draw taken from the seed alone answers the same at every cell. A pattern of fifty
 * cells would place all fifty or none of them, which is not a probability per cell, it is one
 * coin flip for the whole pattern. {@link SeedMixer#forPosition} makes neighbours disagree
 * while the run stays reproducible.
 * <p>
 * The coordinates are the cell's <b>world</b> position, not its place in the pattern, so a
 * recipe writing the same pattern at four anchors gets four different arrangements rather than
 * four copies of one. It also matches what {@code per_position_probability} and
 * {@code random_block_output} already do.
 */
public final class CellMapping {

    /** A chance at or above this always places, and skips the draw entirely. */
    private static final double ALWAYS = 1.0;

    /** What may go here. A null entry means "leave the cell alone". */
    private final String[] choices;
    private final IExpression[] weights;
    private final IExpression chance;

    CellMapping(String blockId) {
        this(blockId, new ConstantExpression(ALWAYS));
    }

    CellMapping(String blockId, IExpression chance) {
        this(new String[] { blockId }, new IExpression[] { new ConstantExpression(1.0) }, chance);
    }

    CellMapping(String[] choices, IExpression[] weights, IExpression chance) {
        this.choices = choices;
        this.weights = weights;
        this.chance = chance;
    }

    /**
     * Whether this mapping answers the same at every cell of every placement.
     * <p>
     * A structure <i>input</i> may only use patterns that do: matching against an arrangement
     * that is drawn fresh each time has nothing to compare to. The refusal happens when the
     * recipe loads - see {@code StructureInput}.
     * <p>
     * An expression-valued chance counts as undecided even if it would evaluate to one. What
     * it evaluates to is not knowable here, and guessing "probably always" would send the
     * question to run time, where the answer is a recipe that silently fails to match.
     */
    public boolean isDeterministic() {
        return choices.length == 1 && chance instanceof ConstantExpression && chance.evaluateDouble(null) >= ALWAYS;
    }

    /**
     * Settle the expressions against the machine, for one placement of the pattern.
     * <p>
     * Call once and ask the result about every cell. Calling it per cell is not wrong, only
     * wasteful - see the note at the top of this class.
     */
    public Resolved resolve(ConditionContext context) {
        double[] settled = new double[weights.length];
        double total = 0;
        for (int i = 0; i < weights.length; i++) {
            settled[i] = weights[i].evaluateDouble(context);
            total += settled[i];
        }
        return new Resolved(choices, settled, total, chance.evaluateDouble(context));
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < choices.length; i++) {
            if (i > 0) text.append('/');
            text.append(choices[i] == null ? " " : choices[i]);
        }
        return text.toString();
    }

    /** One mapping with its numbers settled, ready to be asked about individual cells. */
    public static final class Resolved {

        private final String[] choices;
        private final double[] weights;
        private final double totalWeight;
        private final double chance;

        Resolved(String[] choices, double[] weights, double totalWeight, double chance) {
            this.choices = choices;
            this.weights = weights;
            this.totalWeight = totalWeight;
            this.chance = chance;
        }

        /**
         * The block to place at one cell of this placement.
         *
         * @param seed the machine's evaluation seed, fixed for the run
         * @param x    where the cell landed in the world
         * @return {@code modid:name}, {@code modid:name:meta}, or null when this draw leaves
         *         the cell alone. <b>Alone, not empty</b> - a cell that loses its chance is as
         *         if it had been drawn as a space, so whatever stood there stays. To clear it
         *         instead, give it {@code _}.
         */
        public String blockFor(long seed, int x, int y, int z) {
            if (chance < ALWAYS && !isWritten(seed, x, y, z)) return null;
            if (choices.length == 1) return choices[0];
            return pick(seed, x, y, z);
        }

        private boolean isWritten(long seed, int x, int y, int z) {
            long cell = SeedMixer.forPosition(seed, x, y, z);
            return SeedMixer.toUnitInterval(cell, SeedMixer.PATTERN_CELL_CHANCE) < chance;
        }

        /**
         * Which entry of the table this cell draws.
         * <p>
         * A different stream from {@link #isWritten}: sharing one would make the cells that
         * won their chance be exactly the cells that drew the first entry, so a table behind a
         * chance would only ever place one of its blocks.
         * <p>
         * A total weight of zero is refused when the pattern is read, so reaching it here
         * means an expression brought it to zero. Nothing is placed rather than the first
         * entry being handed out for free.
         */
        private String pick(long seed, int x, int y, int z) {
            if (totalWeight <= 0) return null;

            long cell = SeedMixer.forPosition(seed, x, y, z);
            double roll = SeedMixer.toUnitInterval(cell, SeedMixer.PATTERN_CELL_WEIGHT) * totalWeight;

            double passed = 0;
            for (int i = 0; i < choices.length; i++) {
                passed += weights[i];
                if (roll < passed) return choices[i];
            }
            // The roll is below the total by construction; this only catches floating-point drift.
            return choices[choices.length - 1];
        }
    }
}
