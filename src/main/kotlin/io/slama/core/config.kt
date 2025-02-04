package io.slama.core

import io.slama.core.ConfigFolders.BOT_PROPERTIES
import io.slama.core.ConfigFolders.CALLS_DATA_ROOT
import io.slama.core.ConfigFolders.CONFIG_ROOT
import io.slama.core.ConfigFolders.DATA_ROOT
import io.slama.core.ConfigFolders.GUILD_CONFIG_ROOT
import io.slama.core.ConfigFolders.POLLS_DATA_ROOT
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.interactions.components.ButtonStyle
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

private val logger = LoggerFactory.getLogger("Configuration")

object ConfigFolders {
    const val BOT_PROPERTIES = "bot.properties"
    const val CONFIG_ROOT = "config/"
    const val DATA_ROOT = "data/"
    const val CALLS_DATA_ROOT = "${DATA_ROOT}calls/"
    const val POLLS_DATA_ROOT = "${DATA_ROOT}polls/"
    const val GUILD_CONFIG_ROOT = "${CONFIG_ROOT}guilds/"
}

class BotConfiguration private constructor() {

    companion object {
        val guilds: GuildConfigManager
            get() = innerConfig!!.guildConfigs /*
            Explicitly want an NPE if it's null here,
            because it can only happen if the JVM is literally dying
            */

        val shusher: ShusherConfig?
            get() = innerConfig!!.shusherConfig /*
            Explicitly want an NPE if it's null here,
            because it can only happen if the JVM is literally dying
            */

        val presence: PresenceConfig?
            get() = innerConfig!!.presenceConfig /*
            Explicitly want an NPE if it's null here,
            because it can only happen if the JVM is literally dying
            */

        val mail: MailConfig?
            get() = innerConfig!!.mailConfig /*
            Explicitly want an NPE if it's null here,
            because it can only happen if the JVM is literally dying
            */

        private var innerConfig: BotConfiguration? = null
            get() {
                if (field == null)
                    field = BotConfiguration()
                return field
            }

        private var backupGuilds: GuildConfigManager? = null
        private var backupShusher: ShusherConfig? = null
        private var backupPresence: PresenceConfig? = null
        private var backupMail: MailConfig? = null

        fun resetConfig() {
            backupGuilds = guilds
            backupShusher = shusher
            backupPresence = presence
            backupMail = mail
            setup()
            innerConfig = null
        }

        private fun setup() {
            with(File(BOT_PROPERTIES)) {
                if (!exists())
                    writeText("token=0\n")
                else if (!isFile)
                    throw IOException("Couldn't create a $BOT_PROPERTIES file.")
            }
            with(File(CONFIG_ROOT)) {
                mkdir()
                if (!isDirectory) throw IOException("Couldn't create the $CONFIG_ROOT directory.")
                else with(File(GUILD_CONFIG_ROOT)) {
                    mkdir()
                    if (!isDirectory)
                        throw IOException("Couldn't create the $GUILD_CONFIG_ROOT directory.")
                }
            }
            with(File(DATA_ROOT)) {
                mkdir()
                if (!this.isDirectory) throw IOException("Couldn't create the $DATA_ROOT directory.")
                else {
                    with(File(CALLS_DATA_ROOT)) {
                        mkdir()
                        if (!this.isDirectory) throw IOException("Couldn't create the $CALLS_DATA_ROOT directory.")
                    }
                    with(File(POLLS_DATA_ROOT)) {
                        mkdir()
                        if (!this.isDirectory) throw IOException("Couldn't create the $POLLS_DATA_ROOT directory.")
                    }
                }
            }
        }

        private fun createRolesFile(file: File) {
            file.createNewFile()
            file.writeText(
                """
                {
                  "adminRoleID": 0,
                  "managerRoleID": 1,
                  "teacherRoleID": 2,
                  "studentRoleID": 3
                }
                """.trimIndent()
            )
        }

        private fun createChannelsFile(file: File) {
            file.createNewFile()
            file.writeText(
                """
                {
                  "announcementsChannelID": 0,
                  "moodleAnnouncementsChannelsIDs": {
                  }
                }
                """.trimIndent()
            )
        }

        private fun createAutorolesFile(file: File) {
            file.createNewFile()
            file.writeText("{}\n")
        }

        private fun createShusherFile(file: File) {
            file.createNewFile()
            file.writeText(
                """
                {
                  "sentences": [
                    "Please stop talking..."
                  ]
                }
                """.trimIndent()
            )
        }

        private fun createPresenceFile(file: File) {
            file.createNewFile()
            file.writeText(
                """
                {
                  "messages": {
                    "something": "DEFAULT",
                    "something else": "WATCHING",
                    "some other thing": "LISTENING"
                  }
                }
                """.trimIndent()
            )
        }

        private fun createMailFile(file: File) {
            file.createNewFile()
            file.writeText(
                """
                {
                  "hostname": "mail.server.com",
                  "port": 993,
                  "username": "john.doe@server.com",
                  "password": "1234"
                }
                """.trimIndent()
            )
        }
    }

    class GuildConfigManager {

        private val guildConfigsMap: MutableMap<Long, GuildConfig> = mutableMapOf()

