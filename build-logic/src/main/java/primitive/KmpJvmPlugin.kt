package primitive
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/** Kotlin Multiplatform の JVM（Compose Desktop）target を設定する。 */
// Gradle が plugin descriptor を通じてこの実装をインスタンス化する。
@Suppress("unused")
class KmpJvmPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            kotlin {
                jvm {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_21)
                    }
                }
            }
        }
    }
}
