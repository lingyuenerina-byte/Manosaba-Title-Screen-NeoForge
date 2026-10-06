package me.shiiyuko.manosaba.mixin;

import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 屏蔽原版情景音乐（主界面 menu 音乐、群系音乐等）——用户报修：主界面需一直循环
 * music.ogg（ManosabaMod.TITLE_MUSIC），原版音乐会在数秒后自动开始并叠加。
 *
 * 标题音乐经 SoundManager 直接循环播放、不经过 MusicManager，
 * 因此直接取消 MusicManager#tick 不会影响 music.ogg 的循环；
 * 但原版所有由 MusicManager 调度的音乐（含 NeoForge 选择的群系音乐）都不会再播放。
 */
@Mixin(MusicManager.class)
public class MusicManagerMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void manosaba$blockVanillaMusic(CallbackInfo ci) {
        ci.cancel();
    }
}
