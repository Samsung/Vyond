# WorldGuard-Aware Rocket Core

<img width="1202" alt="image" src="./images/wg_aware_rocket.png">


# WorldGuard-Aware L2 Cache

## Overall View
The following Figure shows the datapath of L2 Cache. Some modules are extended to store and forward `wid`. 
<img width="1202" alt="image" src="./images/l2_overview.png">

### Directory
<img width="1202" alt="image" src="./images/l2_directory.png">


### TAG and WID matching
WGAware L2 Cache checks both tag and wid during the directory lookup. If the tag is matched but not wid, wgaware l2 cache considers it as a miss and the corresponding cache block will be invalidated. The following table summarizes the hit and miss case.

| hit | onlyTagHit| bypassHit | bypassOnlyTagHit | wayMatch | result.bits               | result.bits.hit   | result.bits.hit_wid   | result.bits.wid           | result.bits.way       |
|--   |--         |--         | --               | --       | --                        | --                | --                    | --                        | --                    |
| X   | X         | 0         |1                 | 0        | ways(victimWay)           | 0                 | 0                     | wid                       | victimWay             |
| X   | X         | 1         |1                 | 1        | bypass.data               | 0                 | 0                     | wid                       | victimWay             |
| X   | X         | 0         |1                 | X        | bypass.data               | bypassHit         | bypassOnlyTagHit      | bypass.data.wid           | bypass.way            |
| X   | X         | 1         |1                 | X        | bypass.data               | bypassHit         | bypassOnlyTagHit      | wid                       | bypass.way            |
| 0   | 1         | X         |0                 | X        | Mux1H(onlyTagHits, ways)  | hit               | onlyTagHit            | Mux1H(onlyTagHits, ways)  | OHToUInt(onlyTagHits) |
| 1   | 1         | X         |0                 | X        | Mux1H(hits, ways)         | hit               | onlyTagHit            | wid                       | OHToUInt(hits)        |

<img width="1202" alt="image" src="./images/l2_tag_matching.png">

### Example Transactions on Access with different WID
<img width="583" alt="image" src="./images/t1.png">
<img width="576" alt="image" src="./images/t2.png">
<img width="581" alt="image" src="./images/t3.png">
<img width="580" alt="image" src="./images/t4.png">
<img width="587" alt="image" src="./images/t5.png">
<img width="585" alt="image" src="./images/t6.png">
<img width="584" alt="image" src="./images/t7.png">
<img width="585" alt="image" src="./images/t8.png">
