package net.explorviz.span.hash;

import java.nio.charset.StandardCharsets
import java.util.UUID

object HashHelper {

private val HIGHWAY_HASH_KEY = longArrayOf(
    0x45_78_70_6c_6f_72_56_69L, // CHECKSTYLE.SUPPRESS: Indentation
    0x7a_53_70_61_6e_73_48_69L,
    0x67_68_77_61_79_48_61_73L,
    0x68_43_6f_64_65_4b_65_79L
)

@JvmStatic fun calculateSpanHash(
    landscapeToken: UUID,
    nodeIpAddress: String,
    applicationName: String,
    applicationInstance: Long,
    methodFqn: String,
    k8sPodName: String?,
    k8sNodeName: String?,
    k8sNamespace: String?,
    k8sDeploymentName: String?
): String {
  val hash = HighwayHash(HIGHWAY_HASH_KEY)

  // TODO: Fill with IPv6 address bits (Convert IPv4 to IPv4-in-IPv6 representation)
  hash.update(
      landscapeToken.mostSignificantBits,
      landscapeToken.leastSignificantBits,
      applicationInstance,
      0L
  )

  val builder = StringBuilder().apply {
    append(applicationName)
    append(';')
    append(nodeIpAddress)
    append(';')
    append(methodFqn)
    if (!k8sPodName.isNullOrEmpty()) {
      append(';')
      append(k8sPodName)
      append(';')
      append(k8sNodeName)
      append(';')
      append(k8sNamespace)
      append(';')
      append(k8sDeploymentName)
    }
  }

  val bytes = builder.toString().toByteArray(StandardCharsets.UTF_8)
  var position = 0
  while (bytes.size - position >= 32) {
    hash.updatePacket(bytes, position)
    position += 32
  }
  val remaining = bytes.size - position
  if (remaining > 0) {
    hash.updateRemainder(bytes, position, remaining)
  }

  return hash.finalize64().toString()
}
}
