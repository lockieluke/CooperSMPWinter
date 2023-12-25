package me.lockie.coopersmpwinter

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.AdventureChatColorArgument
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.arguments.GreedyStringArgument
import dev.jorel.commandapi.arguments.StringArgument
import dev.jorel.commandapi.executors.CommandArguments
import dev.jorel.commandapi.executors.CommandExecutor
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import io.papermc.lib.PaperLib
import me.lockie.coopersmpwinter.items.CustomItem
import me.lockie.coopersmpwinter.items.CustomItemManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.net.URI
import java.util.stream.Collectors


class CooperSMPWinter : JavaPlugin() {
    private var customItemManager: CustomItemManager? = null
    private val audioEngine = AudioEngine(this)
    private var registeredEvents = false
    private fun loadCommands() {
        CommandAPICommand("winter")
            .executes(CommandExecutor { sender: CommandSender, args: CommandArguments? -> sender.sendMessage("§cUsage: /winter <reload|removeMainHandItem|giveSnowShovel>") })
            .withSubcommands(
                CommandAPICommand("reload")
                    .withPermission("winter.reload")
                    .executes(CommandExecutor { sender: CommandSender, args: CommandArguments? ->
                        reloadConfig()
                        sender.sendMessage("§aReloaded config")
                    }),
                CommandAPICommand("removeMainHandItem")
                    .withPermission("winter.removeMainHandItem")
                    .executesPlayer(PlayerCommandExecutor { player: Player, args: CommandArguments? ->
                        player.inventory.setItemInMainHand(null)
                        player.sendMessage("§aRemoved item in main hand")
                    }),
                CommandAPICommand("give")
                    .withPermission("winter.give")
                    .withArguments(StringArgument("item").replaceSuggestions(ArgumentSuggestions.strings(
                        customItemManager!!.customItems.stream().map { obj: CustomItem -> obj.id }
                            .collect(Collectors.toList())
                    )
                    )
                    )
                    .executesPlayer(PlayerCommandExecutor { player: Player, args: CommandArguments ->
                        val itemId = args[0] as String?
                        player.inventory.addItem(
                            requireNotNull(
                                customItemManager!!.getCustomItemById(
                                    requireNotNull(itemId)
                                )
                            )
                        )
                        player.sendMessage("§aGiven items")
                    }),
                CommandAPICommand("mockGameMessage")
                    .withPermission("winter.mockGameMessage")
                    .withArguments(AdventureChatColorArgument("chatcolor"))
                    .withArguments(GreedyStringArgument("message"))
                    .executes(CommandExecutor { sender: CommandSender, args: CommandArguments? ->
                        val color = args!!["chatcolor"] as NamedTextColor?
                        val message = args["message"] as String?
                        server.broadcast(Component.text("$message").color(color))
                    }),
                CommandAPICommand("title")
                    .withPermission("winter.title")
                    .withArguments(AdventureChatColorArgument("titlecolor"))
                    .withArguments(GreedyStringArgument("title"))
                    .executes(CommandExecutor { sender: CommandSender, args: CommandArguments? ->
                        val color = args!!["titlecolor"] as NamedTextColor?
                        val title = args["title"] as String?
                        server.onlinePlayers.forEach { player ->
                            player.showTitle(Title.title(Component.text("$title").color(color), Component.empty()))
                        }
                    }),
                CommandAPICommand("downloadAudio")
                    .withPermission("winter.downloadAudio")
                    .withArguments(StringArgument("name"))
                    .withArguments(GreedyStringArgument("url"))
                    .executes(CommandExecutor { sender: CommandSender, args: CommandArguments? ->
                        val name = args!!["name"] as String?
                        val url = URI.create(requireNotNull(args["url"] as String?))
                        val fileBytes = url.toURL().readBytes()
                        val fileFormat = url.path.substringAfterLast(".")

                        this.audioEngine.saveAudioFile("$name.$fileFormat", fileBytes)
                    }),
            )
            .register()
    }

    override fun onLoad() {
        if (!CommandAPI.isLoaded()) CommandAPI.onLoad(CommandAPIBukkitConfig(this))
    }

    override fun onEnable() {
        PaperLib.suggestPaper(this)

        CommandAPI.onEnable()
        logger.info("${this.name} enabled")
        saveDefaultConfig()

        val customItemManager = CustomItemManager()
        val pluginManager = server.pluginManager
        if (!this.registeredEvents) {
            pluginManager.registerEvents(WinterEventListener(this, this.audioEngine), this)
            this.registeredEvents = true
        }
        customItemManager.registerCustomItems()
        this.customItemManager = customItemManager

        if (!this.server.messenger.isOutgoingChannelRegistered(this, AudioEngine.AUDIO_PLAYBACK_CHANNEL))
            this.server.messenger.registerOutgoingPluginChannel(this, AudioEngine.AUDIO_PLAYBACK_CHANNEL)

        loadCommands()
    }

    override fun onDisable() {
        if (this.server.messenger.isOutgoingChannelRegistered(this, AudioEngine.AUDIO_PLAYBACK_CHANNEL))
            this.server.messenger.unregisterOutgoingPluginChannel(this, AudioEngine.AUDIO_PLAYBACK_CHANNEL)

        CommandAPI.unregister("winter")
        logger.info("${this.name} disabled")
    }
}
