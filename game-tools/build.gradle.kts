plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin { jvmToolchain(17) }

application { mainClass.set("com.thevaguebox.picpac.tools.MainKt") }

dependencies {
    implementation(project(":game-core"))
    implementation(project(":game-ai"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
