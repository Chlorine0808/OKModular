package ruiseki.okmodular;

/**
 * Registry-name definitions for OK Modular's blocks, previously part of the
 * parent mod's OKMObjects enum. Only the machinery entries live here; the
 * registry domain is provided by this mod (okmodular:).
 */
public enum OKMObjects {

    // spotless: off

    blockModularItemInput("modular_item_input"),
    blockModularItemOutput("modular_item_output"),
    blockModularItemOutputME("modular_item_output_me"),
    blockModularEnergyInput("modular_energy_input"),
    blockModularEnergyOutput("modular_energy_output"),
    blockModularFluidInput("modular_fluid_input"),
    blockModularFluidOutput("modular_fluid_output"),
    blockModularFluidOutputME("modular_fluid_output_me"),
    blockModularManaInput("modular_mana_input"),
    blockModularManaOutput("modular_mana_output"),
    blockModularGasInput("modular_gas_input"),
    blockModularGasOutput("modular_gas_output"),
    blockModularEssentiaInput("modular_essentia_input"),
    blockModularEssentiaOutput("modular_essentia_output"),
    blockModularEssentiaInputME("modular_essentia_input_me"),
    blockModularVisInput("modular_vis_input"),
    blockModularVisOutput("modular_vis_output"),
    blockVisBridge("vis_bridge"),
    MODULAR_MACHINE_CONTROLLER("modular_machine_controller"),

    ;
    // spotless: on

    public final String name;

    OKMObjects(String name) {
        this.name = name;
    }

    public String getRegistryName() {
        return Reference.MOD_ID + ":" + name;
    }
}
