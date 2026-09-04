package com.simibubi.create.content.logistics.tunnel;

import java.util.List;

import com.simibubi.create.AllBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class BrassTunnelBlock extends BeltTunnelBlock {

	public BrassTunnelBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return onBlockEntityUse(level, pos, be -> {
			if (!(be instanceof BrassTunnelBlockEntity bte))
				return InteractionResult.PASS;
			List<ItemStack> stacksOfGroup = bte.grabAllStacksOfGroup(level.isClientSide);
			if (stacksOfGroup.isEmpty())
				return InteractionResult.PASS;
			if (level.isClientSide)
				return InteractionResult.SUCCESS;
			for (ItemStack itemStack : stacksOfGroup)
				player.getInventory().placeItemBackInInventory(itemStack.copy());
			level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f,
				1f + level.getRandom().nextFloat());
			return InteractionResult.SUCCESS;
		});
	}

	@Override
	public BlockEntityType<? extends BeltTunnelBlockEntity> getBlockEntityType() {
		return AllBlockEntityTypes.BRASS_TUNNEL.get();
	}

	@Override
	public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor worldIn,
								  BlockPos currentPos, BlockPos facingPos) {
		return super.updateShape(state, facing, facingState, worldIn, currentPos, facingPos);
	}

}
