package chipyard

import org.chipsalliance.cde.config.{Config}

class RocketWithWGMConfig extends Config(
  new worldguard.WithWorldGuard(nWorlds = 4, nSlots = 4) ++
  new worldguard.WithNBigRocketCoresWithWGM(1) ++
  new chipyard.config.AbstractConfig)

class DualRocketWithWGMConfig extends Config(
  new worldguard.WithWorldGuard(nWorlds = 4, nSlots = 4) ++
  new worldguard.WithNBigRocketCoresWithWGM(2) ++
  new chipyard.config.AbstractConfig)

class WGRocketConfig extends Config(
  new worldguard.WithWorldGuard(nWorlds = 4, nSlots = 4) ++
  new worldguard.WithWGRocketNBigCores(1) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new chipyard.config.AbstractConfig)

// Single-hart builds with more than the 4 worlds WGRocketConfig uses, and with
// the L2 cross-WID eviction guard on: before evicting a line that belongs to
// another WID, the L2 asks the memory-port checker whether the requester may
// touch the address, and refuses without evicting when it may not. Permitted
// sharers take the path they always took.

// 8-world variant (widWidth=3). Matches an SM firmware built with NWORLDS=8.
class WGRocket8Config extends Config(
  new worldguard.WithWGL2EvictGuard ++
  new worldguard.WithWorldGuard(nWorlds = 8, nSlots = 8) ++
  new worldguard.WithWGRocketNBigCores(1) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new chipyard.config.AbstractConfig)

// 16 worlds (widWidth=4).
class WGRocket16Config extends Config(
  new worldguard.WithWGL2EvictGuard ++
  new worldguard.WithWorldGuard(nWorlds = 16, nSlots = 16) ++
  new worldguard.WithWGRocketNBigCores(1) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new chipyard.config.AbstractConfig)

// Architectural maximum: 32 worlds (widWidth=5). perm is 2 bits per world, so
// 32*2 = 64 bits exactly fills the perm register; 33 or more would overflow.
class WGRocket32Config extends Config(
  new worldguard.WithWGL2EvictGuard ++
  new worldguard.WithWorldGuard(nWorlds = 32, nSlots = 32) ++
  new worldguard.WithWGRocketNBigCores(1) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new chipyard.config.AbstractConfig)

class WGRocketAndRocketWithWGM extends Config(
  new worldguard.WithWorldGuard(nWorlds = 4, nSlots = 4) ++
  new worldguard.WithOneWGAwareRocketOneRocketWithWGMarker++
  //new worldguard.WithOneWGAwareRocketThreeRocketWithWGMarker ++
  new chipyard.config.AbstractConfig)
