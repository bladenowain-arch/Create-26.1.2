package com.simibubi.create;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.joml.Vector3f;

import com.simibubi.create.AllTags.AllFluidTags;
import com.simibubi.create.AllTags.AllItemTags;
import com.simibubi.create.content.decoration.palettes.AllPaletteStoneTypes;
import com.simibubi.create.content.fluids.VirtualFluid;
import com.simibubi.create.content.fluids.potion.PotionFluid;
import com.simibubi.create.content.fluids.potion.PotionFluid.PotionFluidType;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.tterrag.registrate.builders.FluidBuilder.FluidTypeFactory;
import com.tterrag.registrate.util.entry.FluidEntry;

import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry.InteractionInformation;
import net.neoforged.neoforge.fluids.FluidType;

public class AllFluids {
	private static final CreateRegistrate REGISTRATE = Create.registrate();

	static {
		REGISTRATE.setCreativeTab(AllCreativeModeTabs.BASE_CREATIVE_TAB);
	}

	public static final FluidEntry<PotionFluid> POTION =
		REGISTRATE.virtualFluid("potion", PotionFluidType::new, PotionFluid::createSource, PotionFluid::createFlowing)
			.lang("Potion")
			.register();

	public static final FluidEntry<VirtualFluid> TEA = REGISTRATE.virtualFluid("tea")
		.lang("Builder's Tea")
		.tag(AllFluidTags.TEA.tag)
		.register();

	public static final FluidEntry<BaseFlowingFluid.Flowing> HONEY =
		REGISTRATE.standardFluid("honey",
				SolidRenderedPlaceableFluidType.create(0xEAAE2F,
					() -> 1f / 8f * AllConfigs.client().honeyTransparencyMultiplier.getF()))
			.lang("Honey")
			.properties(b -> b.viscosity(2000).density(1400))
			.fluidProperties(p -> p.levelDecreasePerBlock(2).tickRate(25).slopeFindDistance(3).explosionResistance(100f))
			.tag(Tags.Fluids.HONEY)
			.source(BaseFlowingFluid.Source::new)
			.block()
			.properties(p -> p.mapColor(MapColor.TERRACOTTA_YELLOW))
			.build()
			.bucket()
			.onRegister(AllFluids::registerFluidDispenseBehavior)
			.tag(Tags.Items.BUCKETS, AllItemTags.HONEY_BUCKETS.tag)
			.build()
			.register();

	public static final FluidEntry<BaseFlowingFluid.Flowing> CHOCOLATE =
		REGISTRATE.standardFluid("chocolate",
				SolidRenderedPlaceableFluidType.create(0x622020,
					() -> 1f / 32f * AllConfigs.client().chocolateTransparencyMultiplier.getF()))
			.lang("Chocolate")
			.tag(AllFluidTags.CHOCOLATE.tag)
			.properties(b -> b.viscosity(1500).density(1400))
			.fluidProperties(p -> p.levelDecreasePerBlock(2).tickRate(25).slopeFindDistance(3).explosionResistance(100f))
			.source(BaseFlowingFluid.Source::new)
			.block()
			.properties(p -> p.mapColor(MapColor.TERRACOTTA_BROWN))
			.build()
			.bucket()
			.onRegister(AllFluids::registerFluidDispenseBehavior)
			.tag(Tags.Items.BUCKETS, AllItemTags.CHOCOLATE_BUCKETS.tag)
			.build()
			.register();

	public static void register() {}

	public static void registerFluidInteractions() {
		FluidInteractionRegistry.addInteraction(NeoForgeMod.LAVA_TYPE.value(), new InteractionInformation(HONEY.get().getFluidType(),
			fluidState -> fluidState.isSource() ? Blocks.OBSIDIAN.defaultBlockState() : AllPaletteStoneTypes.LIMESTONE.getBaseBlock().get().defaultBlockState()));
		FluidInteractionRegistry.addInteraction(NeoForgeMod.LAVA_TYPE.value(), new InteractionInformation(CHOCOLATE.get().getFluidType(),
			fluidState -> fluidState.isSource() ? Blocks.OBSIDIAN.defaultBlockState() : AllPaletteStoneTypes.SCORIA.getBaseBlock().get().defaultBlockState()));
	}

	@org.jetbrains.annotations.Nullable
	public static BlockState getLavaInteraction(FluidState fluidState) {
		Fluid fluid = fluidState.getType();
		if (fluid.isSame(HONEY.get())) return AllPaletteStoneTypes.LIMESTONE.getBaseBlock().get().defaultBlockState();
		if (fluid.isSame(CHOCOLATE.get())) return AllPaletteStoneTypes.SCORIA.getBaseBlock().get().defaultBlockState();
		return null;
	}

	private static final DispenseItemBehavior DEFAULT = new DefaultDispenseItemBehavior();
	private static final DispenseItemBehavior DISPENSE_FLUID = new DefaultDispenseItemBehavior() {
		@Override
		protected ItemStack execute(BlockSource source, ItemStack stack) {
			DispensibleContainerItem container = (DispensibleContainerItem) stack.getItem();
			BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
			Level level = source.level();
			if (container.emptyContents(null, level, pos, null, stack)) return new ItemStack(Items.BUCKET);
			return DEFAULT.dispense(source, stack);
		}
	};

	private static void registerFluidDispenseBehavior(BucketItem bucket) {
		DispenserBlock.registerBehavior(bucket, DISPENSE_FLUID);
	}

	public static abstract class TintedFluidType extends FluidType {
		public TintedFluidType(Properties properties, Identifier stillTexture, Identifier flowingTexture) {
			super(properties);
		}

		@Override
		public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
			consumer.accept(new IClientFluidTypeExtensions() {
				@Override public void modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, org.joml.Vector4f fluidFogColor) {
					Vector3f customFogColor = TintedFluidType.this.getCustomFogColor();
					if (customFogColor != null)
						fluidFogColor.set(customFogColor.x(), customFogColor.y(), customFogColor.z(), 1f);
				}

				@Override public void modifyFogRender(Camera camera, FogEnvironment environment, float renderDistance, float partialTick, FogData fogData) {
					float modifier = TintedFluidType.this.getFogDistanceModifier();
					if (modifier != 1f) {
						fogData.environmentalStart = -8f;
						fogData.environmentalEnd = 96.0f * modifier;
					}
				}
			});
		}

		protected Vector3f getCustomFogColor() { return null; }
		protected float getFogDistanceModifier() { return 1f; }
	}

	private static class SolidRenderedPlaceableFluidType extends TintedFluidType {
		private Vector3f fogColor;
		private Supplier<Float> fogDistance;

		public static FluidTypeFactory create(int fogColor, Supplier<Float> fogDistance) {
			return (p, s, f) -> {
				SolidRenderedPlaceableFluidType fluidType = new SolidRenderedPlaceableFluidType(p, s, f);
				fluidType.fogColor = new Color(fogColor, false).asVectorF();
				fluidType.fogDistance = fogDistance;
				return fluidType;
			};
		}

		private SolidRenderedPlaceableFluidType(Properties properties, Identifier stillTexture, Identifier flowingTexture) {
			super(properties, stillTexture, flowingTexture);
		}

		@Override protected Vector3f getCustomFogColor() { return fogColor; }
		@Override protected float getFogDistanceModifier() { return fogDistance.get(); }
	}
}
