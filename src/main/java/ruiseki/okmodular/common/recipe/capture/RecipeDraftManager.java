package ruiseki.okmodular.common.recipe.capture;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/** One recipe draft per player, dropped on logout and when the server stops. */
public final class RecipeDraftManager {

    private static final RecipeDraftManager INSTANCE = new RecipeDraftManager();

    private final Map<UUID, RecipeDraft> drafts = new ConcurrentHashMap<>();

    private RecipeDraftManager() {}

    public static RecipeDraftManager getInstance() {
        return INSTANCE;
    }

    public void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    public RecipeDraft get(UUID player) {
        return drafts.get(player);
    }

    public void put(UUID player, RecipeDraft draft) {
        drafts.put(player, draft);
    }

    public void clear(UUID player) {
        drafts.remove(player);
    }

    public void clearAll() {
        drafts.clear();
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) clear(event.player.getUniqueID());
    }
}
