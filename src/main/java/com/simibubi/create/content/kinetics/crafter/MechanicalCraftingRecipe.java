package com.simibubi.create.content.kinetics.crafter;

import org.jetbrains.annotations.NotNull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllRecipeTypes;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

public class MechanicalCraftingRecipe extends ShapedRecipe {
	private final boolean acceptMirrored;

	public MechanicalCraftingRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
			ShapedRecipePattern pattern, ItemStackTemplate recipeOutputIn, boolean acceptMirrored) {
		super(commonInfo, bookInfo, pattern, recipeOutputIn);
		this.acceptMirrored = acceptMirrored;
	}

	private static MechanicalCraftingRecipe fromShaped(ShapedRecipe recipe, boolean acceptMirrored) {
		ItemStackTemplate result = ItemStackTemplate.fromNonEmptyStack(recipe.assemble(CraftingInput.EMPTY));
		return new MechanicalCraftingRecipe(
				new Recipe.CommonInfo(recipe.showNotification()),
				new CraftingRecipe.CraftingBookInfo(recipe.category(), recipe.group()),
				recipe.pattern, result, acceptMirrored);
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		if (!(input instanceof MechanicalCraftingInput))
			return false;
		if (acceptsMirrored())
			return super.matches(input, level);

		for (int i = 0; i <= input.width() - this.getWidth(); ++i)
			for (int j = 0; j <= input.height() - this.getHeight(); ++j)
				if (this.matchesSpecific(input, i, j))
					return true;
		return false;
	}

	private boolean matchesSpecific(CraftingInput input, int offsetX, int offsetY) {
		NonNullList<Ingredient> ingredients = NonNullList.create();
		for (var ingredient : getIngredients())
			ingredients.add(ingredient.orElse(Ingredient.EMPTY));
		int width = getWidth();
		int height = getHeight();
		for (int i = 0; i < input.width(); ++i) {
			for (int j = 0; j < input.height(); ++j) {
				int k = i - offsetX;
				int l = j - offsetY;
				Ingredient ingredient = Ingredient.EMPTY;
				if (k >= 0 && l >= 0 && k < width && l < height)
					ingredient = ingredients.get(k + l * width);
				if (!ingredient.test(input.getItem(i + j * input.width())))
					return false;
			}
		}
		return true;
	}

	@Override
	public RecipeType<?> getType() {
		return AllRecipeTypes.MECHANICAL_CRAFTING.getType();
	}

	@Override
	public boolean isSpecial() {
		return true;
	}

	@Override
	public @NotNull RecipeSerializer<?> getSerializer() {
		return SERIALIZER;
	}

	public boolean acceptsMirrored() {
		return acceptMirrored;
	}

	public static final MapCodec<MechanicalCraftingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ShapedRecipe.MAP_CODEC.forGetter(recipe -> recipe),
			Codec.BOOL.fieldOf("accept_mirrored").forGetter(MechanicalCraftingRecipe::acceptsMirrored)
		).apply(instance, MechanicalCraftingRecipe::fromShaped));

	public static final StreamCodec<RegistryFriendlyByteBuf, MechanicalCraftingRecipe> STREAM_CODEC = StreamCodec.composite(
			ShapedRecipe.STREAM_CODEC, recipe -> recipe,
			ByteBufCodecs.BOOL, MechanicalCraftingRecipe::acceptsMirrored,
			MechanicalCraftingRecipe::fromShaped
	);

	public static final RecipeSerializer<MechanicalCraftingRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);
}
