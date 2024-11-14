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
  new chipyard.config.AbstractConfig)

class WGRocketAndRocketWithWGM extends Config(
  new worldguard.WithWorldGuard(nWorlds = 4, nSlots = 4) ++
  new worldguard.WithOneWGAwareRocketThreeRocketWithWGMarker ++
  new chipyard.config.AbstractConfig)
