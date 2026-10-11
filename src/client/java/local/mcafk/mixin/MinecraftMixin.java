package local.mcafk.mixin;

import local.mcafk.MacroCapture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "isWindowActive", at = @At("HEAD"), cancellable = true)
    private void mcafk$allowWindowInputDuringPlayback(CallbackInfoReturnable<Boolean> cir) {
        if (MacroCapture.isDispatchingMousePlayback()) {
            cir.setReturnValue(true);
        }
    }
}
