package ruiseki.okmodular.api.modular;

import ruiseki.okcore.api.modular.IModularPort;
import ruiseki.okmodular.api.recipe.visitor.IRecipeVisitor;

/**
 * A modular port that participates in recipe execution. Recipe visitation is
 * machinery-specific, so it lives here rather than on OKCore's IModularPort.
 */
public interface IVisitablePort extends IModularPort {

    /**
     * Accept a visitor to perform operations on this port.
     */
    void accept(IRecipeVisitor visitor);
}
