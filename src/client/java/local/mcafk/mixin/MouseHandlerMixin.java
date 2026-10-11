package local.mcafk.mixin;

import local.mcafk.MacroCapture;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void mcafk$captureButton(
            long window, MouseButtonInfo info, int action, CallbackInfo ci
    ) {
        MacroCapture.mouseButton(action, info);
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    private void mcafk$captureMove(
            long window, double x, double y, double deltaX, double deltaY, CallbackInfo ci
    ) {
        MacroCapture.mouseMove(x, y, deltaX, deltaY);
    }

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void mcafk$captureScroll(
            long window, double x, double y, CallbackInfo ci
    ) {
        MacroCapture.scroll(x, y);
    }
}
