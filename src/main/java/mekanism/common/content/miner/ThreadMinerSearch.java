package mekanism.common.content.miner;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import java.util.BitSet;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.math.MathUtils;
import mekanism.api.text.IHasTextComponent;
import mekanism.api.text.ILangEntry;
import mekanism.common.MekanismLang;
import mekanism.common.tags.MekanismTags;
import mekanism.common.tile.TileEntityBoundingBlock;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class ThreadMinerSearch extends Thread {

    private final TileEntityDigitalMiner tile;
    private final Long2ObjectMap<BitSet> oresToMine = new Long2ObjectOpenHashMap<>();
    private PathNavigationRegion chunkCache;
    public State state = State.IDLE;
    public int found = 0;

    public ThreadMinerSearch(TileEntityDigitalMiner tile) {
        this.tile = tile;
    }

    public void setChunkCache(PathNavigationRegion cache) {
        this.chunkCache = cache;
    }

    @Override
    public void run() {
        state = State.SEARCHING;
        if (!tile.getInverse() && !tile.getFilterManager().hasEnabledFilters()) {
            state = State.FINISHED;
            return;
        }
        Object2BooleanMap<Block> acceptedItems = new Object2BooleanOpenHashMap<>();
        BlockPos pos = tile.getStartingPos();
        int diameter = tile.getDiameter();
        int size = tile.getTotalSize();
        Block info;
        BlockPos minerPos = tile.getBlockPos();
        for (int i = 0; i < size; i++) {
            if (tile.isRemoved()) {
                //Make sure the miner is still valid and something hasn't gone wrong
                return;
            }
            BlockPos testPos = TileEntityDigitalMiner.getOffsetForIndex(pos, diameter, i);
            if (minerPos.equals(testPos) || WorldUtils.getTileEntity(TileEntityBoundingBlock.class, chunkCache, testPos) != null) {
                //Skip the miner itself, and also skip any bounding blocks
                continue;
            }
            BlockState state = chunkCache.getBlockState(testPos);
            if (state.isAir() || state.is(MekanismTags.Blocks.MINER_BLACKLIST) || state.getDestroySpeed(chunkCache, testPos) < 0) {
                //Skip air, blacklisted blocks, and unbreakable blocks
                continue;
            }
            info = state.getBlock();
            if (MekanismUtils.isLiquidBlock(info)) {//Skip liquids
                continue;
            }
            boolean accepted = acceptedItems.computeIfAbsent(info, (Block block) -> {
                if (tile.isReplaceTarget(block.asItem())) {
                    //If it is a replace target just mark it as never being accepted
                    return false;
                }
                //Ensure that the inverse mode is the opposite of the filter match
                return tile.getInverse() != tile.getFilterManager().anyEnabledMatch(filter -> filter.canFilter(state));
            });
            if (accepted) {
                long chunk = ChunkPos.asLong(testPos);
                oresToMine.computeIfAbsent(chunk, k -> new BitSet()).set(i);
                found++;
            }
        }

        state = State.FINISHED;
        chunkCache = null;
        if (tile.searcher == this) {
            //Only update search if we are still valid and didn't get replaced due to a reset call
            tile.updateFromSearch(oresToMine, found);
        }
    }

    @NothingNullByDefault
    public enum State implements IHasTextComponent {
        IDLE(MekanismLang.MINER_IDLE),
        SEARCHING(MekanismLang.MINER_SEARCHING),
        PAUSED(MekanismLang.MINER_PAUSED),
        FINISHED(MekanismLang.MINER_READY);

        private static final State[] MODES = values();

        private final ILangEntry langEntry;

        State(ILangEntry langEntry) {
            this.langEntry = langEntry;
        }

        @Override
        public Component getTextComponent() {
            return langEntry.translate();
        }

        public static State byIndexStatic(int index) {
            return MathUtils.getByIndexMod(MODES, index);
        }
    }
}