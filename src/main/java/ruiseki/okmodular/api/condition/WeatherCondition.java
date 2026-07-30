package ruiseki.okmodular.api.condition;

import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.google.gson.JsonObject;

/**
 * Condition that checks the current weather.
 */
public class WeatherCondition implements ICondition {

    public enum Weather {
        CLEAR,
        RAIN,
        THUNDER
    }

    private final Weather weather;

    public WeatherCondition(Weather weather) {
        this.weather = weather;
    }

    @Override
    public boolean isMet(ConditionContext context) {
        World world = context.getWorld();
        if (world == null) return false;

        switch (weather) {
            case THUNDER:
                return world.isThundering();
            case RAIN:
                return world.isRaining();
            case CLEAR:
                return !world.isRaining() && !world.isThundering();
            default:
                return false;
        }
    }

    @Override
    public String getDescription() {
        // Upper case, matching the enum constant and the lang entries. It used to lower
        // case the name, so it asked for a key nobody had written and the GUI showed
        // "okmodular.condition.weather.rain" verbatim. The rest of this mod keys enums by
        // their constant name too - gui.port_type.ITEM, gui.port_color.RED.
        return StatCollector.translateToLocal("okmodular.condition.weather." + weather.name());
    }

    @Override
    public void write(JsonObject json) {
        json.addProperty("type", "weather");
        json.addProperty(
            "weather",
            weather.name()
                .toLowerCase());
    }

    public static ICondition fromJson(JsonObject json) {
        String w = json.get("weather")
            .getAsString();
        return new WeatherCondition(Weather.valueOf(w.toUpperCase()));
    }
}
