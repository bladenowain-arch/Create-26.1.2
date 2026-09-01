package com.simibubi.create.content.equipment.blueprint;

import com.simibubi.create.AllPackets;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public record BlueprintAssignCompleteRecipePacket(Identifier recipeId) implements ServerboundPacketPayload {
	public static final StreamCodec<ByteBuf, BlueprintAssignCompleteRecipePacket> STREAM_CODEC = Identifier.STREAM_CODEC.map(
			BlueprintAssignCompleteRecipePacket::new, BlueprintAssignCompleteRecipePacket::recipeId
	);

	@Override
	public void handle(ServerPlayer player) {
		if (player.containerMenu instanceof BlueprintMenu c) {
			player.level()
					.recipeAccess()
					.byKey(recipeId)
					.ifPresent(r -> BlueprintItem.assignCompleteRecipe(c.player.level(), c.ghostInventory, r.value()));
		}
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.BLUEPRINT_COMPLETE_RECIPE;
	}
}
