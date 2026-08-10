package ruiseki.okmodular.structure.pattern;

import ruiseki.okmodular.api.recipe.expression.SeedMixer;

/**
 * What one character of a pattern puts in a cell.
 *
 * <h2>Why this is not a block ID</h2>
 *
 * It was one, and a {@link StructurePattern.Cell} held it directly. That works only while
 * every cell of a pattern is decided the moment the file is read - and a mapping with a
 * {@code chance} decides <b>when the pattern is placed</b>, from the machine's evaluation seed
 * and the cell's own coordinates.
 * <p>
 * The geometry is still settled at read time and still cached per facing; only the answer to
 * "and what goes here" moved. Keeping the two apart is what lets the cache stay: where the
 * cells are does not depend on the draw.
 *
 * <h2>Why the coordinates have to be mixed in</h2>
 *
 * The evaluation seed is fixed for a whole run, which is what makes a run reproducible - and
 * it means a draw taken from the seed alone answers the same at every cell. A pattern of fifty
 * cells would place all fifty or none of them, which is not a probability, it is a coin flip
 * for the whole pattern. {@link SeedMixer#forPosition} makes neighbours disagree while the run
 * stays reproducible.
 * <p>
 * The coordinates are the cell's <b>world</b> position, not its place in the pattern, so a
 * recipe writing the same pattern at four anchors gets four different arrangements rather than
 * four copies of one. It also matches what {@code per_position_probability} and
 * {@code random_block_output} already do.
 */
public final class CellMapping {

    /** A chance at or above this always places, and skips the draw entirely. */
    private static final double ALWAYS = 1.0;

    /** What may go here. One entry for a plain block; null entries mean "leave it alone". */
    private final String[] choices;
    private final double[] weights;
    private final double totalWeight;
    private final double chance;

    CellMapping(String blockId) {
        this(blockId, ALWAYS);
    }

    CellMapping(String blockId, double chance) {
        this(new String[] { blockId }, new double[] { 1.0 }, chance);
    }

    CellMapping(String[] choices, double[] weights, double chance) {
        this.choices = choices;
        this.weights = weights;
        this.chance = chance;

        double total = 0;
        for (double weight : weights) total += weight;
        this.totalWeight = total;
    }

    /**
     * Whether this mapping answers the same at every cell of every placement.
     * <p>
     * A structure <i>input</i> may only use patterns that do: matching against an arrangement
     * that is drawn fresh each time has nothing to compare to. The refusal happens when the
     * recipe loads - see {@code StructureInput}.
     */
    public boolean isDeterministic() {
        return chance >= ALWAYS && choices.length == 1;
    }

    /**
     * The block to place at one cell of one placement.
     *
     * @param seed the machine's evaluation seed, fixed for the run
     * @param x    where the cell landed in the world
     * @return {@code modid:name}, {@code modid:name:meta}, {@code modid:name:*}, or null when
     *         this draw leaves the cell alone. <b>Alone, not empty</b> - a cell that loses its
     *         chance is as if it had been drawn as a space, so whatever stood there stays. To
     *         clear it instead, map it to {@code _}.
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
     * A different stream from {@link #isWritten}: sharing one would make the cells that won
     * their chance be exactly the cells that drew the first entry, so a table behind a chance
     * would only ever place one of its blocks.
     * <p>
     * A total weight of zero is refused when the pattern is read, so reaching it here means
     * the weights came from somewhere that can move. Nothing is placed rather than the first
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

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < choices.length; i++) {
            if (i > 0) text.append('/');
            text.append(choices[i] == null ? " " : choices[i]);
            if (choices.length > 1) text.append('=')
                .append(weights[i]);
        }
        return chance >= ALWAYS ? text.toString() : text + "@" + chance;
    }
}
