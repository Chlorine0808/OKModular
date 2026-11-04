package ruiseki.okmodular.selftest.judge;

/** Machine state observed at one tick; recipe is null when idle. */
public final class MachineSample {

    public final int tick;
    public final boolean formed;
    public final String recipe;
    public final float progress;
    public final boolean conditionBlocked;

    public MachineSample(int tick, boolean formed, String recipe, float progress, boolean conditionBlocked) {
        this.tick = tick;
        this.formed = formed;
        this.recipe = recipe;
        this.progress = progress;
        this.conditionBlocked = conditionBlocked;
    }
}
