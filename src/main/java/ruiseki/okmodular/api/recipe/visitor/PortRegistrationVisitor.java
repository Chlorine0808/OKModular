package ruiseki.okmodular.api.recipe.visitor;

import ruiseki.okmodular.api.modular.IMachineController;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IVisitablePort;

/**
 * Visitor that handles the registration of ports into the controller.
 */
public class PortRegistrationVisitor implements IRecipeVisitor {

    private final IMachineController controller;

    public PortRegistrationVisitor(IMachineController controller) {
        this.controller = controller;
    }

    /**
     * Helper to visit a port regardless of its specific type.
     */
    public void visitPort(IModularPort port) {
        if (port instanceof IVisitablePort visitable) {
            visitable.accept(this);
        }
    }

    public void register(IModularPort port) {
        switch (port.getPortDirection()) {
            case INPUT:
                controller.addPort(port, true);
                break;
            case OUTPUT:
                controller.addPort(port, false);
                break;
            case BOTH:
                controller.addPort(port, true);
                controller.addPort(port, false);
                break;
        }
    }
}
