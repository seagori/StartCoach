import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
}

val sharedDir = layout.buildDirectory.dir("generated/shared")
val licenseAssetsDir = layout.buildDirectory.dir("generated/licenseAssets").get().asFile

/** 루트의 라이선스 문서를 데스크탑 리소스로 복사해 인앱 라이선스 화면에서 읽는다. */
val syncLicenseAssets by tasks.registering(Sync::class) {
    into(File(licenseAssetsDir, "licenses"))
    from(rootProject.file("LICENSE")) { rename { "GPL-3.0.txt" } }
    from(rootProject.file("NOTICE")) { rename { "NOTICE.txt" } }
    from(rootProject.file("THIRD_PARTY_NOTICES.md")) { rename { "THIRD_PARTY_NOTICES.txt" } }
    from(rootProject.file("licenses"))
}

val copyShared by tasks.registering(Sync::class) {
    from("../app/src/main/java") {
        include(
            "com/seagori/hgr/i18n/AppStrings.kt",
            "com/seagori/hgr/ui/theme/Theme.kt",
            "com/seagori/hgr/ui/components/Components.kt",
            "com/seagori/hgr/ui/components/ActivitySection.kt",
            "com/seagori/hgr/domain/Models.kt",
            "com/seagori/hgr/domain/GoalDomains.kt",
            "com/seagori/hgr/domain/ActivityStats.kt",
            "com/seagori/hgr/domain/LlmModels.kt",
            "com/seagori/hgr/domain/SessionStateMachine.kt",
            "com/seagori/hgr/domain/MarkdownChunker.kt",
            "com/seagori/hgr/domain/KeyMask.kt",
            "com/seagori/hgr/domain/UrlRules.kt",
            "com/seagori/hgr/domain/UserErrors.kt",
            "com/seagori/hgr/domain/Labels.kt",
            "com/seagori/hgr/data/io/Streams.kt",
            "com/seagori/hgr/data/backup/BackupCipher.kt",
            "com/seagori/hgr/data/remote/LlmHttp.kt",
            "com/seagori/hgr/data/remote/OpenAiCompatProvider.kt",
            "com/seagori/hgr/sync/SyncModels.kt",
            "com/seagori/hgr/sync/SyncCodec.kt",
            "com/seagori/hgr/sync/SyncJson.kt",
            "com/seagori/hgr/sync/SyncEngine.kt",
            "com/seagori/hgr/sync/SyncCrypto.kt",
            "com/seagori/hgr/sync/SyncTransport.kt",
            "com/seagori/hgr/sync/SyncController.kt",
            "com/seagori/hgr/sync/SyncTransfer.kt",
            "com/seagori/hgr/sync/PairQr.kt",
            "com/seagori/hgr/data/backup/PortableBackup.kt"
        )
    }
    into(sharedDir)
}

sourceSets {
    main {
        kotlin.srcDir(sharedDir.get().asFile)
        resources.srcDir(licenseAssetsDir)
    }
}

tasks.named("compileKotlin") {
    dependsOn(copyShared)
}

tasks.named("processResources") {
    dependsOn(syncLicenseAssets)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.bouncycastle.bcpkix)
    implementation(libs.jmdns)
    implementation(libs.zxing.core)
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.0")
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.seagori.hgr.desktop.MainKt"
        nativeDistributions {
            packageName = "OneStep"
            packageVersion = "1.0.0"
            targetFormats(TargetFormat.Msi)
            windows {
                iconFile.set(project.file("icons/startcoach.ico"))
            }
        }
        buildTypes.release.proguard {
            // 기본 ProGuard 7.2.2는 Java 21 바이트코드를 읽지 못하고,
            // 7.6.1은 OkHttp의 Android 전용 참조를 오류로 처리한다.
            isEnabled.set(false)
        }
    }
}
