package io.slama.events

import io.slama.core.BotConfiguration
import io.slama.core.ConfigFolders
import io.slama.utils.isAdmin
import io.slama.utils.isManager
import io.slama.utils.isTeacher
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.random.Random

//private val logger: Logger = LoggerFactory.getLogger("Shusher")

class Banword(
    val jda: JDA
) : ListenerAdapter() {

    init {
        jda.addEventListener(this)
    }

    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
        event.member?.let { member ->
            if (member.isAdmin() or member.isManager() or member.isTeacher()) {
                return
            }

            if (event.message.contentRaw.contains(Regex("[sS]+[hH]*[eE]+[sS]+[hH]+"))) {
                    val sentence = BotConfiguration.shusher?.sentences?.random() ?: "Bouge."
                    event.channel.sendMessage(sentence).queue()
            }
        }
    }
}
