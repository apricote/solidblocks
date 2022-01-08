package de.solidblocks.provisioner.hetzner.cloud.floatingip

import de.solidblocks.core.IInfrastructureResource
import de.solidblocks.provisioner.hetzner.cloud.server.IServerLookup

data class FloatingIpAssignment(val server: IServerLookup, override val floatingIp: IFloatingIpLookup) :
    IFloatingIpAssignmentLookup,
    IInfrastructureResource<FloatingIpAssignment, FloatingIpAssignmentRuntime> {

    override val parents = setOf(server, floatingIp)

    override val name = "${this.server.name}-${this.floatingIp.name}"
}
