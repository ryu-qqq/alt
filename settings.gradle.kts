plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "alt"

include(
    "domain",
    "application",
    "adapter-in",
    "adapter-out:persistence-mysql",
    "adapter-out:persistence-redis",
    "adapter-out:client-csrng",
    "adapter-out:client-llm",
    "bootstrap",
)

project(":adapter-out:persistence-mysql").projectDir = file("adapter-out/persistence-mysql")
project(":adapter-out:persistence-redis").projectDir = file("adapter-out/persistence-redis")
project(":adapter-out:client-csrng").projectDir = file("adapter-out/client-csrng")
project(":adapter-out:client-llm").projectDir = file("adapter-out/client-llm")
