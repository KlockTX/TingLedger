import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 签名信息放在 keystore/keystore.properties（本地机密，勿提交）。
// 文件不存在时 release 退化为未签名产物，不至于让没拿到密钥的机器构建失败。
val keystorePropsFile = rootProject.file("keystore/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val signingAvailable = keystorePropsFile.exists()

android {
    namespace = "com.klocktx.moneyblocks"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.klocktx.moneyblocks"
        minSdk = 26
        targetSdk = 36
        versionCode = 38
        versionName = "1.25.0"
    }

    signingConfigs {
        create("release") {
            if (signingAvailable) {
                storeFile = rootProject.file("keystore/${keystoreProps["storeFile"]}")
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
            // v2 + v3 即为完整配置：minSdk 26 意味着最低是 Android 8.0，
            // 系统安装器只校验 v2/v3，v1 (JAR signing) 只服务 Android 7.0 以下，AGP 也不会产出
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    testOptions {
        unitTests {
            // Robolectric 要读合并后的 manifest 与资源，否则 createComposeRule 起不来
            isIncludeAndroidResources = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 没有密钥时仍产出未签名包，方便在别人机器上验证混淆结果
            if (signingAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.reorderable)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Robolectric + Compose：在 JVM 里真的组合一次界面。
    // 这次的账单页闪退就是「只编译不组合」漏掉的——组合期越界，编译器看不见。
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// 组合冒烟测试要靠 ui-test-manifest 里注册的 ComponentActivity 才跑得起来，
// 而那个依赖只能挂 debug：挂到 release 会把测试 Activity 打进正式包。
// 于是 release 变体的单测必然报 "Unable to resolve activity"——那是环境缺件，不是界面有问题。
// 不排掉的话这道闸长期是红的，而长期红的闸等于没有闸。真正的组合检查由 testDebugUnitTest 把。
tasks.withType<Test>().matching { it.name == "testReleaseUnitTest" }.configureEach {
    filter {
        excludeTestsMatching("com.klocktx.moneyblocks.ui.ScreenSmokeTest")
        excludeTestsMatching("com.klocktx.moneyblocks.ui.bill.BillEditorDialogSmokeTest")
        excludeTestsMatching("com.klocktx.moneyblocks.ui.keypad.RecordSheetHeightTest")
        excludeTestsMatching("com.klocktx.moneyblocks.ui.settings.SettingsScreenSmokeTest")
    }
}

// Robolectric 会给每个测试类建真资源与位图，而组合冒烟用的是 411dp×3200dp 的大屏
// （为了让 LazyColumn 视口外的行也组合进来）。测试数涨到 150 条之后默认堆开始不够，
// 表现是 OutOfMemoryError 和"Compose 永远不空闲"混着出现——那是内存不够，不是界面有问题。
tasks.withType<Test>().configureEach {
    maxHeapSize = "3g"
}
