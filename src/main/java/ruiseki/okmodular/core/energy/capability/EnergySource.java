package ruiseki.okmodular.core.energy.capability;

public interface EnergySource {

    int extract(int amount, boolean simulate);

    boolean canConnect();
}
