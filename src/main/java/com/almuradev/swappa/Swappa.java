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

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.GameRegistry;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTransaction;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.trait.BlockTrait;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GameLoadCompleteEvent;
import org.spongepowered.api.event.world.chunk.PopulateChunkEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.plugin.PluginManager;
import org.spongepowered.api.service.config.DefaultConfig;
import org.spongepowered.api.text.Texts;
import org.spongepowered.api.util.command.CommandException;
import org.spongepowered.api.util.command.CommandResult;
import org.spongepowered.api.util.command.spec.CommandSpec;
import org.spongepowered.api.world.gen.PopulatorType;
import org.yaml.snakeyaml.DumperOptions;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Plugin(id = Swappa.PLUGIN_ID, name = Swappa.PLUGIN_NAME, version = Swappa.PLUGIN_VERSION)
public class Swappa {

    public static final String PLUGIN_ID = "swappa", PLUGIN_NAME = "Swappa", PLUGIN_VERSION = "1.0-r2.1", POPULATORS_FILE = "populators.yml",
            BLOCKSTATES_FILE = "blockstates.yml", DUMP_FILE = "dump.yml";
    public static Swappa instance;

    @Inject public PluginManager pluginManager;
    @Inject public GameRegistry registry;
    @Inject public Logger logger;
    public ConfigurationNode populatorsRootNode, blockStatesRootNode;
    @Inject
    @DefaultConfig(sharedRoot = false)
    private File configDir;
    private YAMLConfigurationLoader populatorMappingsConfigLoader, blockStateMappingsConfigLoader;

    public Swappa() {
        instance = this;
    }

    @Listener
    public void onGameInitialization(GameInitializationEvent event) {
        event.getGame().getCommandDispatcher().register(this, CommandSpec.builder()
                .permission("swappa.command.dump")
                .executor((src, args) -> {
                    final Path dumpMappingsPath = configDir.toPath().getParent().resolve(Swappa.DUMP_FILE);
                    if (Files.exists(dumpMappingsPath)) {
                        try {
                            Files.delete(dumpMappingsPath);
                        } catch (IOException e) {
                            throw new CommandException(Texts.of(e));
                        }
                    }

                    final YAMLConfigurationLoader dumpMappingsConfigLoader =
                            YAMLConfigurationLoader.builder().setFile(dumpMappingsPath.toFile()).build();
                    final ConfigurationNode dumpRootNode = dumpMappingsConfigLoader.createEmptyNode(ConfigurationOptions.defaults());
                    final List<BlockType> blockTypes = (List<BlockType>) registry.getAllOf(BlockType.class);
                    final List<BlockType> sorted = Lists.newArrayList(blockTypes);
                    Collections.sort(sorted, (o1, o2) -> o1.getName().compareTo(o2.getName()));

                    for (BlockType blockType : sorted) {
                        final String[] modIdName = blockType.getName().split(":");
                        final ConfigurationNode blockTypeRootNode = dumpRootNode.getNode(modIdName[0], modIdName[1]);
                        if (!blockType.getTraits().isEmpty()) {
                            for (BlockTrait<?> blockTrait : blockType.getTraits()) {
                                final ConfigurationNode blockTraitRootNode = blockTypeRootNode.getNode(blockTrait.getName());
                                final Collection<? extends Comparable<?>> possibleValues = blockTrait.getPossibleValues();
                                blockTraitRootNode.setValue(possibleValues);
                            }
                        }
                    }

                    try {
                        dumpMappingsConfigLoader.save(dumpRootNode);

                        src.sendMessage(Texts.of("Dumped saved successfully."));
                    } catch (IOException e) {
                        throw new CommandException(Texts.of(e));
                    }

                    return CommandResult.success();
                })
                .build(), "dump");
    }

    @Listener
    public void onGameLoadComplete(GameLoadCompleteEvent event) throws Exception {
        loadConfig();
        BlockStateMappingsRegistry.load();
        PopulatorMappingsRegistry.load();

        for (Map.Entry<PluginContainer, Map<String, BlockState>> entry : BlockStateMappingsRegistry.getAll().entrySet()) {
            this.logger.info("Mod [" + entry.getKey().getId() + "] mapped [" + entry.getValue() + "].");
        }
    }

