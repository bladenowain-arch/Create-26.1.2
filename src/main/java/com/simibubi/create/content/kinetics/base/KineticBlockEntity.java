package com.simibubi.create.content.kinetics.base;

import static net.minecraft.ChatFormatting.GOLD;
import static net.minecraft.ChatFormatting.GRAY;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.Create;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.compat.computercraft.events.KineticsChangeEvent;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.IRotate.SpeedLevel;
import com.simibubi.create.content.kinetics.base.IRotate.StressImpact;
import com.simibubi.create.content.kinetics.gearbox.GearboxBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity.SequenceContext;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.sound.SoundScapes;
import com.simibubi.create.foundation.sound.SoundScapes.AmbienceGroup;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;

import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import net.createmod.catnip.lang.FontHelper.Palette;
import net.createmod.catnip.nbt.NBTHelper;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class KineticBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, IHaveHoveringInformation {

	public @Nullable Long network;
	public @Nullable BlockPos source;
	public boolean networkDirty;
	public boolean updateSpeed;
	public int preventSpeedUpdate;

	protected KineticEffectHandler effects;
	protected float speed;
	protected float capacity;
	protected float stress;
	protected boolean overStressed;
	protected boolean wasMoved;

	private int flickerTally;
	private int networkSize;
	private int validationCountdown;
	protected float lastStressApplied;
	protected float lastCapacityProvided;

	public SequenceContext sequenceContext;

	public KineticBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
		effects = new KineticEffectHandler(this);
		updateSpeed = true;
	}

	@Override
	public void initialize() {
		if (hasNetwork() && !level.isClientSide) {
			KineticNetwork network = getOrCreateNetwork();
			if (!network.initialized)
				network.initFromTE(capacity, stress, networkSize);
			network.addSilently(this, lastCapacityProvided, lastStressApplied);
		}

		super.initialize();
	}

	@Override
	public void tick() {
		if (!level.isClientSide && needsSpeedUpdate())
			attachKinetics();

		super.tick();
		effects.tick();

		preventSpeedUpdate = 0;

		if (level.isClientSide) {
			CatnipServices.PLATFORM.executeOnClientOnly(() -> () -> this.tickAudio());
			return;
		}

		if (validationCountdown-- <= 0) {
			validationCountdown = AllConfigs.server().kinetics.kineticValidationFrequency.get();
			validateKinetics();
		}

		if (getFlickerScore() > 0)
			flickerTally = getFlickerScore() - 1;

		if (networkDirty) {
			if (hasNetwork())
				getOrCreateNetwork().updateNetwork();
			networkDirty = false;
		}
	}

	private void validateKinetics() {
		if (hasSource()) {
			if (!hasNetwork()) {
				removeSource();
				return;
			}

			if (!level.isLoaded(source))
				return;

			BlockEntity blockEntity = level.getBlockEntity(source);
			KineticBlockEntity sourceBE = blockEntity instanceof KineticBlockEntity ? (KineticBlockEntity) blockEntity : null;
			if (sourceBE == null || sourceBE.speed == 0) {
				removeSource();
				detachKinetics();
				return;
			}

			return;
		}

		if (speed != 0 && getGeneratedSpeed() == 0)
			speed = 0;
	}

	public void updateFromNetwork(float maxStress, float currentStress, int networkSize) {
		networkDirty = false;
		this.capacity = maxStress;
		this.stress = currentStress;
		this.networkSize = networkSize;
		boolean overStressed = maxStress < currentStress && StressImpact.isEnabled();
		setChanged();

		if (overStressed != this.overStressed) {
			float prevSpeed = getSpeed();
			this.overStressed = overStressed;
			onSpeedChanged(prevSpeed);
			sendData();
		}
	}

	protected KineticsChangeEvent makeComputerKineticsChangeEvent() {
		return new KineticsChangeEvent(speed, capacity, stress, overStressed);
	}

	protected Block getStressConfigKey() {
		return getBlockState().getBlock();
	}

	public float calculateStressApplied() {
		float impact = (float) BlockStressValues.getImpact(getStressConfigKey());
		this.lastStressApplied = impact;
		return impact;
	}

	public float calculateAddedStressCapacity() {
		float capacity = (float) BlockStressValues.getCapacity(getStressConfigKey());
		this.lastCapacityProvided = capacity;
		return capacity;
	}

	public void onSpeedChanged(float previousSpeed) {
		boolean fromOrToZero = (previousSpeed == 0) != (getSpeed() == 0);
		boolean directionSwap = !fromOrToZero && Math.signum(previousSpeed) != Math.signum(getSpeed());
		if (fromOrToZero || directionSwap)
			flickerTally = getFlickerScore() + 5;
		setChanged();
	}

	@Override
	public void remove() {
		if (!level.isClientSide) {
			if (hasNetwork())
				getOrCreateNetwork().remove(this);
			detachKinetics();
		}
		super.remove();
	}

	@Override
	protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		compound.putFloat("Speed", speed);
		if (sequenceContext != null && (!clientPacket || syncSequenceContext()))
			compound.put("Sequence", sequenceContext.serializeNBT());
		if (needsSpeedUpdate())
			compound.putBoolean("NeedsSpeedUpdate", true);
		if (hasSource())
			compound.put("Source", NbtUtils.writeBlockPos(source));
		if (hasNetwork()) {
			CompoundTag networkTag = new CompoundTag();
			networkTag.putLong("Id", this.network);
			networkTag.putFloat("Stress", stress);
			networkTag.putFloat("Capacity", capacity);
			networkTag.putInt("Size", networkSize);
			if (lastStressApplied != 0)
				networkTag.putFloat("AddedStress", lastStressApplied);
			if (lastCapacityProvided != 0)
				networkTag.putFloat("AddedCapacity", lastCapacityProvided);
			compound.put("Network", networkTag);
		}
		super.write(compound, registries, clientPacket);
	}

	public boolean needsSpeedUpdate() {
		return updateSpeed;
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		boolean overStressedBefore = overStressed;
		clearKineticInformation();
		if (wasMoved) {
			super.read(compound, registries, clientPacket);
			return;
		}
		speed = compound.getFloatOr("Speed", 0);
		sequenceContext = SequenceContext.fromNBT(compound.getCompoundOrEmpty("Sequence"));
		source = null;
		if (compound.contains("Source"))
			source = NBTHelper.readBlockPos(compound, "Source");
		if (compound.contains("Network")) {
			CompoundTag networkTag = compound.getCompoundOrEmpty("Network");
			network = networkTag.getLongOr("Id", 0);
			stress = networkTag.getFloatOr("Stress", 0);
			capacity = networkTag.getFloatOr("Capacity", 0);
			networkSize = networkTag.getIntOr("Size", 0);
			lastStressApplied = networkTag.getFloatOr("AddedStress", 0);
			lastCapacityProvided = networkTag.getFloatOr("AddedCapacity", 0);
			overStressed = capacity < stress && StressImpact.isEnabled();
		}
		super.read(compound, registries, clientPacket);
		if (clientPacket && overStressedBefore != overStressed && speed != 0)
			effects.triggerOverStressedEffect();
		if (clientPacket)
			CatnipServices.PLATFORM.executeOnClientOnly(() -> () -> VisualizationHelper.queueUpdate(this));
	}
}
