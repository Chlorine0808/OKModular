package ruiseki.okmodular.client.nei.modular.layout;

import ruiseki.okmodular.client.nei.modular.renderer.INEIPositionedRenderer;

public class LayoutPartEnergy extends LayoutPartRenderer {

    public LayoutPartEnergy(INEIPositionedRenderer renderer) {
        super(renderer);
    }

    @Override
    public int getMaxHorizontalCount() {
        return 1;
    }

    @Override
    public int getSortOrder() {
        return 1000;
    }
}
