package ruiseki.okmodular.client.nei.modular.layout;

import ruiseki.okmodular.client.nei.modular.renderer.INEIPositionedRenderer;

public class LayoutPartVis extends LayoutPartRenderer {

    public LayoutPartVis(INEIPositionedRenderer renderer) {
        super(renderer);
    }

    @Override
    public int getMaxHorizontalCount() {
        return 6;
    }

    @Override
    public int getSortOrder() {
        return 50;
    }
}
