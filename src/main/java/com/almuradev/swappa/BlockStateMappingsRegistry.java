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

import com.google.common.collect.ImmutableMap;
import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.trait.BlockTrait;
import org.spongepowered.api.plugin.PluginContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class BlockStateMappingsRegistry {
    private static final Logger logger = LoggerFactory.getLogger(Swappa.PLUGIN_ID + "-blockstates");
    private static final Map<PluginContainer, Map<String, BlockState>> REGISTRY = new HashMap<>();
    private static final String SECTION_MAPPINGS = "mappings";

    public static Optional<BlockState> put(PluginContainer container, String mappedName, BlockState state) {
        Map<String, BlockState> mappedContainerBlockStates = REGISTRY.get(container);
        if (mappedContainerBlockStates == null) {
            mappedContainerBlockStates = new HashMap<>();
            REGISTRY.put(container, mappedContainerBlockStates);
        }

        return Optional.ofNullable(mappedContainerBlockStates.put(mappedName, state));
    }

    public static Optional<BlockState> get(PluginContainer container, String mappedName) {
        final Map<String, BlockState> mappedContainerBlockStates = REGISTRY.get(container);

        if (mappedContainerBlockStates != null) {
            return Optional.ofNullable(mappedContainerBlockStates.get(mappedName));
        }

        return Optional.empty();
    }

    public static Map<PluginContainer, Map<String, BlockState>> getAll() {
        final ImmutableMap.Builder<PluginContainer, Map<String, BlockState>> builder = ImmutableMap.builder();
        for (Map.Entry<PluginContainer, Map<String, BlockState>> entry : REGISTRY.entrySet()) {
            final ImmutableMap<String, BlockState> containerBuilder = ImmutableMap.copyOf(entry.getValue());
            builder.put(entry.getKey(), containerBuilder);
        }

        return builder.build();
    }

    public static void load() {
        REGISTRY.clear();

        for (Map.Entry<Object, ? extends ConfigurationNode> modEntry : Swappa.instance.blockStatesRootNode.getNode(BlockStateMappingsRegistry.SECTION_MAPPINGS)
                .getChildrenMap().entrySet()) {
            final String modId = (String) modEntry.getKey();
            final Optional<PluginContainer> optPluginContainer = Swappa.instance.pluginManager.getPlugin(modId);
            if (!optPluginContainer.isPresent()) {
                BlockStateMappingsRegistry.logger.warn("Potential Mod [" + modId + "] is not loaded (did you forget it in /mods ?)");
                continue;
            }

            for (Map.Entry<Object, ? extends ConfigurationNode> blockTypeEntry : modEntry.getValue().getChildrenMap().entrySet()) {
                final String blockTypeId = (String) blockTypeEntry.getKey();
                final Optional<BlockType> optBlockType = Swappa.instance.registry.getType(BlockType.class, modId + ":" + blockTypeId);
                if (!optBlockType.isPresent()) {
                    BlockStateMappingsRegistry.logger.warn("Potential Block [" + blockTypeId + "] for Mod [" + modId + "] is not a valid Block!");
                    continue;
                }

                for (Map.Entry<Object, ? extends ConfigurationNode> mappedEntry : blockTypeEntry.getValue().getChildrenMap().entrySet()) {
                    final String mappedName = (String) mappedEntry.getKey();
                    final ConfigurationNode mappedNode = mappedEntry.getValue();

                    BlockState blockState = optBlockType.get().getDefaultState();
                    for (Map.Entry<Object, ? extends ConfigurationNode> valueEntry : mappedNode.getChildrenMap().entrySet()) {
                        BlockTrait<?> matchedTrait = null;
                        for (BlockTrait<?> trait : blockState.getType().getTraits()) {
                            if (trait.getName().equals(valueEntry.getKey())) {
                                matchedTrait = trait;
                                break;
                            }
                        }

                        if (matchedTrait == null) {
                            BlockStateMappingsRegistry.logger.warn("Potential Trait [" + valueEntry.getKey() + "] in [" + mappedName + "] is not valid for "
                                    + "Block [" + blockState.getType().getName() + "] in Mod [" + optPluginContainer.get().getId() + "]!");
                            continue;
                        }

                        final Optional<BlockState> optCombinedBlockState = blockState.withTrait(matchedTrait, valueEntry.getValue().getValue().
                                toString());
                        if (!optCombinedBlockState.isPresent()) {
                            BlockStateMappingsRegistry.logger.warn("Potential Value [" + valueEntry.getValue().getValue() + "] for provided Trait ["
                                    + matchedTrait.getName() + "] in [" + mappedName + "] is not valid for Block [" + blockState.getType().getName()
                                    + "] in Mod [" + optPluginContainer.get().getId() + "].");
                            continue;
                        }

                        blockState = optCombinedBlockState.get();
                    }

                    put(optPluginContainer.get(), mappedName, blockState);
                }
            }
        }
    }
}
