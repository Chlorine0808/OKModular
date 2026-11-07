package ruiseki.okmodular.common.recipe.capture;

import java.util.List;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.modular.IModularPort;

/** Reads what one port holds as recipe IO entries, written by the real IO classes. */
@FunctionalInterface
public interface PortCapturer {

    /** @param output true when the port is an output port, so entries are written as outputs */
    List<JsonObject> capture(IModularPort port, boolean output);
}
