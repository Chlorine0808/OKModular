# The Wrench

The wrench sets a port's **per-side IO** and registers **external blocks as ports**.
Neither is visible from the block itself, so the wrench **highlights what it can act on
just for being held**.

## 📚 Related Documentation

- [Modular Machinery Documentation](./INDEX.md)
- [Port Colours](./PORT_COLORS.md)

---

## 1. Controls

| Action | Target | Result |
|---|---|---|
| **Right-click** | a port | advance the IO of the side the clicked section selects (none → input → output → …) |
| **Left-click** | a port | the same, backwards (the block is not broken) |
| **Right-click** | empty air | advance the selected port kind |
| **Sneak + right-click** | empty air | go back a port kind |
| **Sneak + right-click** | a controller | link to that controller |
| **Sneak + right-click** | any other block | register that block with the linked controller as an external port **of the selected kind** |
| **Sneak + left-click** | anything | drop the link |

> [!IMPORTANT]
> **Registering an external port needs a link to a controller first.**
> Sneak + right-clicking a block with an unlinked wrench does nothing at all.
> Whether a wrench is linked shows in its tooltip and in the highlight below.

## 2. Which side gets changed

A port's face is divided into a 3 × 3 grid, and **the section clicked decides which
side's IO changes**. Not the face itself - **the side the section is nearest to**.

| Section | Side selected |
|---|---|
| centre | the face that was clicked |
| edge (four) | the neighbouring side that edge runs along |
| corner (four) | the side **opposite** the clicked face |

Sections are laid out in world coordinates. Clicking the northern edge of the top face
selects the north side; clicking the upper edge of the west face selects the top.

Holding a wrench and looking at a port draws that 3 × 3 grid on the face. The section
under the cursor is filled in - **green in the centre, yellow everywhere else**.

The side's current IO appears in the WAILA tooltip, and **it changes as the cursor
moves**: it reports the side the clicked section would select, not the face being
looked at.

## 3. The highlight

While a wrench is held:

| What you see | Meaning |
|---|---|
| white outline and 3 × 3 grid on a port face | per-side IO can be set here (above) |
| **green box** around a whole block | the controller this wrench is linked to |
| **amber box** around a whole block | the controller being looked at - sneak + right-click links it |
| lines out of a controller | registered external ports: blue for input, orange for output, purple for both |
| `[ kind : direction ]` floating at an external port | what that block is registered as, and which way |

> [!NOTE]
> **The linked controller and its port lines draw through terrain.**
> Registering an external port on the far side of a machine normally means the
> controller is not in sight.
>
> A controller is not given the 3 × 3 grid. It has no per-side IO, so the grid would
> draw an interaction that does not exist.

A `[ kind : direction ]` label ending in **`fixed`** means the structure definition
pins that IO, and the wrench cannot change it.

## 4. Tooltip

- The **item name** carries the selected port kind in brackets (`Wrench (Item)`), so
  cycling kinds with a right-click in air renames the item in hand
- The **link** line reads green with coordinates when linked, yellow when not

## 5. What is saved where

The link and the selected port kind live in the **wrench's own item NBT**. Switching to
a different wrench does not carry them over.

The external port registrations themselves are saved **on the controller**. Throwing the
wrench away only loses the link; ports already registered keep working.
