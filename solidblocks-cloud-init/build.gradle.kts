plugins {
    id("solidblocks.kotlin-library-conventions")
    id("solidblocks.kotlin-publish-conventions")
}

sourceSets {
    main {
        resources {
            setSrcDirs(listOf("src"))
        }
    }
}

abstract class GenerateTask @Inject constructor(@get:Input val projectLayout: ProjectLayout) : DefaultTask() {

    fun generate_cloud_init(target: String, files: List<String>) {
        val file_contents = files.map { file ->
            File("${projectLayout.projectDirectory}/src/$file").readText(Charsets.UTF_8)
        }

        File("${projectLayout.projectDirectory}/src/lib-cloud-init-generated").mkdirs()
        File("${projectLayout.projectDirectory}/src/lib-cloud-init-generated/$target").writeText(file_contents.joinToString("\n"), Charsets.UTF_8)
    }

    @TaskAction
    fun generate() {

        generate_cloud_init(
            "controller-cloud-init.sh",
            listOf(
                "lib-cloud-init/shell-script-header.sh",
                "lib-cloud-init/cloud-init-variables.sh",
                "lib/configuration.sh",
                "lib/curl.sh",
                "lib/package.sh",
                "lib/network.sh",
                "lib/vault.sh",
                "lib/storage.sh",
                "lib-cloud-init/controller-cloud-init-body.sh"
            )
        )

        generate_cloud_init(
            "service-cloud-init.sh",
            listOf(
                "lib-cloud-init/shell-script-header.sh",
                "lib-cloud-init/cloud-init-variables.sh",
                "lib/configuration.sh",
                "lib/curl.sh",
                "lib/package.sh",
                "lib/network.sh",
                "lib/vault.sh",
                "lib/storage.sh",
                "lib-cloud-init/service-cloud-init-body.sh"
            )
        )

        generate_cloud_init(
            "backup-cloud-init.sh",
            listOf(
                "lib-cloud-init/shell-script-header.sh",
                "lib-cloud-init/cloud-init-variables.sh",
                "lib-cloud-init/vault-cloud-init-variables.sh",
                "lib/configuration.sh",
                "lib/curl.sh",
                "lib/package.sh",
                "lib/network.sh",
                "lib/vault.sh",
                "lib/storage.sh",
                "lib-cloud-init/backup-cloud-init-body.sh"
            )
        )
        generate_cloud_init(
            "vault-cloud-init.sh",
            listOf(
                "lib-cloud-init/shell-script-header.sh",
                "lib-cloud-init/cloud-init-variables.sh",
                "lib-cloud-init/vault-cloud-init-variables.sh",
                "lib/configuration.sh",
                "lib/network.sh",
                "lib/ssh.sh",
                "lib/storage.sh",
                "lib/package.sh",
                "lib-cloud-init/vault-cloud-init-body.sh"
            )
        )
    }
}
tasks.register<GenerateTask>("generate")

tasks.getByName("jar").dependsOn("generate")

publishing {
    publications {
        register<MavenPublication>("gpr") {
            from(components["java"])
        }
    }
}
