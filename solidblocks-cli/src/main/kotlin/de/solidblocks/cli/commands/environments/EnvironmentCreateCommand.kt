package de.solidblocks.cli.commands.environments

import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import de.solidblocks.cli.config.CliApplicationCloudCreate
import de.solidblocks.cli.self.BaseSpringCommand
import de.solidblocks.cloud.config.CloudConfigurationManager
import de.solidblocks.cloud.config.Constants.ConfigKeys.Companion.GITHUB_TOKEN_RO_KEY
import de.solidblocks.cloud.config.Constants.ConfigKeys.Companion.HETZNER_CLOUD_API_TOKEN_RO_KEY
import de.solidblocks.cloud.config.Constants.ConfigKeys.Companion.HETZNER_CLOUD_API_TOKEN_RW_KEY
import de.solidblocks.cloud.config.Constants.ConfigKeys.Companion.HETZNER_DNS_API_TOKEN_RW_KEY
import de.solidblocks.cloud.config.model.createConfigValue
import org.springframework.stereotype.Component
import kotlin.system.exitProcess

@Component
class EnvironmentCreateCommand :
    BaseSpringCommand(
        name = "create",
        help = "create new environment",
        cliClass = CliApplicationCloudCreate::class.java
    ) {

    val cloud: String by option(help = "name of the cloud").required()

    val environment: String by option(help = "cloud environment").required()

    val hetznerCloudApiTokenReadOnly: String by option(help = "Hetzner Cloud api token (ro)").required()

    val hetznerCloudApiTokenReadWrite: String by option(help = "Hetzner Cloud api token (rw)").required()

    val hetznerDnsApiToken: String by option(help = "Hetzner DNS api token").required()

    val githubReadOnlyToken: String by option(help = "Github read only API token").required()

    override fun run() {
        runSpringApplication(mapOf("spring.profiles.active" to "CloudCreate")) {
            it.getBean(CloudConfigurationManager::class.java).let {
                if (!it.createEnvironment(
                        cloud, environment,
                        listOf(
                                createConfigValue(GITHUB_TOKEN_RO_KEY, githubReadOnlyToken),
                                createConfigValue(HETZNER_CLOUD_API_TOKEN_RO_KEY, hetznerCloudApiTokenReadOnly),
                                createConfigValue(HETZNER_CLOUD_API_TOKEN_RW_KEY, hetznerCloudApiTokenReadWrite),
                                createConfigValue(HETZNER_DNS_API_TOKEN_RW_KEY, hetznerDnsApiToken),
                            )
                    )
                ) {
                    exitProcess(1)
                }
            }
        }
    }
}