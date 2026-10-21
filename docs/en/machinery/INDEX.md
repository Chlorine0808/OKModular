# Modular Machinery Documentation

Technical documentation for the Modular Machinery module.
It is split into structures, recipes and machines, with what each document covers and who it is for.

---

## 1. Structures

#### [JSON Format](../structures/JSON_FORMAT.md)
The JSON format for defining multiblock structures.

#### [Structure IO](../structures/STRUCTURE_IO.md)
Using a whole arrangement of blocks as one recipe input or output.

## 2. Recipes

#### [JSON Format](../recipes/JSON_FORMAT.md)
The JSON format for defining recipes.

#### [Conditions](../recipes/CONDITIONS.md)
What decides whether a recipe runs or stops.

#### [Decorators](../recipes/DECORATORS.md)
Chance, bonus output, catalysts.

#### [Expression Reference](../recipes/EXPRESSION_REFERENCE.md)
The list of variables and functions.

#### [Practical Examples](../recipes/EXPRESSION_EXAMPLES.md)
Usage by pattern.

### Expression system

The Dynamic Amount System (Expression System) is an expression system for varying recipe input and output amounts. It allows flexible recipe design that responds to machine state and world conditions.

**Main Features**:
- **Machine state**: energy, fluid, mana, gas, tier, progress and more
- **World conditions**: time, weather, moon phase, biome, elapsed days and more
- **Math functions**: trigonometry, logarithms, powers, random numbers
- **Branching**: the ternary operator and logical operators for more involved control

**Example**:
```json
{
  "inputs": [
    { "item": "minecraft:coal", "amount": "tier * 10 + 5" }
  ],
  "outputs": [
    { "fluid": "steam", "amount": "energy_p * 1000" }
  ]
}
```

**Related Documentation**:
- [JSON Format: Dynamic amounts](../recipes/JSON_FORMAT.md#4-dynamic-amounts) - basic usage
- [Expression Reference](../recipes/EXPRESSION_REFERENCE.md) - the list of variables and functions
- [Practical Examples](../recipes/EXPRESSION_EXAMPLES.md) - usage by pattern

**Audience**: anyone building their own machine

## 3. Machines

#### [Machine Conditions](./MACHINE_CONDITIONS.md)
Conditions in a structure definition that stop the machine itself, whatever recipe it was going to run.

**Contents**:
- Writing them (array or single, asking about machine state with an expression)
- What happens when they break mid-recipe (`pause` / `abort`), and that `abort` gives nothing back

**Audience**: anyone building their own machine

#### [Port Colours](./PORT_COLORS.md)
Painting a machine's ports so that only ports of the same colour are treated as one group.

**Contents**:
- Painting
- How ports are grouped, and the order groups are checked
- How a recipe is chosen

**Audience**: Players, Modpackers
