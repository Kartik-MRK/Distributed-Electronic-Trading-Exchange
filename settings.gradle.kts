rootProject.name = "dete"

// Shared libraries
include(
    "libs:common-domain",
    "libs:common-events",
    "libs:common-security",
    "libs:common-test"
)

// Services
include(
    "services:gateway",
    "services:auth",
    "services:order",
    "services:risk",
    "services:account",
    "services:matching-engine",
    "services:market-data",
    "services:audit",
    "services:simulator"
)
