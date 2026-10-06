package me.shiiyuko.manosaba;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = ManosabaMod.MOD_ID, dist = Dist.CLIENT)
public class ManosabaMod {

    public static final String MOD_ID = "manosaba";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> TITLE_MUSIC =
            SOUNDS.register("music", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "music")));

    /** 确认类操作音效（按钮点击、按键绑定完成等），资源：Sfx_System_Submit_001.ogg */
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_SUBMIT =
            SOUNDS.register("ui_submit", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "ui_submit")));

    /** 取消类操作音效（关闭、中断、返回等），资源：Sfx_System_Cancel_001.ogg */
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_CANCEL =
            SOUNDS.register("ui_cancel", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "ui_cancel")));

    public ManosabaMod(IEventBus modBus, ModContainer container) {
        SOUNDS.register(modBus);
        container.registerConfig(ModConfig.Type.CLIENT, ManosabaClientConfig.SPEC);
        LOGGER.info("Manosaba initialized!");
        preloadSkikoNativeLibrary();
    }

    /**
     * Eagerly loads the skiko native library at mod construction time, before any UI
     * rendering can trigger it.
     *
     * <p>skiko's {@code Library.load()} sets its "loaded" flag before actually loading the
     * library and never resets it on failure. If the very first load attempt happens inside
     * code that swallows exceptions (e.g. runCatching in the UI classes), the flag stays
     * poisoned and every later load() silently no-ops, making the first native call explode
     * with UnsatisfiedLinkError (e.g. Paint_nMake). Doing it here guarantees the first
     * attempt either succeeds or is reported to the log.
     */
    private static void preloadSkikoNativeLibrary() {
        try {
            Class<?> libraryClass = Class.forName("org.jetbrains.skia.impl.Library");
            libraryClass.getMethod("staticLoad").invoke(null);
            // Provoke a real native call to verify the JNI bindings are actually resolved
            // (staticLoad silently no-ops if the loaded flag was already poisoned elsewhere).
            Class.forName("org.jetbrains.skia.Paint").getConstructor().newInstance();
            LOGGER.info("Skiko native library preloaded OK");
        } catch (Throwable t) {
            LOGGER.error("Skiko native library preload FAILED - UI rendering will throw UnsatisfiedLinkError", t);
        }
    }
}
