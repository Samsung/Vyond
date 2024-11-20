package chipyard.fpga.vcu118

import chisel3._
import chisel3.experimental.{BaseModule}

import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.uart.{HasPeripheryUARTModuleImp, UARTPortIO}
import sifive.blocks.devices.spi.{HasPeripherySPI, SPIPortIO}
import sifive.fpgashells.shell._

import chipyard._
import chipyard.harness._
import chipyard.iobinders._

/*** UART ***/
class WithUART extends HarnessBinder({
  case (th: VCU118FPGATestHarnessImp, port: UARTPort, chipId: Int) => {
    th.vcu118Outer.io_uart_bb.bundle <> port.io
  }
})

/*** SPI ***/
class WithSPISDCard extends HarnessBinder({
  case (th: VCU118FPGATestHarnessImp, port: SPIPort, chipId: Int) => {
    th.vcu118Outer.io_spi_bb.bundle <> port.io
  }
})

/*** Experimental DDR ***/
class WithDDRMem extends HarnessBinder({
  case (th: VCU118FPGATestHarnessImp, port: TLMemPort, chipId: Int) => {
    val bundles = th.vcu118Outer.ddrClient.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io
  }
})

class WithJTAG extends HarnessBinder({
  case (th: VCU118FPGATestHarnessImp, port: JTAGPort, chipId: Int) => {

    port.io.TCK := th.jtag.TCK
    port.io.TMS := th.jtag.TMS
    port.io.TDI := th.jtag.TDI
    th.jtag.TDO := port.io.TDO

    // Chiptop does not expose the reset so skip the pin map for it.
    //val pin_locations = Map(
    //  "PMOD_J52" -> Seq("AW15",      "AU16",      "AV16",      "AY14",      "AY15"),
    //  "PMOD_J53" -> Seq( "N30",       "L31",       "P29",       "N28",       "M30"),
    //  "FMC_J2"   -> Seq("AL12",      "AN15",      "AP15",      "AM12",      "AK12"))
    val pin_locations = Map(
      "PMOD_J52" -> Seq("AW15",      "AU16",      "AV16",      "AY14"),
      "PMOD_J53" -> Seq( "N30",       "L31",       "P29",       "N28"),
      "FMC_J2"   -> Seq("AL12",      "AN15",      "AP15",      "AM12"))
    val pins      = Seq(th.jtag.TCK, th.jtag.TMS, th.jtag.TDI, th.jtag.TDO)

    th.vcu118Outer.sdc.addClock("JTCK", IOPin(th.jtag.TCK), 10)
    th.vcu118Outer.sdc.addGroup(clocks = Seq("JTCK"))
    th.vcu118Outer.xdc.clockDedicatedRouteFalse(IOPin(th.jtag.TCK))

    val pin_voltage:String = "LVCMOS18"
    (pin_locations("FMC_J2") zip pins) foreach { case (pin_location, ioport) =>
      val io = IOPin(ioport)
      th.vcu118Outer.xdc.addPackagePin(io, pin_location)
      th.vcu118Outer.xdc.addIOStandard(io, pin_voltage)
      th.vcu118Outer.xdc.addPullup(io)
      th.vcu118Outer.xdc.addIOB(io)
    }
  }
})