        operator fun get(guildId: Long): GuildConfig? {
            if (guildId !in guildConfigsMap.keys) {
                val old = backupGuilds?.guildConfigsMap?.get(guildId)
                try {
                    loadConfig(guildId)
                } catch (e: SerializationException) {
                    logger.warn("Failed to load guild config for guild $guildId. Using backup")
                    if (old != null) {
                        guildConfigsMap[guildId] = old
                    } else {
                        logger.error("No backup for guild $guildId. Guild will be ignored")
                        return null
                    }
                }
            }
            return guildConfigsMap[guildId]
        }

        @OptIn(ExperimentalSerializationApi::class)
        private fun loadConfig(guildId: Long) {
            with(File(GUILD_CONFIG_ROOT)) {
                if (!exists() || !isDirectory)
                    resetConfig()
            }
            File("$GUILD_CONFIG_ROOT$guildId").run {
                mkdir()
                if (!this.isDirectory)
                    throw IOException("Couldn't create the $name directory as there is already a file named like that.")
            }

            val rolesF = File("$GUILD_CONFIG_ROOT$guildId/roles.json")
            if (!rolesF.exists()) {
                createRolesFile(rolesF)
            }

            val channelsF = File("$GUILD_CONFIG_ROOT$guildId/channels.json")
            if (!channelsF.exists()) {
                createChannelsFile(channelsF)
            }

            val autorolesF = File("$GUILD_CONFIG_ROOT$guildId/autoroles.json")
            if (!autorolesF.exists()) {
                createAutorolesFile(autorolesF)
            }

            guildConfigsMap[guildId] = GuildConfig(
                Json.decodeFromString(rolesF.readText()),
                Json.decodeFromString(channelsF.readText()),
                Json.decodeFromString(autorolesF.readText()),
            )
            logger.info("Loaded config of guild $guildId")
        }
    }

    private val guildConfigs: GuildConfigManager by lazy {
        GuildConfigManager()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val shusherConfig: ShusherConfig? by lazy {
        with(File(CONFIG_ROOT)) {
            if (!exists() || !isDirectory)
                resetConfig()
        }
        val shusherF = File("${CONFIG_ROOT}shusher.json")
        if (!shusherF.exists()) {
            createShusherFile(shusherF)
        }

        var config: ShusherConfig?
        try {
            config = Json.decodeFromString(shusherF.readText())
            logger.info("Loaded shusher config")
        } catch (e: SerializationException) {
            logger.warn("Failed to load shusher config. Using backup")
            config = if (backupShusher != null) {
                backupShusher
            } else {
                logger.warn("No backup for shusher config. Using default")
                null
            }
        }
        config
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val presenceConfig: PresenceConfig? by lazy {
        with(File(CONFIG_ROOT)) {
            if (!exists() || !isDirectory)
                resetConfig()
        }
        val presenceF = File("${CONFIG_ROOT}presence.json")
        if (!presenceF.exists()) {
            createPresenceFile(presenceF)
        }
        var config: PresenceConfig?
        try {
            config = Json.decodeFromString(presenceF.readText())
            logger.info("Loaded presence config")
        } catch (e: SerializationException) {
            logger.warn("Failed to load presence config. Using backup")
            config = if (backupPresence != null) {
                backupPresence
            } else {
                logger.warn("No backup for presence config. Using default")
                null
            }
        }
        config
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val mailConfig: MailConfig? by lazy {
        with(File(CONFIG_ROOT)) {
            if (!exists() || !isDirectory)
                resetConfig()
        }
        val mailF = File("${CONFIG_ROOT}mail.json")
        if (!mailF.exists()) {
            createMailFile(mailF)
        }
        var config: MailConfig?
        try {
            config = Json.decodeFromString(mailF.readText())
            logger.info("Loaded mail config")
        } catch (e: SerializationException) {
            logger.warn("Failed to load mail config. Using backup.")
            config = if (backupMail != null) {
                backupMail
            } else {
                logger.error("No backup for mail config. Mail functionality will be disabled.")
                null
            }
        }
        config
    }
}

@Serializable
data class RolesDTO(
    val adminRoleID: Long,
    val managerRoleID: Long,
    val teacherRoleID: Long,
    val studentRoleID: Long,
)

@Serializable
data class ChannelsDTO(
    val announcementsChannelID: Long,
    val moodleAnnouncementsChannelsIDs: Map<String, Long>,
)

@Serializable
data class AutoRoleButtonDTO(
    val title: String,
    val role: Long,
    val emote: String? = null,
    val color: ButtonStyle = ButtonStyle.PRIMARY,
)

@Serializable
data class AutoRoleDTO(
    val title: String,
    val description: String,
    val color: Int,
    val roles: List<AutoRoleButtonDTO>,
    val maxRoles: Int = 1,
)

@Serializable
data class ShusherConfig(
    val sentences: List<String>,
)

@Serializable
data class PresenceConfig(
    val messages: Map<String, Activity.ActivityType>,
)

@Serializable
data class MailConfig(
    val hostname: String,
    val port: Int,
    val username: String,
    val password: String,
    val debugMode: Boolean = false,
    val enableSSL: Boolean = true,
)

data class GuildConfig(
    val roles: RolesDTO,
    val channels: ChannelsDTO,
    val autoRoles: Map<String, AutoRoleDTO>,
)
