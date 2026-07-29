package ruiseki.okmodular.api.modular;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Divides a machine's ports into colour groups, in the order a machine should try
 * them when looking for a recipe.
 *
 * <h2>The rules</h2>
 *
 * <ol>
 * <li>A painted port belongs only to the group of its own colour.</li>
 * <li><strong>An unpainted port belongs to every group.</strong> Without this,
 * using colours at all would mean one energy hatch per colour.</li>
 * <li>Groups come in {@link PortColor} declaration order, white through
 * black.</li>
 * <li><strong>The group of unpainted ports comes last.</strong></li>
 * <li>A {@code Type.BLOCK} port - the controller itself - belongs to every group
 * whatever its colour.</li>
 * </ol>
 *
 * Rule 4 follows from rule 2: with sharing, the unpainted group holds every
 * unpainted port and so matches almost anything. Evaluated first it would make
 * painting pointless.
 *
 * <p>
 * Rule 5 is because the port lists a controller hands out include the controller
 * itself, whose port type is {@code BLOCK}. Dropping it would stop every recipe
 * using {@code BlockInput} or {@code BlockOutput} from running in any group.
 *
 * <p>
 * What rule 2 costs: material put in an unpainted input port is visible to every
 * group, so colours do not isolate completely. That is the intended behaviour - a
 * player wanting full isolation paints every port.
 *
 * <h2>A machine with no paint on it</h2>
 *
 * Answers exactly one group holding every port, which is what the machine did
 * before colours existed. That case also skips the copying entirely, since it is
 * the one that runs every tick on every machine in the world.
 */
public final class PortColorGrouping {

    /** One colour's worth of ports, ready to hand to recipe matching. */
    public static final class Group {

        private final PortColor color;
        private final List<IModularPort> inputs;
        private final List<IModularPort> outputs;

        Group(PortColor color, List<IModularPort> inputs, List<IModularPort> outputs) {
            this.color = color;
            this.inputs = inputs;
            this.outputs = outputs;
        }

        public PortColor getColor() {
            return color;
        }

        public List<IModularPort> getInputs() {
            return inputs;
        }

        public List<IModularPort> getOutputs() {
            return outputs;
        }

        @Override
        public String toString() {
            return color + "(" + inputs.size() + " in, " + outputs.size() + " out)";
        }
    }

    private PortColorGrouping() {}

    /**
     * The groups to try, most preferred first.
     *
     * Never empty: a machine with nothing painted answers a single group holding
     * every port, so callers do not need a special case.
     */
    public static List<PortColorGrouping.Group> group(List<IModularPort> inputs, List<IModularPort> outputs) {
        Set<PortColor> painted = paintedColors(inputs, outputs);

        if (painted.isEmpty()) {
            // Nothing is painted, which is every machine that has not opted in. Hand
            // back the lists as they came rather than copying them: this runs each
            // tick a machine looks for a recipe.
            return Collections.singletonList(new Group(PortColor.NONE, inputs, outputs));
        }

        List<Group> groups = new ArrayList<>(painted.size() + 1);
        for (PortColor color : PortColor.values()) {
            if (painted.contains(color)) {
                groups.add(new Group(color, select(inputs, color), select(outputs, color)));
            }
        }

        // The unpainted group only earns its place if some port is actually
        // unpainted. When every port carries a colour it would just repeat whichever
        // group ran last, one findMatch per tick for nothing. BLOCK ports do not
        // count - they are in every group already.
        if (hasUnpainted(inputs) || hasUnpainted(outputs)) {
            groups.add(new Group(PortColor.NONE, select(inputs, PortColor.NONE), select(outputs, PortColor.NONE)));
        }
        return groups;
    }

    /** The colours actually in use, so unused ones cost nothing. */
    private static Set<PortColor> paintedColors(List<IModularPort> inputs, List<IModularPort> outputs) {
        Set<PortColor> colors = EnumSet.noneOf(PortColor.class);
        collectPaintedColors(inputs, colors);
        collectPaintedColors(outputs, colors);
        return colors;
    }

    private static void collectPaintedColors(List<IModularPort> ports, Set<PortColor> into) {
        for (IModularPort port : ports) {
            if (isEverywhere(port)) continue;
            PortColor color = port.getPortColor();
            if (color != null && color.isColored()) into.add(color);
        }
    }

    private static boolean hasUnpainted(List<IModularPort> ports) {
        for (IModularPort port : ports) {
            if (isEverywhere(port)) continue;
            PortColor color = port.getPortColor();
            if (color == null || !color.isColored()) return true;
        }
        return false;
    }

    /**
     * The ports one group sees: its own colour first, then everything unpainted.
     *
     * Ordering inside a group is stable - painted before unpainted, original order
     * within each - because recipe matching walks the list and stops at the first
     * port that fits. An unstable order would make which port gets used vary.
     */
    private static List<IModularPort> select(List<IModularPort> ports, PortColor color) {
        List<IModularPort> selected = new ArrayList<>(ports.size());
        if (color.isColored()) {
            for (IModularPort port : ports) {
                if (!isEverywhere(port) && port.getPortColor() == color) selected.add(port);
            }
        }
        for (IModularPort port : ports) {
            if (isEverywhere(port)) continue;
            PortColor portColor = port.getPortColor();
            if (portColor == null || !portColor.isColored()) selected.add(port);
        }
        for (IModularPort port : ports) {
            if (isEverywhere(port)) selected.add(port);
        }
        return selected;
    }

    /**
     * Whether this port belongs to every group regardless of colour.
     *
     * The controller reports itself as a {@code BLOCK} port so that recipes reading
     * and writing world blocks can find it. It is not a resource port and painting
     * it means nothing, so it is never what divides the groups.
     */
    private static boolean isEverywhere(IModularPort port) {
        return port.getPortType() == IPortType.Type.BLOCK;
    }
}
