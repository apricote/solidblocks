package de.solidblocks.service.integrationtest

import de.solidblocks.base.Wrapper
import de.solidblocks.core.utils.LinuxCommandExecutor
import de.solidblocks.service.base.TriggerUpdateRequest
import de.solidblocks.service.base.TriggerUpdateResponse
import de.solidblocks.service.base.VersionResponse
import de.solidblocks.test.DevelopmentEnvironment
import de.solidblocks.test.DevelopmentEnvironmentExtension
import de.solidblocks.test.KDockerComposeContainer
import mu.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File
import java.net.ConnectException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.concurrent.thread

@ExtendWith(DevelopmentEnvironmentExtension::class)
class ServiceIntegrationTest {

    private val logger = KotlinLogging.logger {}

    var commandExecutor: LinuxCommandExecutor? = null

    private val blueVersion =
        ServiceIntegrationTest::class.java.getResource("/service-integrationtest/bootstrap/artefacts/blue.version")
            .readText().trim()

    private val greenVersion =
        ServiceIntegrationTest::class.java.getResource("/service-integrationtest/bootstrap/artefacts/green.version")
            .readText().trim()

    @Test
    fun testAgentUpdate(developmentEnvironment: DevelopmentEnvironment) {
        logger.info { "blue version for integration test is '$blueVersion'" }
        logger.info { "green version for integration test is '$greenVersion'" }

        val dockerEnvironment =
            KDockerComposeContainer(File("src/test/resources/service-integrationtest/docker-compose.yml"))
                .apply {
                    withEnv(
                        mapOf(
                            "SOLIDBLOCKS_BLUE_VERSION" to blueVersion,
                            "SOLIDBLOCKS_GREEN_VERSION" to greenVersion
                        )
                    )
                    withExposedService("bootstrap", 80)
                }
        dockerEnvironment.start()

        val vaultToken = developmentEnvironment.createService("test")
        val solidblocksDirectory = createSolidblocksDirectory(vaultToken, blueVersion, dockerEnvironment)

        thread {
            while (true) {
                val workingDir = System.getProperty("user.dir")
                commandExecutor = LinuxCommandExecutor()
                val result = commandExecutor!!.executeCommand(
                    printStream = true,
                    environment = mapOf("SOLIDBLOCKS_DIR" to solidblocksDirectory.toString()),
                    workingDir = File(workingDir),
                    command = listOf("$workingDir/solidblocks-service-wrapper.sh").toTypedArray()
                )

                logger.info { "service wrapper exited with ${result.code}" }
            }
        }

        val client = de.solidblocks.base.HttpClient("http://localhost:8080")

        await ignoreException (ConnectException::class) until {
            val response: Wrapper<VersionResponse> = client.get("/v1/agent/version")
            response.isSuccessful
        }

        val beforeUpdateVersionResponse: Wrapper<VersionResponse> = client.get("/v1/agent/version")
        assertThat(beforeUpdateVersionResponse.data?.version).isEqualTo(blueVersion)

        val triggerResponse: Wrapper<TriggerUpdateResponse> =
            client.post("/v1/agent/trigger-update", TriggerUpdateRequest(greenVersion))
        assertThat(triggerResponse.data?.triggered).isTrue

        await atMost (Duration.ofSeconds(15)) ignoreException (ConnectException::class) untilAsserted {
            val afterUpdateVersionResponse: Wrapper<VersionResponse> = client.get("/v1/agent/version")
            assertThat(afterUpdateVersionResponse.data?.version).isEqualTo(greenVersion)
        }
    }

    private fun createSolidblocksDirectory(
        vaultToken: String,
        solidblocksVersion: String,
        dockerEnvironment: KDockerComposeContainer
    ): Path? {

        val solidblocksDir = Files.createTempDirectory("service-integrationtest")

        val protectedDir = File(solidblocksDir.toFile(), "protected")
        protectedDir.mkdirs()

        val initialEnvironmentFile = File(protectedDir, "environment")
        initialEnvironmentFile.writeText(
            """
            VAULT_TOKEN=$vaultToken
            GITHUB_TOKEN_RO=XXX
            GITHUB_USERNAME=YYY
            """.trimIndent()
        )
        logger.info { "created initial environment file: '$initialEnvironmentFile'" }

        val instanceDir = File(solidblocksDir.toFile(), "instance")
        instanceDir.mkdirs()

        val instanceEnvironmentFile = File(instanceDir, "environment")
        instanceEnvironmentFile.writeText(
            """
            SOLIDBLOCKS_VERSION=$solidblocksVersion
            SOLIDBLOCKS_BOOTSTRAP_ADDRESS=http://localhost:${dockerEnvironment.getServicePort("bootstrap", 80)}
            """.trimIndent()
        )
        logger.info { "created environment instance file: '$instanceEnvironmentFile'" }

        val serviceDir = File(solidblocksDir.toFile(), "service")
        serviceDir.mkdirs()

        val serviceEnvironmentFile = File(serviceDir, "environment")
        serviceEnvironmentFile.writeText(
            """
            SOLIDBLOCKS_COMPONENT=solidblocks-service-base-integrationtest
            """.trimIndent()
        )
        logger.info { "created service instance file: '$serviceEnvironmentFile'" }

        return solidblocksDir
    }

    @AfterEach
    fun cleanUp() {
        commandExecutor?.kill()
    }
}