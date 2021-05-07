/*
 * Requiem
 * Copyright (C) 2017-2021 Ladysnake
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses>.
 *
 * Linking this mod statically or dynamically with other
 * modules is making a combined work based on this mod.
 * Thus, the terms and conditions of the GNU General Public License cover the whole combination.
 *
 * In addition, as a special exception, the copyright holders of
 * this mod give you permission to combine this mod
 * with free software programs or libraries that are released under the GNU LGPL
 * and with code included in the standard release of Minecraft under All Rights Reserved (or
 * modified versions of such code, with unchanged license).
 * You may copy and distribute such a system following the terms of the GNU GPL for this mod
 * and the licenses of the other code concerned.
 *
 * Note that people who make modified versions of this mod are not obligated to grant
 * this special exception for their modified versions; it is their choice whether to do so.
 * The GNU General Public License gives permission to release a modified version without this exception;
 * this exception also makes it possible to release a modified version which carries forward this exception.
 */
package ladysnake.requiem.common.block;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import ladysnake.requiem.common.tag.RequiemBlockTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.StairShape;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Tickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class RunicObsidianBlockEntity extends BlockEntity implements Tickable {
    public static final Direction[] OBELISK_SIDES = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};
    private @Nullable BlockPos delegating = null;
    private final Object2IntMap<StatusEffect> levels = new Object2IntOpenHashMap<>();
    private int obeliskWidth = 0;
    private int obeliskHeight = 0;

    public RunicObsidianBlockEntity() {
        super(RequiemBlockEntities.RUNIC_OBSIDIAN);
    }

    @Override
    public void tick() {
        assert this.world != null;
        if (this.world.isClient) return;

        if (this.world.getTime() % 80L == 0L) {
            this.refresh();

            if (!this.levels.isEmpty()) {
                this.applyPlayerEffects();
            }
        }
    }

    private void applyPlayerEffects() {
        assert this.world != null;
        double range = this.obeliskWidth * 10 + 10;
        int effectDuration = (9 + this.obeliskWidth * 2) * 20;
        Box box = (new Box(this.pos)).expand(range).stretch(0.0D, this.world.getHeight(), 0.0D);
        List<PlayerEntity> players = this.world.getNonSpectatingEntities(PlayerEntity.class, box);
        for (PlayerEntity playerEntity : players) {
            for (Object2IntMap.Entry<StatusEffect> effect : this.levels.object2IntEntrySet()) {
                playerEntity.addStatusEffect(new StatusEffectInstance(effect.getKey(), effectDuration, effect.getIntValue() - 1, true, true));
            }
        }
    }

    private RunicObsidianBlockEntity getObeliskCore() {
        if (this.world != null && this.delegating != null) {
            BlockEntity blockEntity = this.world.getBlockEntity(this.delegating);
            if (blockEntity instanceof RunicObsidianBlockEntity) {
                return (RunicObsidianBlockEntity) blockEntity;
            }
        }
        return this;
    }

    public int getRangeLevel() {
        return this.getObeliskCore().obeliskWidth;
    }

    public int getPowerLevel() {
        return this.getObeliskCore().obeliskHeight;
    }

    private void refresh() {
        assert this.world != null;
        this.delegating = null;
        this.levels.clear();
        this.obeliskWidth = 0;
        this.obeliskHeight = 0;
        BlockEntity be = this.world.getBlockEntity(this.pos.down());

        if (be instanceof RunicObsidianBlockEntity) {
            this.delegating = ((RunicObsidianBlockEntity) be).delegating != null ? ((RunicObsidianBlockEntity) be).delegating : this.pos.down();
        } else if (this.world.getBlockState(this.pos.down()).isIn(RequiemBlockTags.OBELISK_FRAME)) {
            be = this.world.getBlockEntity(this.pos.west());
            if (be instanceof RunicObsidianBlockEntity) {
                this.delegating = ((RunicObsidianBlockEntity) be).delegating != null ? ((RunicObsidianBlockEntity) be).delegating : this.pos.west();
            } else {
                be = this.world.getBlockEntity(this.pos.north());
                if (be instanceof RunicObsidianBlockEntity) {
                    this.delegating = ((RunicObsidianBlockEntity) be).delegating != null ? ((RunicObsidianBlockEntity) be).delegating : this.pos.north();
                } else {
                    this.matchObelisk(this.world, this.pos);
                }
            }
        }
    }

    private void matchObelisk(BlockView world, BlockPos origin) {
        int tentativeWidth = matchObeliskBase(world, origin);
        if (tentativeWidth > 0) {
            Object2IntMap<StatusEffect> levels = new Object2IntOpenHashMap<>();
            int tentativeHeight = matchObeliskCore(world, origin, tentativeWidth, levels);
            if (tentativeHeight > 0 && matchObeliskCap(world, origin, tentativeWidth, tentativeHeight)) {
                this.obeliskWidth = tentativeWidth;
                this.obeliskHeight = tentativeHeight;
                this.levels.putAll(levels);
            }
        }
    }

    private static int matchObeliskCore(BlockView world, BlockPos origin, int width, Object2IntMap<StatusEffect> levels) {
        // start at north-west corner
        BlockPos start = origin.add(-1, 0, -1);
        BlockPos.Mutable pos = start.mutableCopy();
        int height = 0;

        while (true) {
            if (!world.getBlockState(pos.set(start.getX(), start.getY() + height, start.getZ())).isIn(RequiemBlockTags.OBELISK_FRAME)
                || !world.getBlockState(pos.set(start.getX() + width + 1, start.getY() + height, start.getZ())).isIn(RequiemBlockTags.OBELISK_FRAME)
                || !world.getBlockState(pos.set(start.getX() + width + 1, start.getY() + height, start.getZ() + width + 1)).isIn(RequiemBlockTags.OBELISK_FRAME)
                || !world.getBlockState(pos.set(start.getX(), start.getY() + height, start.getZ() + width + 1)).isIn(RequiemBlockTags.OBELISK_FRAME)
            ) {
                return height;
            }
            StatusEffect eff = getCoreStatusEffect(world, origin, width, height);
            if (eff != null) {
                levels.mergeInt(eff, 1, Integer::sum);
            }
            height++;
        }
    }

    private static @Nullable StatusEffect getCoreStatusEffect(BlockView world, BlockPos origin, int width, int height) {
        StatusEffect eff = null;
        for (BlockPos corePos : iterateCoreBlocks(origin, width, height)) {
            Block block = world.getBlockState(corePos).getBlock();
            if (block instanceof RunicObsidianBlock) {
                if (eff == null) {
                    eff = ((RunicObsidianBlock) block).getEffect();
                } else if (((RunicObsidianBlock) block).getEffect() != eff) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return eff;
    }

    private static int matchObeliskBase(BlockView world, BlockPos origin) {
        return checkObeliskExtremity(world, origin.add(-1, -1, -1), state -> state.isIn(RequiemBlockTags.OBELISK_FRAME));
    }

    private static boolean matchObeliskCap(BlockView world, BlockPos origin, int width, int height) {
        if (checkObeliskExtremity(world, origin.add(-1, height, -1), state -> {
            if (state.isOf(RequiemBlocks.POLISHED_OBSIDIAN_STAIRS)) {
                StairShape stairShape = state.get(StairsBlock.SHAPE);
                return stairShape == StairShape.OUTER_LEFT || stairShape == StairShape.OUTER_RIGHT;
            }
            return false;
        }) != width) {
            return false;
        }
        for (BlockPos blockPos : iterateCoreBlocks(origin, width, height + 1)) {
            if (!world.getBlockState(blockPos).isOf(RequiemBlocks.POLISHED_OBSIDIAN_SLAB)) {
                return false;
            }
        }
        return true;
    }

    @NotNull
    private static Iterable<BlockPos> iterateCoreBlocks(BlockPos origin, int width, int height) {
        return BlockPos.iterate(origin.up(height), origin.add(width - 1, height, width - 1));
    }

    private static int checkObeliskExtremity(BlockView world, BlockPos origin, Predicate<BlockState> corner) {
        // start at bottom north-west corner
        BlockPos.Mutable pos = origin.mutableCopy();
        int lastSideLength = -1;

        for (Direction direction : OBELISK_SIDES) {
            int sideLength = 0;
            while (true) {
                BlockState state = world.getBlockState(pos);
                if (corner.test(state)) {
                    // Corner block can be start or end of side
                    if (sideLength > 0) {
                        break;
                    }
                } else if (state.isOf(RequiemBlocks.POLISHED_OBSIDIAN_STAIRS)) {
                    sideLength++;
                    if (sideLength > 5) {
                        // Too large: not a valid base
                        return 0;
                    }
                } else {
                    // Unexpected block: not a valid base
                    return 0;
                }
                pos.move(direction);
            }
            if (lastSideLength < 0) {
                lastSideLength = sideLength;
            } else if (lastSideLength != sideLength) {
                // Not a square base: not a valid base
                return 0;
            }
        }

        return lastSideLength;
    }
}
