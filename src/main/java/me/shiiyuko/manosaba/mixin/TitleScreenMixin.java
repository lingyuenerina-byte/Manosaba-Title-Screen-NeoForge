package me.shiiyuko.manosaba.mixin;

import me.shiiyuko.manosaba.bridge.ActiveUiReclaimer;
import me.shiiyuko.manosaba.ui.ManosabaTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void onInit(CallbackInfo ci) {
        // 兜底释放：连接中屏（TEST45）成功进服后界面可能仍登记（该屏无 removed 可注入），
        // 玩家退出服务器 / 服务器断连回标题屏时在此统一释放
        ActiveUiReclaimer.dispose();
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new ManosabaTitleScreen());
        ci.cancel();
    }
}