    @Listener
    public void onPopulateChunkPost(PopulateChunkEvent.Post event) {
        for (Map.Entry<PopulatorType, List<BlockTransaction>> populatorTypeTransactionEntry : event.getPopulatedTransactions().entrySet()) {
            for (BlockTransaction transaction : populatorTypeTransactionEntry.getValue()) {
                Optional<PopulatorMappingsRegistry.ReplacementEntry> optReplacementEntry = PopulatorMappingsRegistry.getEntry
                        (populatorTypeTransactionEntry.getKey(), transaction.getFinalReplacement().getState());

                if (optReplacementEntry.isPresent()) {
                    transaction.setCustomReplacement(transaction.getFinalReplacement().withState(optReplacementEntry.get().replacementBlockState));
                } else {
                    optReplacementEntry = PopulatorMappingsRegistry.getEntry(transaction.getFinalReplacement().getState());
                    if (optReplacementEntry.isPresent()) {
                        transaction
                                .setCustomReplacement(transaction.getFinalReplacement().withState(optReplacementEntry.get().replacementBlockState));
                    }
                }
            }
        }
    }

    private void loadConfig() throws IOException {
        final Path blockStateMappingsPath = configDir.toPath().getParent().resolve(Swappa.BLOCKSTATES_FILE);
        final Path populatorMappingsPath = configDir.toPath().getParent().resolve(Swappa.POPULATORS_FILE);
        populatorMappingsConfigLoader = YAMLConfigurationLoader.builder().setFlowStyle(DumperOptions.FlowStyle.BLOCK).setFile(populatorMappingsPath
                .toFile()).build();
        blockStateMappingsConfigLoader = YAMLConfigurationLoader.builder().setFlowStyle(DumperOptions.FlowStyle.BLOCK).setFile
                (blockStateMappingsPath.toFile()).build();

        try {
            Files.createDirectories(configDir.toPath().getParent());
        } catch (FileAlreadyExistsException ignore) {
        }

        if (Files.notExists(blockStateMappingsPath)) {
            final ConfigurationOptions blockStatesOptions = ConfigurationOptions.defaults().setHeader(
                    "1.0\n"
                    + "BlockState Mappings File.\n"
                    + "\n"
                    + "BlockState mappings can easily be added so long as you follow the specification:\n"
                    + "\n"
                    + "mappings:\n"
                    + "    mod_id: -> Consult the mod/plugin author for this\n"
                    + "        block_id -> Wiki/Mod Author/Block.java in official source is your friend\n"
                    + "            some_readable_name: -> This is the name you'll use in populators.yml, can be whatever you want\n"
                    + "                some_property_name: -> some_property_value\n"
                    + "                ... continue for how many properties that may \n"
                    + "\n"
                    + "Example:\n"
                    + "\n"
                    + "mappings:\n"
                    + "    minecraft:\n"
                    + "        log2:\n"
                    + "            log2_acacia_down:\n"
                    + "                variant: acacia\n"
                    + "                axis: y\n"
                    + "");
            blockStatesRootNode = blockStateMappingsConfigLoader.createEmptyNode(blockStatesOptions);
            blockStatesRootNode.getNode("general", "debug").setValue(false);
            blockStatesRootNode.getNode("mappings", "minecraft").setValue("");
            blockStateMappingsConfigLoader.save(blockStatesRootNode);
        } else {
            blockStatesRootNode = blockStateMappingsConfigLoader.load();
        }

        if (Files.notExists(populatorMappingsPath)) {
            final ConfigurationOptions populatorsOptions = ConfigurationOptions.defaults().setHeader(
                    "1.0\n"
                    + "Populator Mappings File.\n"
                    + "\n"
                    + "Populator mappings can easily be added so long as you follow the specification:\n"
                    + "\n"
                    + "populators:\n"
                    + "    mod_id: -> Consult the mod/plugin author for this\n"
                    + "        populator_id: ->\n"
                    + "            mapped_populator_block: mapped_replacement_block -> Instructs the populator to take the populator block\n"
                    + "                                                                and replace it with the replacement block. An example\n"
                    + "                                                                would be taking stone and replacing with water.\n"
                    + "\n"
                    + "Another feature is an \"all\" replacement. This means, literally, replace all found instances of a blockstate with\n"
                    + "with another blockstate.\n"
                    + "\n"
                    + "all:\n"
                    + "    mapped_populator_block: mapped_replacement_block\n"
                    + "\n"
                    + "Example:\n"
                    + "\n"
                    + "all:\n"
                    + "    water: grass\n"
                    + "populators:\n"
                    + "    minecraft:\n"
                    + "        ore:\n"
                    + "            stone: diamond_block");
            populatorsRootNode = populatorMappingsConfigLoader.createEmptyNode(populatorsOptions);
            populatorsRootNode.getNode("general", "debug").setValue(false);
            populatorsRootNode.getNode("all").setValue("");
            populatorsRootNode.getNode("populators", "minecraft").setValue("");
            populatorMappingsConfigLoader.save(populatorsRootNode);
        } else {
            populatorsRootNode = populatorMappingsConfigLoader.load();
        }
    }
}
