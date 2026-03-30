package folk.sisby.surveyor.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractBlock.class)
public interface AccessAbstractBlock {
	@Accessor
	boolean isCollidable();

	@Invoker
	ItemStack invokeGetPickStack(final WorldView world, final BlockPos pos, final BlockState state, boolean includeData);
}
