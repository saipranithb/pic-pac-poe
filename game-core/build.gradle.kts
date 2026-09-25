plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(libs.junit)
    testFixturesApi("com.google.code.gson:gson:2.10.1")
}
