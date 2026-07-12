package ruiseki.okmodular.api.modular;

import ruiseki.okcore.api.modular.IModularPort;
import ruiseki.okmodular.api.recipe.visitor.IRecipeVisitor;
import ruiseki.okmodular.core.tileentity.ISidedIO;

/**
 * A modular port that participates in recipe execution. Recipe visitation,
 * sided IO and sided textures are machinery-specific, so they live here
 * rather than on OKCore's IModularPort.
 */
public interface IVisitablePort extends IModularPort, ISidedIO, ISidedTexture {

    /**
     * Accept a visitor to perform operations on this port.
     */
    void accept(IRecipeVisitor visitor);
}
