package ruiseki.okmodular.client.gui.widget;

import java.util.List;

import com.cleanroommc.modularui.value.sync.BooleanSyncValue;

/** Two-state button: the first variant shows {@code true}, the second {@code false}. */
public class ToggleWidget extends CyclicVariantButtonWidget {

    public ToggleWidget(List<CyclicVariantButtonWidget.Variant> variants, BooleanSyncValue syncer) {
        super(variants, syncer.getValue() ? 0 : 1, 1, 16, value -> { syncer.setValue(value == 0); });
        size(18);
    }
}
