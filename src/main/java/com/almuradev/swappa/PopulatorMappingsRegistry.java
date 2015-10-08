/**
 * This file is part of Swappa, licensed under the MIT License (MIT).
 *
 * Copyright (c) AlmuraDev <http://github.com/AlmuraDev>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.almuradev.swappa;

import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.world.gen.PopulatorType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class PopulatorMappingsRegistry {
    private static final Logger logger = LoggerFactory.getLogger(Swappa.PLUGIN_ID + "-populators");
    private static final Map<PluginContainer, Map<PopulatorType, Set<ReplacementEntry>>> REGISTRY_POPULATORS = new HashMap<>();
    private static final Set<ReplacementEntry> REGISTRY_ALL = new HashSet<>();

    private static final String SECTION_ALL = "all", SECTION_POPULATORS = "populators";
    private static final PluginContainer PLUGIN_MINECRAFT = Swappa.instance.pluginManager.getPlugin("minecraft").get();

    public static Optional<ReplacementEntry> getEntry(PopulatorType type, BlockState populatedBlockState) {
        for (Map.Entry<PluginContainer, Map<PopulatorType, Set<ReplacementEntry>>> pluginContainerEntry : REGISTRY_POPULATORS.entrySet()) {
            for (Map.Entry<PopulatorType, Set<ReplacementEntry>> populatorTypeEntry : pluginContainerEntry.getValue().entrySet()) {
                if (populatorTypeEntry.getKey().equals(type)) {
                    final Set<ReplacementEntry> replacementEntries = populatorTypeEntry.getValue();
                    if (replacementEntries != null) {
                        for (ReplacementEntry replacementEntry : replacementEntries) {
                            final BlockState originalReplacementEntry = replacementEntry.originalBlockState;

                            if (originalReplacementEntry.getType().equals(populatedBlockState.getType())) {
                                return Optional.of(replacementEntry);
                            }
                        }
                    }

                    return Optional.empty();
                }
            }
        }

        return Optional.empty();
    }

    public static Optional<ReplacementEntry> getEntry(BlockState populatedBlockState) {
        for (ReplacementEntry replacementEntry : REGISTRY_ALL) {
            if (replacementEntry.originalBlockState.getType().equals(populatedBlockState.getType())) {
                return Optional.of(replacementEntry);
            }
        }

        return Optional.empty();
    }

    public static void load() {
        REGISTRY_ALL.clear();
        REGISTRY_POPULATORS.clear();

        for (Map.Entry<Object, ? extends ConfigurationNode> blockStateMappingEntry : Swappa.instance.populatorsRootNode.getNode
                (PopulatorMappingsRegistry.SECTION_ALL).getChildrenMap().entrySet()) {
            final String originalBlockStateMapping = (String) blockStateMappingEntry.getKey();
            final Optional<BlockState> optOriginalBlockState = lookupBlockState(originalBlockStateMapping);
            if (!optOriginalBlockState.isPresent()) {
                logger.warn("Original All BlockState [" + originalBlockStateMapping + "] is not a registered BlockState nor a mapping!");
                continue;
            }
            final String replacementBlockStateMapping = blockStateMappingEntry.getValue().getString("");
            final Optional<BlockState> optReplacementBlockState = lookupBlockState(replacementBlockStateMapping);
            if (!optReplacementBlockState.isPresent()) {
                logger.warn("Replacement BlockState [" + replacementBlockStateMapping + "] for [All] is not a registered BlockState nor a mapping!");
                continue;
            }
            REGISTRY_ALL.add(new ReplacementEntry(optOriginalBlockState.get(), optReplacementBlockState.get()));
        }

        for (Map.Entry<Object, ? extends ConfigurationNode> modEntry : Swappa.instance.populatorsRootNode.getNode(
                PopulatorMappingsRegistry.SECTION_POPULATORS)
                .getChildrenMap().entrySet()) {
            final String modId = (String) modEntry.getKey();
            final Optional<PluginContainer> optPluginContainer = Swappa.instance.pluginManager.getPlugin(modId);
            if (!optPluginContainer.isPresent()) {
                logger.warn("Potential Mod [" + modId + "] is not loaded (did you forget it in /mods ?)");
                continue;
            }

            for (Map.Entry<Object, ? extends ConfigurationNode> populatorTypeEntry : modEntry.getValue().getChildrenMap().entrySet()) {
                final String populatorTypeId = (String) populatorTypeEntry.getKey();
                final Optional<PopulatorType> optPopulatorType = Swappa.instance.registry.getType(PopulatorType.class, modId + ":" + populatorTypeId);
                if (!optPopulatorType.isPresent()) {
                    logger.warn("Potential Populator [" + populatorTypeId + "] for Mod [" + modId + "] is not a valid Populator!");
                    continue;
                }

                final Set<ReplacementEntry> populatorReplacementEntries = new HashSet<>();
                for (Map.Entry<Object, ? extends ConfigurationNode> blockStateMappingEntry : populatorTypeEntry.getValue().getChildrenMap()
                        .entrySet()) {
                    final String originalBlockStateMapping = (String) blockStateMappingEntry.getKey();
                    final Optional<BlockState> optOriginalBlockState = lookupBlockState(originalBlockStateMapping);
                    if (!optOriginalBlockState.isPresent()) {
                        logger.warn("Original Populator BlockState [" + originalBlockStateMapping + "] for Populator [" + optPopulatorType.get()
                                .getId() + "] in Mod [" + optPluginContainer.get().getId() + "] is not a registered BlockState nor a mapping!");
                        continue;
                    }
                    final String replacementBlockStateMapping = blockStateMappingEntry.getValue().getString("");
                    final Optional<BlockState> optReplacementBlockState = lookupBlockState(replacementBlockStateMapping);
                    if (!optReplacementBlockState.isPresent()) {
                        logger.warn("Replacement BlockState [" + originalBlockStateMapping + "] for Populator [" + optPopulatorType.get()
                                .getId() + "] in Mod [" + optPluginContainer.get().getId() + "] is not a registered BlockState nor a mapping!");
                        continue;
                    }

                    populatorReplacementEntries.add(new ReplacementEntry(optOriginalBlockState.get(), optReplacementBlockState.get()));
                }

                if (!populatorReplacementEntries.isEmpty()) {
                    Map<PopulatorType, Set<ReplacementEntry>> storedPopulatorReplacementEntries = REGISTRY_POPULATORS.get(optPluginContainer.get());
                    if (storedPopulatorReplacementEntries == null) {
                        storedPopulatorReplacementEntries = new HashMap<>();
                        REGISTRY_POPULATORS.put(optPluginContainer.get(), storedPopulatorReplacementEntries);
                    }

                    storedPopulatorReplacementEntries.put(optPopulatorType.get(), populatorReplacementEntries);
                }
            }
        }
    }

    static Optional<BlockState> lookupBlockState(String rawId) {
        Optional<BlockState> optBlockState;

        // Lookup from registry first
        Optional<BlockType> optBlockType = Swappa.instance.registry.getType(BlockType.class, rawId);
        if (!optBlockType.isPresent()) {
            final String[] split = rawId.split(":");

            PluginContainer container;
            String id;
            if (split.length > 1) {
                container = Swappa.instance.pluginManager.getPlugin(split[0]).orElse(null);
                if (container == null) {
                    return Optional.empty();
                }
                id = split[1];
            } else {
                container = PLUGIN_MINECRAFT;
                id = split[0];
            }

            optBlockState = BlockStateMappingsRegistry.get(container, id);
        } else {
            optBlockState = Optional.ofNullable(optBlockType.get().getDefaultState());
        }

        return optBlockState;
    }

    public static class ReplacementEntry {
        public final BlockState originalBlockState, replacementBlockState;

        public ReplacementEntry(BlockState originalBlockState, BlockState replacementBlockState) {
            this.originalBlockState = originalBlockState;
            this.replacementBlockState = replacementBlockState;
        }
    }
}
