package ruiseki.okmodular.api.structure.visitor;

import ruiseki.okmodular.api.structure.core.IStructureEntry;
import ruiseki.okmodular.api.structure.io.IStructureRequirement;

/**
 * Visitor interface for navigating structure definitions.
 */
public interface IStructureVisitor {

    void visit(IStructureEntry entry);

    void visit(IStructureRequirement requirement);
}
