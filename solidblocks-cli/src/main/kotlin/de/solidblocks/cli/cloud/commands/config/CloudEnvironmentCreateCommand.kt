package de.solidblocks.cli.cloud.commands.config

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import de.solidblocks.cli.config.SpringContextUtil
import de.solidblocks.cloud.config.CloudConfigurationManager
import de.solidblocks.provisioner.hetzner.cloud.createHetznerCloudApiToken
import de.solidblocks.provisioner.hetzner.dns.createHetznerDnsApiTokenConfig
import mu.KotlinLogging
import org.springframework.stereotype.Component
import kotlin.io.path.ExperimentalPathApi
import kotlin.system.exitProcess

@Component
class CloudEnvironmentCreateCommand :
        CliktCommand(name = "create-environment", help = "create a new cloud environment") {

    val name: String by option(help = "name of the cloud").required()

    val environment: String by option(help = "cloud environment").required()

    val hetznerCloudApiToken: String by option(help = "Hetzner Cloud api token").required()

    val hetznerDnsApiToken: String by option(help = "Hetzner DNS api token").required()

    private val logger = KotlinLogging.logger {}

    @OptIn(ExperimentalPathApi::class)
    override fun run() {

        logger.error { "creating environment '${environment}' for cloud '$name'" }

        SpringContextUtil.bean(CloudConfigurationManager::class.java).let {

            if (!it.hasCloud(name)) {
                logger.error { "cloud '$name' not found" }
                exitProcess(1)
            }

            if (!it.createEnvironment(name, environment, listOf(
                            createHetznerCloudApiToken(hetznerCloudApiToken),
                            createHetznerDnsApiTokenConfig(hetznerDnsApiToken),
                    ))) {
                exitProcess(1)
            }
        }
    }
}
