package ruiseki.okmodular.api.recipe.expression;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.context.IRecipeContext;

/**
 * Expression that evaluates to a world property (e.g., time, moon_phase).
 */
public class WorldPropertyExpression implements IExpression {

    private final String property;

    public WorldPropertyExpression(String property) {
        this.property = property.toLowerCase();
    }

    @Override
    public EvaluationValue evaluate(ConditionContext context) {
        if (context == null || context.getWorld() == null) return EvaluationValue.ZERO;

        String cacheKey = "world_" + property;
        EvaluationValue cached = context.getCachedValue(cacheKey);
        if (cached != null) return cached;

        EvaluationValue result;
        switch (property) {
            case "total_days":
            case "day":
                result = new EvaluationValue(
                    (double) (context.getWorld()
                        .getTotalWorldTime() / 24000L));
                break;
            case "time":
                result = new EvaluationValue(
                    (double) (context.getWorld()
                        .getWorldTime() % 24000L));
                break;
            case "total_time":
                result = new EvaluationValue(
                    (double) context.getWorld()
                        .getTotalWorldTime());
                break;
            case "moon_phase":
            case "moon":
                result = new EvaluationValue(
                    (double) context.getWorld()
                        .getMoonPhase());
                break;
            case "is_day":
                result = new EvaluationValue(
                    context.getWorld()
                        .isDaytime());
                break;
            case "is_night":
                result = new EvaluationValue(
                    !context.getWorld()
                        .isDaytime());
                break;
            case "is_raining":
                result = new EvaluationValue(
                    context.getWorld()
                        .isRaining());
                break;
            case "is_thundering":
                result = new EvaluationValue(
                    context.getWorld()
                        .isThundering());
                break;
            case "temp":
                result = new EvaluationValue(
                    (double) context.getWorld()
                        .getBiomeGenForCoords(context.getX(), context.getZ())
                        .getFloatTemperature(context.getX(), context.getY(), context.getZ()));
                break;
            case "humidity":
                result = new EvaluationValue(
                    (double) context.getWorld()
                        .getBiomeGenForCoords(context.getX(), context.getZ())
                        .getFloatRainfall());
                break;
            case "dimension":
                result = new EvaluationValue((double) context.getWorld().provider.dimensionId);
                break;
            case "light":
                result = new EvaluationValue(
                    (double) context.getWorld()
                        .getBlockLightValue(context.getX(), context.getY(), context.getZ()));
                break;
            case "light_block":
                result = new EvaluationValue(
                    (double) context.getWorld()
                        .getSavedLightValue(
                            net.minecraft.world.EnumSkyBlock.Block,
                            context.getX(),
                            context.getY(),
                            context.getZ()));
                break;
            case "light_sky":
                result = new EvaluationValue(
                    (double) context.getWorld()
                        .getSavedLightValue(
                            net.minecraft.world.EnumSkyBlock.Sky,
                            context.getX(),
                            context.getY(),
                            context.getZ()));
                break;
            // Both delegate to the function form rather than answering for themselves. They
            // used to ask the world directly - canBlockSeeTheSky and getHeightValue - and
            // both of those include the controller's own block. A controller is a solid
            // cube, so canBlockSeeTheSky was false at its own position however open the sky
            // above it was, and getHeightValue is never negative. The variables were
            // constant zeroes, which reads in game exactly like a condition that is not
            // being evaluated at all. See VisionFunctionExpression.SKY.
            case "can_see_sky":
                result = VisionFunctionExpression.SKY.evaluate(context);
                break;
            case "can_see_void":
                result = VisionFunctionExpression.VOID.evaluate(context);
                break;
            case "x":
                result = new EvaluationValue((double) context.getX());
                break;
            case "y":
                result = new EvaluationValue((double) context.getY());
                break;
            case "z":
                result = new EvaluationValue((double) context.getZ());
                break;
            case "seed":
            case "random_seed":
                result = new EvaluationValue((double) context.getEvaluationSeed());
                break;
            case "world_seed":
                result = new EvaluationValue(
                    (double) context.getWorld()
                        .getSeed());
                break;
            case "tick":
                result = new EvaluationValue(
                    (double) context.getWorld()
                        .getTotalWorldTime());
                break;
            case "recipe_tick": {
                IRecipeContext rc = context.getRecipeContext();
                result = new EvaluationValue(
                    (double) (rc != null ? context.getWorld()
                        .getTotalWorldTime() - rc.getRecipeStartTick() : 0));
                break;
            }
            case "progress_tick": {
                ruiseki.okmodular.api.recipe.core.IMachineState state = context.getRecipeContext() != null
                    ? context.getRecipeContext()
                        .getMachineState()
                    : null;
                result = new EvaluationValue((double) (state != null ? state.getProgress() : 0));
                break;
            }
            case "redstone": {
                IRecipeContext rc = context.getRecipeContext();
                result = new EvaluationValue((double) (rc != null ? rc.getRedstoneLevel() : 0));
                break;
            }
            case "facing": {
                IRecipeContext rc = context.getRecipeContext();
                result = new EvaluationValue(
                    (double) (rc != null && rc.getFacing() != null ? rc.getFacing()
                        .ordinal() : 0));
                break;
            }
            default:
                result = EvaluationValue.ZERO;
        }

        context.setCachedValue(cacheKey, result);
        return result;
    }

    @Override
    public String toString() {
        return property;
    }

    public static WorldPropertyExpression fromJson(JsonObject json) {
        return new WorldPropertyExpression(
            json.get("property")
                .getAsString());
    }
}
