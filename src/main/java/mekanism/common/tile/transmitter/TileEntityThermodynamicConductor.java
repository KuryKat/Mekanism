package mekanism.common.tile.transmitter;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import mekanism.api.NBTConstants;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.heat.IHeatHandler;
import mekanism.api.math.FloatingLong;
import mekanism.api.providers.IBlockProvider;
import mekanism.api.tier.AlloyTier;
import mekanism.api.tier.BaseTier;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.ColorRGBA;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.states.BlockStateHelper;
import mekanism.common.block.states.TransmitterType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.capabilities.heat.ITileHeatHandler;
import mekanism.common.capabilities.proxy.ProxyHeatHandler;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tier.ConductorTier;
import mekanism.common.transmitters.grid.HeatNetwork;
import mekanism.common.upgrade.transmitter.ThermodynamicConductorUpgradeData;
import mekanism.common.upgrade.transmitter.TransmitterUpgradeData;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.NBTUtils;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

public class TileEntityThermodynamicConductor extends TileEntityTransmitter<IHeatHandler, HeatNetwork, Void> implements ITileHeatHandler {

    public final ConductorTier tier;

    public FloatingLong clientTemperature = FloatingLong.ZERO;

    private ProxyHeatHandler readOnlyHandler;
    private final Map<Direction, ProxyHeatHandler> heatHandlers;
    private final List<IHeatCapacitor> capacitors;
    public BasicHeatCapacitor buffer;

    public TileEntityThermodynamicConductor(IBlockProvider blockProvider) {
        super(blockProvider);
        this.tier = Attribute.getTier(blockProvider.getBlock(), ConductorTier.class);
        heatHandlers = new EnumMap<>(Direction.class);
        buffer = new BasicHeatCapacitor(tier.getHeatCapacity(), tier.getInverseConduction(), tier.getInverseConductionInsulation(), true, true, this);
        capacitors = Collections.singletonList(buffer);
    }

    private IHeatHandler getHeatHandler(@Nullable Direction side) {
        if (side == null) {
            if (readOnlyHandler == null) {
                readOnlyHandler = new ProxyHeatHandler(this, null, null);
            }
            return readOnlyHandler;
        }
        ProxyHeatHandler heatHandler = heatHandlers.get(side);
        if (heatHandler == null) {
            heatHandlers.put(side, heatHandler = new ProxyHeatHandler(this, side, null));
        }
        return heatHandler;
    }

    @Override
    public HeatNetwork createNewNetwork() {
        return new HeatNetwork();
    }

    @Override
    public HeatNetwork createNewNetworkWithID(UUID networkID) {
        return new HeatNetwork(networkID);
    }

    @Override
    public HeatNetwork createNetworkByMerging(Collection<HeatNetwork> networks) {
        return new HeatNetwork(networks);
    }

    @Override
    public int getCapacity() {
        return 0;
    }

    @Override
    public Void getBuffer() {
        return null;
    }

    @Override
    public void takeShare() {
    }

    @Override
    public TransmitterType getTransmitterType() {
        return TransmitterType.THERMODYNAMIC_CONDUCTOR;
    }

    @Override
    public boolean isValidAcceptor(TileEntity tile, Direction side) {
        return CapabilityUtils.getCapability(tile, Capabilities.HEAT_HANDLER_CAPABILITY, side.getOpposite()).isPresent();
    }

    @Override
    public TransmissionType getTransmissionType() {
        return TransmissionType.HEAT;
    }

    @Override
    public IHeatHandler getCachedAcceptor(Direction side) {
        return MekanismUtils.toOptional(CapabilityUtils.getCapability(getCachedTile(side), Capabilities.HEAT_HANDLER_CAPABILITY, side.getOpposite())).orElse(null);
    }

    @Nonnull
    @Override
    public CompoundNBT getReducedUpdateTag() {
        CompoundNBT updateTag = super.getReducedUpdateTag();
        updateTag.putString(NBTConstants.TEMPERATURE, buffer.getHeat().toString());
        return updateTag;
    }

