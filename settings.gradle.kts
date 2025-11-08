val name = providers.gradleProperty("name")
rootProject.name = name.get()

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    val yumiGradleLicenserVersion = providers.gradleProperty("yumi_gradle_licenser_version")
    val nmcpVersion = providers.gradleProperty("nmcp_version")
    val cpdVersion = providers.gradleProperty("cpd_version")
    val enforcerGradleVersion = providers.gradleProperty("enforcer_gradle_version")
    plugins {
        id("dev.yumi.gradle.licenser").version(yumiGradleLicenserVersion.get())
        id("com.gradleup.nmcp").version(nmcpVersion.get())
        id("de.aaschmid.cpd").version(cpdVersion.get())
        id("org.kordamp.gradle.project-enforcer").version(enforcerGradleVersion.get())
    }
}
