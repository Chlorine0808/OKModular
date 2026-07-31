# Port Colours

Paint a machine's ports and **only ports of the same colour are treated as one group**.
One machine can then hold several lines that take material independently.

## 📚 Related Documentation

- [Modular Machinery Documentation](./INDEX.md)
- [Recipe JSON Format](../recipes/JSON_FORMAT.md)

---

## 1. Summary

For managing several kinds of recipe through one machine.

- Material put in a red port comes out of a red port
- Material put in a blue port comes out of a blue port
- Red and blue never combine to satisfy one recipe
- Only one recipe runs at a time; this is not parallel processing.

## 2. Painting

| Action | Result |
|---|---|
| **Right-click a port with a dye** | Paints it that colour (consumes the dye) |
| **Right-click with a bucket of water** | Washes the colour off (the water is not used up) |
| **Shift + Right-click with AE2's Color Applicator** | Paints |
| **Right-click with IC2's Painter** | Paints |

Holding neither a dye nor a water bucket, right-clicking opens the GUI as before.
Painting a port the colour it already is consumes no dye.

The colour also shows in the WAILA tooltip.

> [!NOTE]
External block ports cannot be painted yet.
They count as unpainted, so by the rules below every colour shares them.

## 3. The order recipes run in

| # | Rule |
|---|---|
| 1 | A painted port belongs to its own colour's group |
| 2 | An unpainted port belongs to every colour's group |
| 3 | Groups are looked at in metadata order, that is white → orange → magenta → light blue → yellow → lime → pink → gray → light gray → cyan → purple → blue → brown → green → red → black |
| 4 | The group of unpainted ports only comes last |
| 5 | The controller itself belongs to every group, whatever its colour |

For example, this arrangement gives:

```
red ports:  item in, item out
blue ports: item in, item out
unpainted:  one energy input

-> red group  = red in, red out, the unpainted energy hatch
-> blue group = blue in, blue out, the unpainted energy hatch
```

Material in an unpainted input port is visible to every colour.


## 4. How a recipe is chosen

Groups are tried in order, and the first recipe that both matches and has room for its
output runs.

> [!NOTE]
> A group whose output is blocked is skipped.
> A full red output tank does not hold blue back.

When no group can run, the GUI shows the error from the highest-priority colour.

## 5. Tips

Breaking a port does not lose its colour.

Colour is a separate axis from ordered port positions (`portIndex` in structure JSON).
The index is read within a colour's group, so both work at once.
