package ruiseki.okmodular.common.recipe.capture;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ruiseki.okmodular.api.recipe.io.EnergyInput;

/**
 * A recipe being edited before it is saved. Entries are plain JSON written by the IO classes.
 * Numbers are 1-based: inputs first, then outputs. Energy is not numbered.
 */
public final class RecipeDraft {

    private final String group;
    private int duration;
    private final List<JsonObject> inputs = new ArrayList<>();
    private final List<JsonObject> outputs = new ArrayList<>();
    private int energy;
    private boolean energyPerTick;

    public RecipeDraft(String group, int duration) {
        this.group = group;
        setDuration(duration);
    }

    public String getGroup() {
        return group;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int ticks) {
        if (ticks <= 0) throw new IllegalArgumentException("duration must be positive: " + ticks);
        this.duration = ticks;
    }

    public void addInput(JsonObject entry) {
        merge(inputs, entry);
    }

    public void addOutput(JsonObject entry) {
        merge(outputs, entry);
    }

    public int size() {
        return inputs.size() + outputs.size();
    }

    public boolean isOutput(int number) {
        check(number);
        return number > inputs.size();
    }

    public JsonObject entry(int number) {
        check(number);
        return copy(at(number));
    }

    public void setAmount(int number, int amount) {
        check(number);
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive: " + amount);
        at(number).addProperty("amount", amount);
    }

    public void remove(int number) {
        check(number);
        if (number <= inputs.size()) inputs.remove(number - 1);
        else outputs.remove(number - inputs.size() - 1);
    }

    public void setEnergy(int amount, boolean perTick) {
        if (amount < 0) throw new IllegalArgumentException("energy must not be negative: " + amount);
        this.energy = amount;
        this.energyPerTick = amount > 0 && perTick;
    }

    public int getEnergy() {
        return energy;
    }

    public boolean isEnergyPerTick() {
        return energyPerTick;
    }

    public boolean isEmpty() {
        return inputs.isEmpty() && outputs.isEmpty() && energy == 0;
    }

    public JsonObject toRecipeJson() {
        if (isEmpty()) throw new IllegalStateException("the draft has nothing to save");
        JsonObject json = new JsonObject();
        json.addProperty("duration", duration);
        JsonArray in = new JsonArray();
        for (JsonObject e : inputs) in.add(copy(e));
        if (energy > 0) {
            JsonObject e = new JsonObject();
            new EnergyInput(energy, energyPerTick).write(e);
            in.add(e);
        }
        json.add("inputs", in);
        JsonArray out = new JsonArray();
        for (JsonObject e : outputs) out.add(copy(e));
        json.add("outputs", out);
        return json;
    }

    private void check(int number) {
        if (number < 1 || number > size()) {
            throw new IllegalArgumentException("no entry number " + number + " (1.." + size() + ")");
        }
    }

    private JsonObject at(int number) {
        return number <= inputs.size() ? inputs.get(number - 1) : outputs.get(number - inputs.size() - 1);
    }

    /** Adds the entry, or adds its amount to an entry that differs only in amount. Throws IAE on int overflow. */
    private static void merge(List<JsonObject> list, JsonObject entry) {
        JsonObject added = copy(entry);
        int amount = amountOf(added);
        added.remove("amount");
        for (JsonObject existing : list) {
            int existingAmount = amountOf(existing);
            JsonObject key = copy(existing);
            key.remove("amount");
            if (key.equals(added)) {
                long sum = (long) existingAmount + amount;
                if (sum > Integer.MAX_VALUE) throw new IllegalArgumentException("amount overflows int: " + sum);
                existing.addProperty("amount", (int) sum);
                return;
            }
        }
        added.addProperty("amount", amount);
        list.add(added);
    }

    private static int amountOf(JsonObject entry) {
        return entry.has("amount") ? entry.get("amount")
            .getAsInt() : 1;
    }

    private static JsonObject copy(JsonObject source) {
        return new JsonParser().parse(source.toString())
            .getAsJsonObject();
    }
}
