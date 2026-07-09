plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    mainClass.set("io.effects.samples.ecommerce.EcommerceApp")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
}
