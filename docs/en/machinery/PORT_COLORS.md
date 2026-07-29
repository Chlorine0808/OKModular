# Port Colours

Paint a machine's ports and **only ports of the same colour are considered together**.
One machine can then hold several independent lines that take material separately.

## 📚 Related Documentation

- [Modular Machinery Documentation](./INDEX.md)
- [Recipe JSON Format](../recipes/JSON_FORMAT.md)

---

## 1. What it does

For running several lines through one machine, assembly-line style.

- Material put in a red port comes out of a red port
- Material put in a blue port comes out of a blue port
- Red and blue never combine to satisfy one recipe

> [!IMPORTANT]
> **Only one recipe runs at a time.**
> Colours decide which material is looked at together, and which colour is looked at
> first. **They are not parallel processing.** With material in both red and blue, the
> machine runs the red recipe, finishes it, and then runs the blue one.

## 2. Painting

| Action | Result |
|---|---|
| **Right-click a port with a dye** | Paints it that colour (consumes one dye) |
| **Right-click with a bucket of water** | Washes the colour off (**the water is not used up**) |
| **Shift + Right-click with AE2's Color Applicator** | Paints |
| **Right-click with IC2's Painter** | Paints |

Holding neither a dye nor a water bucket, right-clicking opens the GUI as before.
Painting a port the colour it already is consumes no dye.

The colour also shows in the WAILA tooltip, which is how to check one: a machine with
its own colour scheme can make a painted port hard to read at a glance.

> [!NOTE]
> **External blocks used as ports - a vanilla chest, say - cannot be painted yet.** They
> count as unpainted, so by the rules below every colour shares them.

## 3. Which ports belong to which group

Five rules.

| # | Rule |
|---|---|
| 1 | A painted port belongs **only to its own colour's group** |
| 2 | **An unpainted port belongs to every group** |
| 3 | Groups are looked at in the order white, orange, magenta, light blue, yellow, lime, pink, gray, light gray, cyan, purple, blue, brown, green, red, black |
| 4 | The group of **unpainted ports comes last** |
| 5 | The controller itself belongs to every group, whatever its colour |

### What rule 2 is for

So that an energy hatch does not have to exist once per colour.

```
red ports:  item in, item out
blue ports: item in, item out
unpainted:  one energy input

-> red group  = red in, red out, the unpainted energy hatch
-> blue group = blue in, blue out, the unpainted energy hatch
```

**It has a cost.** Material in an unpainted input port is visible to **every** colour,
so colours do not isolate completely. For full separation, **paint every port**.

### What rule 4 is for

It follows from rule 2. The unpainted group holds every unpainted port, so it matches
almost anything. Looked at first, it would make painting pointless.

A machine with no unpainted ports at all gets no such group.

## 4. How a recipe is chosen

Groups are tried in order, and the first one that **both matches a recipe and has room
for its output** runs.

> [!NOTE]
> **A group whose output is blocked is skipped, not treated as a stop.**
> A full red output tank does not hold blue back.

When no group can run, the GUI shows the failure from the **highest-priority colour**
that got that far.

## 5. Appearance

A painted port renders in its colour, and **that wins over any tint the machine's
structure sets**. An unpainted port looks exactly as it did.

## 6. Saving

Colours are stored in the world and survive a reload.

**Breaking a port does not lose its colour.** The dropped item carries it, putting the
port back down restores it, and the item's tooltip says which colour it is.

> [!NOTE]
> **Per-side IO settings do reset when a port is placed again.** That is separate from
> colour and has always been the case - they are set from the direction it is placed
> facing.

Colour is a **separate axis** from ordered port positions (`portIndex` in structure
JSON): the index is read within a colour's group, so both work at once.
