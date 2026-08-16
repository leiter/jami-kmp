plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

dependencies {
    implementation(project(":e2e-protocol"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
}

application {
    mainClass.set("net.jami.e2e.MainKt")
}

kotlin {
    jvmToolchain(17)
}

// ==================== End-to-end harness tasks ====================
// Usage:
//   ./gradlew :e2e-runner:e2eList
//   ./gradlew :e2e-runner:e2e -Pscenario=ping -Pdevices=<serial>[,<serial>]
// Exit code 0 = pass. The merged timeline prints regardless.

tasks.register<JavaExec>("e2e") {
    group = "verification"
    description = "Run an end-to-end device scenario (-Pscenario=<id> [-Pdevices=a,b] " +
        "[-PkeepAccounts=true] [-PaccountState=<label>] [-Pusername=<name>])"
    dependsOn(":android-app:installHarnessDebug")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("net.jami.e2e.MainKt")
    doFirst {
        val scenario = project.findProperty("scenario") as String?
            ?: throw GradleException("Missing -Pscenario=<id> (see :e2e-runner:e2eList)")
        val devices = project.findProperty("devices") as String? ?: ""
        val keepAccounts = project.findProperty("keepAccounts") as String? ?: "false"
        val accountState = project.findProperty("accountState") as String? ?: ""
        val username = project.findProperty("username") as String? ?: ""
        args = listOf(scenario, devices, keepAccounts, accountState, username)
    }
}

tasks.register<JavaExec>("e2eList") {
    group = "verification"
    description = "List available end-to-end scenarios"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("net.jami.e2e.MainKt")
    args = listOf("--list")
}

tasks.register<JavaExec>("e2eListAccountStates") {
    group = "verification"
    description = "List named account-state fixtures available for -PaccountState=<label>"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("net.jami.e2e.MainKt")
    args = listOf("--list-account-states")
}
