package local.mcafk.mixin;

import local.mcafk.MacroCapture;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void mcafk$captureKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        MacroCapture.key(action, event);
    }

    @Inject(method = "charTyped", at = @At("HEAD"))
    private void mcafk$captureCharacter(long window, CharacterEvent event, CallbackInfo ci) {
        MacroCapture.character(event);
    }
}
