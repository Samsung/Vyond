rocket-dsp-utils
================

[![Test](https://github.com/ucb-bar/rocket-dsp-utils/actions/workflows/test.yml/badge.svg?branch=master)](https://github.com/ucb-bar/rocket-dsp-utils/actions/workflows/test.yml)

This repository is part of a transition to move the rocket subdirectory from
[ucb-bar/dsptools]() to its own repository

----------

This README will be filled out later. At the moment it will only contain instructions to run it locally

Goals: Get the rocket sub-project of dsptools to run within the chipyard environment.
It is based on running using the chipyards rocket-chip commit.

This project now works as a stand-alone library. However, the main goal is still to work inside chipyard, so the commits of its dependencies will match those specified within chipyard.

---

Steps for using this library within your project:

1. Clone this repository and recursively initialize all of its submodules, in one of the following ways:
   * ```sh
     git clone https://github.com/ucb-bar/rocket-dsp-utils --recurse-submodules
     ```
   * ```sh
     git clone https://github.com/ucb-bar/rocket-dsp-utils
     git -C rocket-dsp-utils submodule update --init --recursive
     ```
2. Use `dependsOn` inside your project's build.sbt:

```sbt
lazy val `rocket-dsp-utils` = (project in file("rocket-dsp-utils"))

val yourproject = (project in file("."))
  .dependsOn(`rocket-dsp-utils`)
  .settings(
    ...
  )
```   

---

Questions:
- Questionable code is marked with //TODO: CHIPYARD


This code was maintained by [Chick](https://github.com/chick)
