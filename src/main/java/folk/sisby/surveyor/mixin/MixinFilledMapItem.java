package folk.sisby.surveyor.mixin;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorMapIntegration;
import folk.sisby.surveyor.config.SurveyorConfig;
import net.minecraft.block.BlockState;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.map.MapState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FilledMapItem.class)
public class MixinFilledMapItem {
	@Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
	private void addExplorationOnSneakUse(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
		MapState mapState = FilledMapItem.getMapState(context.getStack(), context.getWorld());
		if (mapState != null && context.getPlayer() instanceof ServerPlayerEntity spe && spe.isSneaking()) {
			boolean didThing = false;
			BlockState state = context.getWorld().getBlockState(context.getBlockPos());
			if (Surveyor.CONFIG.builtins.recordFromMapItems && state.isIn(SurveyorMapIntegration.RECORD_FROM_MAP)) {
				SurveyorMapIntegration.recordMapData(spe, mapState);
				didThing = true;
			}
			if (!mapState.locked && Surveyor.CONFIG.builtins.recordToMapItems != SurveyorConfig.Builtins.RecordStyle.NONE && state.isIn(SurveyorMapIntegration.RECORD_TO_MAP)) {
				SurveyorMapIntegration.applyMapData(spe, mapState);
				didThing = true;
			}
			if (didThing) {
				context.getPlayer().getWorld().playSoundFromEntity(null, context.getPlayer(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, context.getPlayer().getSoundCategory(), 1.0F, 0.7F);
				cir.setReturnValue(ActionResult.SUCCESS);
			}
		}
	}
}