    @Override
    public void handleUpdateTag(@Nonnull CompoundNBT tag) {
        super.handleUpdateTag(tag);
        NBTUtils.setFloatingLongIfPresent(tag, NBTConstants.TEMPERATURE, heat -> buffer.setHeat(heat));
    }

    public ColorRGBA getBaseColor() {
        return tier.getBaseColor();
    }

    @Override
    public List<IHeatCapacitor> getHeatCapacitors(Direction side) {
        return capacitors;
    }

    @Override
    public void onContentsChanged() {
        if (Math.abs(buffer.getTemperature().subtract(clientTemperature).doubleValue()) > buffer.getTemperature().divide(20).doubleValue()) {
            clientTemperature = buffer.getTemperature();
            sendUpdatePacket();
        }
        markDirty(false);
    }

    @Nullable
    @Override
    public IHeatHandler getAdjacent(Direction side) {
        if (connectionMapContainsSide(getAllCurrentConnections(), side)) {
            TileEntity adj = MekanismUtils.getTileEntity(getWorld(), getPos().offset(side));
            Optional<IHeatHandler> capability = MekanismUtils.toOptional(CapabilityUtils.getCapability(adj, Capabilities.HEAT_HANDLER_CAPABILITY, side.getOpposite()));
            if (capability.isPresent()) {
                return capability.get();
            }
        }
        return null;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (capability == Capabilities.HEAT_HANDLER_CAPABILITY) {
            List<IHeatCapacitor> heatCapacitors = getHeatCapacitors(side);
            //Don't return a heat handler if we don't actually even have any capacitors for that side
            LazyOptional<IHeatHandler> lazyHeatHandler = heatCapacitors.isEmpty() ? LazyOptional.empty() : LazyOptional.of(() -> getHeatHandler(side));
            return Capabilities.HEAT_HANDLER_CAPABILITY.orEmpty(capability, lazyHeatHandler);
        }
        return super.getCapability(capability, side);
    }

    @Override
    protected boolean canUpgrade(AlloyTier alloyTier) {
        return alloyTier.getBaseTier().ordinal() == tier.getBaseTier().ordinal() + 1;
    }

    @Nonnull
    @Override
    protected BlockState upgradeResult(@Nonnull BlockState current, @Nonnull BaseTier tier) {
        switch (tier) {
            case BASIC:
                return BlockStateHelper.copyStateData(current, MekanismBlocks.BASIC_THERMODYNAMIC_CONDUCTOR.getBlock().getDefaultState());
            case ADVANCED:
                return BlockStateHelper.copyStateData(current, MekanismBlocks.ADVANCED_THERMODYNAMIC_CONDUCTOR.getBlock().getDefaultState());
            case ELITE:
                return BlockStateHelper.copyStateData(current, MekanismBlocks.ELITE_THERMODYNAMIC_CONDUCTOR.getBlock().getDefaultState());
            case ULTIMATE:
                return BlockStateHelper.copyStateData(current, MekanismBlocks.ULTIMATE_THERMODYNAMIC_CONDUCTOR.getBlock().getDefaultState());
        }
        return current;
    }

    @Nullable
    @Override
    protected ThermodynamicConductorUpgradeData getUpgradeData() {
        return new ThermodynamicConductorUpgradeData(redstoneReactive, connectionTypes, buffer.getHeat());
    }

    @Override
    protected void parseUpgradeData(@Nonnull TransmitterUpgradeData upgradeData) {
        if (upgradeData instanceof ThermodynamicConductorUpgradeData) {
            ThermodynamicConductorUpgradeData data = (ThermodynamicConductorUpgradeData) upgradeData;
            redstoneReactive = data.redstoneReactive;
            connectionTypes = data.connectionTypes;
            buffer.setHeat(data.heat);
        } else {
            super.parseUpgradeData(upgradeData);
        }
    }
}