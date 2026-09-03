package com.simibubi.create.api.data.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import com.simibubi.create.Create;

import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

/**
 * A class containing some basic setup for other recipe generators to use.
 * Addons should extend this if they add a custom recipe type that is not
 * a processing recipe type and want to use Create's helpers.
 * For processing recipes extend {@link StandardProcessingRecipeGen}.
 */
public abstract class BaseRecipeProvider {
	protected final String modid;
	protected final List<GeneratedRecipe> all = new ArrayList<>();

	public BaseRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, String defaultNamespace) {
		this.modid = defaultNamespace;
	}

	protected Identifier asResource(String path) {
		return Identifier.fromNamespaceAndPath(modid, path);
	}

	protected GeneratedRecipe register(GeneratedRecipe recipe) {
		all.add(recipe);
		return recipe;
	}

	/**
	 * Registers all recipes accumulated by this generator against the supplied
	 * 26.1 {@link RecipeOutput}.
	 */
	public void buildRecipes(RecipeOutput recipeOutput) {
		all.forEach(c -> c.register(recipeOutput));
		Create.LOGGER.info("{} registered {} recipe{}", getName(), all.size(), all.size() == 1 ? "" : "s");
	}

	protected Criterion<InventoryChangeTrigger.TriggerInstance> inventoryTrigger(ItemPredicate... items) {
		return InventoryChangeTrigger.TriggerInstance.hasItems(items);
	}

	protected Criterion<InventoryChangeTrigger.TriggerInstance> inventoryTrigger(ItemLike item) {
		return InventoryChangeTrigger.TriggerInstance.hasItems(item);
	}

	public String getName() {
		return modid + " recipes";
	}

	@FunctionalInterface
	public interface GeneratedRecipe {
		void register(RecipeOutput recipeOutput);
	}

	/**
	 * Adapts Create's recipe generator helpers to Minecraft 26.1's
	 * {@link RecipeProvider.Runner} based data generation API.
	 */
	public static class Runner extends RecipeProvider.Runner {
		private final PackOutput output;
		private final CompletableFuture<HolderLookup.Provider> registries;
		private final BiFunction<PackOutput, CompletableFuture<HolderLookup.Provider>, BaseRecipeProvider> factory;
		private final String name;

		public Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> registries,
				BiFunction<PackOutput, CompletableFuture<HolderLookup.Provider>, BaseRecipeProvider> factory, String name) {
			super(output, registries);
			this.output = output;
			this.registries = registries;
			this.factory = factory;
			this.name = name;
		}

		@Override
		protected RecipeProvider createRecipeProvider(HolderLookup.Provider provider, RecipeOutput recipeOutput) {
			BaseRecipeProvider generator = factory.apply(output, registries);
			return new RecipeProvider(provider, recipeOutput) {
				@Override
				protected void buildRecipes() {
					generator.buildRecipes(recipeOutput);
				}
			};
		}

		@Override
		public String getName() {
			return name;
		}
	}
}
