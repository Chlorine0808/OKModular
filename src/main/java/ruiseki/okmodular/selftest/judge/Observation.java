package ruiseki.okmodular.selftest.judge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ruiseki.okmodular.selftest.grid.BlockGrid;

/** Mutable bag of everything observed while a scenario ran; the Minecraft glue fills it, the judge reads it. */
public final class Observation {

    public List<MachineSample> samples = new ArrayList<>();
    public Map<String, BlockGrid> snapshots = new LinkedHashMap<>();
    public Map<String, Integer> outputItems = new HashMap<>();
    public Map<String, Integer> inputItems = new HashMap<>();
    public Map<String, Integer> droppedItems = new HashMap<>();
    /** Label to key to value. */
    public Map<String, Map<String, String>> nbt = new HashMap<>();
    public boolean chunkReloaded;
    public String validationError;
    public Map<String, Integer> componentTiers = new HashMap<>();
    public List<String> logLines = new ArrayList<>();
    /** Relative path to file content. */
    public Map<String, String> files = new HashMap<>();
    public String conditionFailure;
}
