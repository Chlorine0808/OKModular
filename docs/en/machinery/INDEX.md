# Modular Machinery Documentation

Technical documentation for the Modular Machinery module.

## 📚 Documentation

### System Design

#### [External Port Proxy System](./EXTERNAL_PROXY.md)
Design documentation for the proxy system that integrates external blocks (chests, tanks, energy storage, etc.) as part of the machine.

**Contents**:
- Fusion of Adapter + Proxy patterns
- 6 types of proxy implementations (Item, Fluid, Energy, Gas, Essentia, Mana)
- AbstractExternalProxy base class details
- Integration with Self-Validation Pattern
- Proxy factory registration methods
- Code examples and usage

**Target Audience**: Developers, Design Pattern Learners

---

#### [Machine Conditions](./MACHINE_CONDITIONS.md)
Conditions in a structure definition that stop the machine itself, whatever recipe it was going to run.

**Contents**:
- How they differ from a recipe's conditions (they work independently)
- Writing them (array or single, asking about machine state with an expression)
- What happens when they break mid-recipe (`pause` / `abort`), and that `abort` gives nothing back
- A misspelled condition loosens the gate rather than closing it

**Audience**: anyone writing structure definitions

---

#### [Port Colours](./PORT_COLORS.md)
Painting a machine's ports so that only ports of the same colour are considered
together.

**Contents**:
- Painting (dyes, AE2's Color Applicator)
- Which ports belong to which group (five rules)
- Why unpainted ports are shared by every colour, and what that costs
- How a recipe is chosen (a group with blocked output is skipped)

**Target Audience**: Players, Modpack Authors

---

#### [The Wrench](./WRENCH.md)
The tool that sets a port's per-side IO and registers external blocks as ports.

**Contents**:
- Every control (side IO, port kind, linking, registering an external port)
- The 3 × 3 sections and which side each one selects
- What is highlighted while it is held (green and amber controllers, lines to ports)
- Reading the tooltip, and what is saved where

**Target Audience**: Players, Modpack Authors

---

## 💡 New Features Guide

### Dynamic Amount System (Expression System)
An expression system for dynamically changing recipe input/output amounts. Enables flexible recipe design based on machine state and world environment.

**Main Features**:
- **Machine State Reference**: Energy, fluids, mana, gas, Tier, progress, etc.
- **World Environment Reference**: Time, weather, moon phase, biome, elapsed days, etc.
- **Mathematical Functions**: Trigonometric functions, logarithms, exponentiation, random numbers, etc.
- **Conditional Branching**: Complex control via ternary operators and logical operators

**Usage Example**:
```json
{
  "inputs": [
    { "item": "minecraft:iron_ingot", "amount": "tier * 10 + 5" }
  ],
  "outputs": [
    { "fluid": "steam", "amount": "energy_p * 1000" }
  ]
}
```

**Related Documentation**:
- [JSON Format: Dynamic Amount](../recipes/JSON_FORMAT.md#31-dynamic-amount) - Basic usage
- [Practical Examples](../recipes/EXPRESSION_EXAMPLES.md) - Detailed usage examples by pattern

**Target Audience**: Recipe creators, Modpack developers

---

## 🔗 Related Documentation

### Recipe System
- [Overview](../recipes/OVERVIEW.md)
- [JSON Format](../recipes/JSON_FORMAT.md)
- [Practical Examples](../recipes/EXPRESSION_EXAMPLES.md) 🆕
- [Developer Guide](../recipes/DEVELOPER_GUIDE.md)

### Structure System
- [Overview](../structures/OVERVIEW.md)
- [JSON Format](../structures/JSON_FORMAT.md)
- [Developer Guide](../structures/DEVELOPER_GUIDE.md)

---

*This documentation is updated regularly.*
