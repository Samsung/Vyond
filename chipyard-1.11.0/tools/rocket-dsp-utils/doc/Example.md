# Example

A basic DSP Module + Tester might look like this:

```scala
package SimpleDsp

// Allows you to use Chisel Module, Bundle, etc.
import chisel3.{fromDoubleToLiteral => _, fromIntToBinaryPoint => _, _}
// Allows you to use FixedPoint
import fixedpoint._
// If you want to take advantage of type classes >> Data:RealBits (i.e. pass in FixedPoint or DspReal)
// Required for you to use operators defined via type classes (+ has special Dsp overflow behavior, etc.)
import dsptools.numbers._
// Enables you to set DspContext's for things like overflow behavior, rounding modes, etc.
import dsptools.DspContext
// Chiseltest with peek-poke testers
import chiseltest._
import chiseltest.iotesters._
// Use dsptools extensions for peek-poke testing
import dsptools.misc.PeekPokeDspExtensions
// Scala unit testing style
import org.scalatest.{FlatSpec, Matchers}

// IO Bundle. Note that when you parameterize the bundle, you may need to override cloneType.
// This also creates x, y, z inputs/outputs (direction must be specified at some IO hierarchy level)
// of the type you specify via gen (must be Data:RealBits = UInt, SInt, FixedPoint, DspReal)
class SimpleDspIo[T <: Data:RealBits](gen: T) extends Bundle {
  val x = Input(gen)
  val y = Input(gen)
  val z = Output(gen)
}

// Parameterized Chisel Module; takes in type parameters as explained above
class SimpleDspModule[T <: Data:RealBits](gen: T, val addPipes: Int) extends Module {
  // This is how you declare an IO with parameters
  val io = IO(new SimpleDspIo(gen))
  // Output will be current x + y addPipes clock cycles later
  // Note that this relies on the fact that type classes have a special + that
  // add addPipes # of ShiftRegister after the sum. If you don't wrap the sum in 
  // DspContext.withNumAddPipes(addPipes), the default # of addPipes is used.
  DspContext.withNumAddPipes(addPipes) { 
    io.z := io.x + io.y
  }
}

// You create a tester that must extend PeekPokeDspExtensions to support Dsp type peeks/pokes (with doubles, complex, etc.)
class SimpleDspModuleTester[T <: Data:RealBits](c: SimpleDspModule[T]) extends PeekPokeTester(c) with PeekPokeDspExtensions {
  val x = Seq(-1.1, -0.4, 0.4, 1.1)
  val z = x map (2 * _)
  for (i <- 0 until (x.length + c.addPipes)) {
    val in = x(i % x.length)
    // Feed in to the x, y inputs
    poke(c.io.x, in)
    poke(c.io.y, in)
    if (i >= c.addPipes) {
      // Expect that the z output matches the expected value @ z(i - c.addPipes) to some tolerance
      // as described below
      expect(c.io.z, z(i - c.addPipes))
    }
    // Step the clock by 1 period
    step(1)
  }
}

// Scala style testing
class SimpleDspModuleSpec extends FlatSpec with ChiselScalatestTester with Matchers {

  behavior of "simple dsp module"

  it should "properly add fixed point types" in {
    // Run the tester by following this style: You need to pass in the Chisel Module [SimpleDspModule] 
    // to test. You must also specify the tester [SimpleDspModuleTester] to run with the module.
    // This tester should be something that extends PeekPokeTester with PeekPokeDspExtensions. 
    // Note that here, you're testing the module with inputs/outputs of FixedPoint type (Q15.12) 
    // and 3 registers (for retiming) at the output. You could alternatively use DspReal()
    // Scala keeps track of which tests pass/fail; the execute method returns true if the test passes. 
    // Supposedly, Chisel3 testing infrastructure might be overhauled to reduce the amount of boilerplate, 
    // but this is currently the endorsed way to do things.
    test(new SimpleDspModule(FixedPoint(16.W, 12.BP), addPipes = 3))
      .runPeekPoke(c => new SimpleDspModuleTester(c))
  }

}
```

Please read the above code + comments carefully. 

It shows you how to create a parameterized module + IO bundle (API might change again...) with generic type classes to allow you to test your "math" both with real numbers (that result in numerically correct outputs) and fixed point numbers (that allow you to factor in quantization, etc. and are actually synthesizable).

It also demonstrates a simple example of changing the Dsp Context. You can do this locally (per operation), at the module level, or at the top level (simply affected by where you wrap the DspContext.withNumAddPipes(addPipes) {}).

The example also shows you how a tester interacts with the DUT via peek and expect.

To run this single test, you can use the command `sbt "testOnly SimpleDsp.SimpleDspModuleSpec"`. Note that `sbt test` runs all tests in *src/test/scala*.
