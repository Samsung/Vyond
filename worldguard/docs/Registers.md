# 1. WorldGuard-Aware Rocket (WG-Aware Rocket)
### CSRs added by WG-Aware Rocket
|CSR | Address| 
|-|-|
|mlwid | 0x390|
|mweideleg|0x748|
|slwid|0x190|


# 2. WorldGuard Markers (WGM)

## WGMs in Rocket SoC
|Base Address| Device to Mark | Description|
|--|--|--|
|WG_MARKER_ROCKET_BASE|0x210_0000| WG Marker for a RocketTile|

## Registers in WGM
|offset| register | description|
|--|--|--|
|0x00| VENDOR | Vendoer ID|
|0x04| IMPID| Implementation ID|
|0x08| WID | wid value|
|0x0c| LCOK_VALID| lock and valid|

### VENDOR
|Bits|Field Name| Attribute | Reset | Description|
|--|--|--|--|--|
|[31:0]| VendorField |RO|0x0|Vendor ID|

### IMIPID
|Bits|Field Name| Attribute | Reset | Description|
|--|--|--|--|--|
|[15:0]| Implementation Field |RO|0x0| Implementation ID|

### WID
|Bits|Field Name| Attribute | Reset | Description|
|--|--|--|--|--|
|[log2Ceil(NWorlds)-1:0]| WID Field|RW|0x0|World ID|

### LOCK_VALID
|Bits|Field Name| Attribute | Reset | Description|
|--|--|--|--|--|
|[0]|lock |RS|0x0|Lock bit. If set, WID cannot be changed until system reset.|
|[1]|valid |RW|0x1|Valid bit. If set, WGM marks incomming transactions. If clear, WGM blocks transactions. Not implemented yet.|
|[7:2]|Reserved ||||

# 3. WorldGuard Checker (WGC)

## WGCs in Rocket SoC
|Base Address| Device to Mark | Description|
|--|--|--|
|WGC_PERIPHERY_BASE|0x203_0000| WGC for an Example Periphery|
|WGC_PLIC_BASE|0x204_0000| WGC for PLIC|
|WGC_BOOTROM_BASE|0x205_0000| WGC for BootROM|
|WGC_MEMORY_BASE|0x206_0000| WGC for DRAM|


## Registers in WGC
|offset| register | description|
|--|--|--|
|0x00| VENDOR | Vendoer ID|
|0x04| IMPID| Implementation ID|
|0x08| NSLOTS | Number of slots  |
|0x10|ERROR_CAUSE_SLOT_WID_RW|WID that causes invalid access|
|0x14|ERROR_CAUSE_SLOT_BE_IP|Indicates type(s) of event triggered (Bus Error or Interrupt) caused by invalid access|
|0x18|ERROR_ADDR|Address tried to access|
|0x20|SLOT_0_ADDRESS|Slot 0 Address|
|0x28|SLOT_0_PERM|Slot 0 Permission bitmap|
|0x30|SLOT_0_CONFIG|Slot 0 Configuration|
|0x40|SLOT_1_ADDRESS|Slot 1 Address|
|0x48|SLOT_1_PERM|Slot 1 Permission bitmap|
|0x50|SLOT_1_CONFIG|Slot 1 Configuration|


### VENDOR
|Bits|Field Name| Attribute | Reset | Description|
|--|--|--|--|--|
|[31:0]| VendorField |RO|0x0|Vendor ID|

### IMIPID
|Bits|Field Name| Attribute | Reset | Description|
|--|--|--|--|--|
|[15:0]| Implementation Field |RO|0x0| Implementation ID|

### NSLOTS
|Bits|Field Name| Attribute | Reset | Description|
|--|--|--|--|--|
|[31:0]| NSLOT |RO|0x1| Number of Slots in WGC|


### ERROR_CAUSE_SLOT_WID_RW
|Bits|Field Name| Attribute | Reset | Description|
|--|--|--|--|--|
|[1:0]| WID |RO|0x0| |
|[7:2]| Reserved ||| |
|[9:8]| Access Type |RO|0x0| |
|[15:10]| Reserved ||| |

### ERROR_CAUSE_SLOT_BE_IP
|Bits|Field Name| Attribute | Reset | Description|
|--|--|--|--|--|
|[29:0]| Reserved ||| |
|[30]| Bus Error Generated |RW|0x0| |
|[31]| Interrupt Generated |RW|0x0| |

### ERROR_ADDRESS
|Bits|Field Name| Attribute | Reset | Description|
|--|--|--|--|--|
|[63:0]| Accessed Address |RO|0x0| |

### SLOT_N_ADDRESS - Base Address + 0x20 X (N+1)
|Bits|Field Name| Attribute | Reset | Description|
|--|--|--|--|--|
|[paddrBits-lgAlign:0]| Address |RW|0x0| Rule Address |


### SLOT_N_PERM - Base Address + 0x20 X (N+1) + 0x8
|Bits|Field Name| Attribute | Reset | Description|
|--|--|--|--|--|
|[63:0]| PERM |RW|0x0| Bitmap of WIDs |

### SLOT_N_CONFIG - Base Address + 0x20 X (N+1) + 010
|Bits|Field Name| Attribute | Reset | Description|
|--|--|--|--|--|
|[1:0]| A |RW|0x0|  |
|[7:2]| Reserved |||  |
|[8]| ER |RW|0x0|  |
|[9]| EW |RW|0x0|  |
|[10]| IR |RW|0x0|  |
|[11]| IW |RW|0x0|  |
|[30:12]| Reserved |||  |
|[31]| LOCK |RS|0x0|  |


