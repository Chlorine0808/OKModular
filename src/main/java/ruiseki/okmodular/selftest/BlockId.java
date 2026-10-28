package ruiseki.okmodular.selftest;

/** A block or item named "modid:name" or "modid:name:meta"; the spelling scenarios and BlockGrid share. */
final class BlockId {

    final String name;
    final int meta;

    private BlockId(String name, int meta) {
        this.name = name;
        this.meta = meta;
    }

    static BlockId parse(String id) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("empty block or item id");
        String[] parts = id.split(":");
        if (parts.length <= 2) return new BlockId(id, 0);
        if (parts.length == 3) {
            try {
                return new BlockId(parts[0] + ":" + parts[1], Integer.parseInt(parts[2]));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad meta in \"" + id + "\"");
            }
        }
        throw new IllegalArgumentException("bad block or item id \"" + id + "\"");
    }

    /** Meta 0 is left out, as BlockGrid records it. */
    static String format(String name, int meta) {
        return meta == 0 ? name : name + ":" + meta;
    }
}
