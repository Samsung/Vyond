// SPDX-License-Identifier: Apache-2.0

package dspblocks

import chiseltest._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.system.BaseConfig
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DspBlockSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  implicit val p: Parameters = (new BaseConfig).toInstance

//  behavior of "Passthrough"
//
//  it should "work with AXI4" in {
//    val params = PassthroughParams(depth = 5)
//    val lazymod = LazyModule(new AXI4Passthrough(params) with AXI4StandaloneBlock)
//
//    test(lazymod.module).runPeekPoke(_ => new AXI4PassthroughTester(lazymod))
//  }
//
//  it should "work with APB" in {
//    val params = PassthroughParams(depth = 5)
//    val lazymod = LazyModule(new APBPassthrough(params) with APBStandaloneBlock)
//
//    test(lazymod.module).runPeekPoke(_ => new APBPassthroughTester(lazymod))
//  }
//
//  it should "work with TL" ignore {
//    val params = PassthroughParams(depth = 5)
//    val lazymod = LazyModule(new TLPassthrough(params) with TLStandaloneBlock)
//
//    test(lazymod.module).runPeekPoke(_ => new TLPassthroughTester(lazymod))
//  }
//
//  behavior of "Byte Rotate"
//
//  it should "work with AXI4" in {
//    val lazymod = LazyModule(new AXI4ByteRotate() with AXI4StandaloneBlock)
//
//    test(lazymod.module).runPeekPoke(_ => new AXI4ByteRotateTester(lazymod))
//  }
//
//  it should "work with APB" in {
//    val lazymod = LazyModule(new APBByteRotate() with APBStandaloneBlock)
//
//    test(lazymod.module).runPeekPoke(_ => new APBByteRotateTester(lazymod))
//  }
//
//  it should "work with TL" ignore {
//    val lazymod = LazyModule(new TLByteRotate() with TLStandaloneBlock)
//
//    test(lazymod.module)
//      .withAnnotations(Seq(VerilatorBackendAnnotation))
//      .runPeekPoke(_ => new TLByteRotateTester(lazymod))
//  }
//
//  behavior of "PTBR Chain"
//
//  it should "work with APB" in {
//    val lazymod = LazyModule(new APBChain(Seq(
//      implicit p => LazyModule(new APBPassthrough(PassthroughParams(5))),
//      implicit p => LazyModule(new APBByteRotate() {
//        override def csrAddress: AddressSet = AddressSet(0x100, 0xFF)
//      })
//    )) with APBStandaloneBlock)
//
//    test(lazymod.module).runPeekPoke(_ => new APBPTBRTester(lazymod, 0, 0x100))
//  }
//
//  it should "work with AXI4" in {
//    val lazymod = LazyModule(new AXI4Chain(Seq(
//      implicit p => LazyModule(new AXI4Passthrough(PassthroughParams(5))),
//      implicit p => LazyModule(new AXI4ByteRotate() {
//        override def csrAddress: AddressSet = AddressSet(0x100, 0xFF)
//      })
//    )) with AXI4StandaloneBlock)
//
//    test(lazymod.module).runPeekPoke(_ => new AXI4PTBRTester(lazymod, 0, 0x100))
//  }
//
//  it should "work with TL" ignore {
//    val lazymod = LazyModule(new TLChain(Seq(
//      implicit p => LazyModule(new TLPassthrough(PassthroughParams(5))),
//      implicit p => LazyModule(new TLByteRotate() {
//        override def csrAddress: AddressSet = AddressSet(0x100, 0xFF)
//      })
//    )) with TLStandaloneBlock)
//
//    test(lazymod.module)
//      .withAnnotations(Seq(VerilatorBackendAnnotation))
//      .runPeekPoke(_ => new TLPTBRTester(lazymod, 0, 0x100))
//  }
}
