package ruiseki.okmodular.structure.pattern;

/**
 * What one character of a pattern puts in a cell.
 *
 * <h2>Why this is not a block ID</h2>
 *
 * It was one, and a {@link StructurePattern.Cell} held it directly. That works only while
 * every cell of a pattern is decided the moment the file is read - and the whole point of the
 * weight tables this makes room for is that a cell's content is decided <b>when the pattern is
 * placed</b>, from the machine's evaluation seed and the cell's own coordinates.
 * <p>
 * The geometry is still settled at read time and still cached per facing; only the answer to
 * "and what goes here" moved. Keeping the two apart is what lets the cache stay: where the
 * cells are does not depend on the draw.
 */
public final class CellMapping {

    private final String blockId;

    CellMapping(String blockId) {
        this.blockId = blockId;
    }

    /**
     * The block to place at one cell of one placement.
     *
     * @param seed the machine's evaluation seed, fixed for the run
     * @param x    where the cell landed in the world; a mapping that varies per cell mixes
     *             these in so that neighbours differ while the run stays reproducible
     * @return {@code modid:name}, {@code modid:name:meta} or {@code modid:name:*}
     */
    public String blockFor(long seed, int x, int y, int z) {
        return blockId;
    }

    @Override
    public String toString() {
        return blockId;
    }
}
