package com.simibubi.create.content.equipment.blueprint;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.logistics.filter.AttributeFilterWhitelistMode;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute.ItemAttributeEntry;
import com.simibubi.create.content.logistics.item.filter.attribute.attributes.InTagAttribute;
import com.simibubi.create.foundation.item.ItemHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.items.ItemStackHandler;

public class BlueprintItem extends Item {

	public BlueprintItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Direction face = ctx.getClickedFace();
		Player player = ctx.getPlayer();
		ItemStack stack = ctx.getItemInHand();
		BlockPos pos = ctx.getClickedPos()
			.relative(face);

		if (player != null && !player.mayUseItemAt(pos, face, stack))
			return InteractionResult.FAIL;

		Level world = ctx.getLevel();
		HangingEntity hangingentity = new BlueprintEntity(world, pos, face, face.getAxis()
			.isHorizontal() ? Direction.DOWN : ctx.getHorizontalDirection());
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);

		if (customData != null)
			EntityType.updateCustomEntityTag(world, player, hangingentity, customData);
		if (!hangingentity.survives())
			return InteractionResult.CONSUME;
		if (!world.isClientSide) {
			hangingentity.playPlacementSound();
			world.addFreshEntity(hangingentity);
		}

		stack.shrink(1);
		return InteractionResult.sidedSuccess(world.isClientSide);
	}

	public static void assignCompleteRecipe(Level level, ItemStackHandler inv, Recipe<?> recipe) {
		List<Ingredient> ingredients;
		if (recipe instanceof ShapedRecipe shapedRecipe)
			ingredients = shapedRecipe.getIngredients().stream().map(optional -> optional.orElse(Ingredient.EMPTY)).toList();
		else if (recipe instanceof CraftingRecipe craftingRecipe)
			ingredients = craftingRecipe.placementInfo().stackedRecipeContents().toList();
		else
			return;

		for (int i = 0; i < 9; i++)
			inv.setStackInSlot(i, ItemStack.EMPTY);

		ItemStack result = recipe.display().stream()
			.findFirst()
			.map(display -> display.result().resolveForFirstStack(ContextMap.Builder.create(ContextKeySet.EMPTY).build()))
			.orElse(ItemStack.EMPTY);
		inv.setStackInSlot(9, result);

		if (recipe instanceof ShapedRecipe shapedRecipe) {
			for (int row = 0; row < shapedRecipe.getHeight(); row++)
				for (int col = 0; col < shapedRecipe.getWidth(); col++)
					inv.setStackInSlot(row * 3 + col,
						convertIngredientToFilter(ingredients.get(row * shapedRecipe.getWidth() + col)));
		} else {
			for (int i = 0; i < ingredients.size(); i++)
				inv.setStackInSlot(i, convertIngredientToFilter(ingredients.get(i)));
		}
	}

	private static ItemStack convertIngredientToFilter(Ingredient ingredient) {
		boolean isCompoundIngredient = ingredient.getCustomIngredient() instanceof CompoundIngredient;
		List<ItemStack> acceptedItems = ingredient.items().map(Holder::value).map(ItemStack::new).toList();
		if (acceptedItems.size() > 18 || acceptedItems.isEmpty())
			return ItemStack.EMPTY;
		if (acceptedItems.size() == 1)
			return convertIItemListToFilter(acceptedItems.stream(), isCompoundIngredient);

		ItemStack result = AllItems.FILTER.asStack();
		ItemStackHandler filterItems = AllItems.FILTER.get().getFilterItemHandler(result);
		for (int i = 0; i < acceptedItems.size(); i++)
			filterItems.setStackInSlot(i, acceptedItems.get(i));
		result.set(AllDataComponents.FILTER_ITEMS, ItemHelper.containerContentsFromHandler(filterItems));
		return result;
	}

	private static ItemStack convertIItemListToFilter(Stream<ItemStack> stacks, boolean isCompoundIngredient) {
		if (!isCompoundIngredient)
			return stacks.findFirst().orElse(ItemStack.EMPTY);

		ItemStack result = AllItems.FILTER.asStack();
		ItemStackHandler filterItems = AllItems.FILTER.get().getFilterItemHandler(result);
		int i = 0;
		for (ItemStack itemStack : stacks.toList()) {
			if (i >= 18)
				break;
			filterItems.setStackInSlot(i++, itemStack);
		}
		result.set(AllDataComponents.FILTER_ITEMS, ItemHelper.containerContentsFromHandler(filterItems));
		result.set(AllDataComponents.FILTER_ITEMS_RESPECT_NBT, true);
		return result;
	}

}
