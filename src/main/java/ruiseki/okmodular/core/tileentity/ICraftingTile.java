package ruiseki.okmodular.core.tileentity;

import ruiseki.okmodular.api.enums.CraftingState;

public interface ICraftingTile extends IProgressTile {

    CraftingState getCraftingState();

    void setCraftingState(CraftingState state);
}
