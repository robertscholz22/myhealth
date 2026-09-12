package com.myhealth

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Enforces the layering contract of PLAN §1.2 / rule R6 by reading the sources, not the
 * bytecode — a source scan catches a violation even when the offending file would still compile.
 *
 * `user.dir` is the Gradle module directory (`app/`) for unit tests, but a run from the project
 * root is just as plausible, so both are resolved.
 */
class ArchitectureTest {

    @Test
    fun domain_never_imports_android_androidx_or_other_layers() {
        val offenders = kotlinFilesUnder("domain").flatMap { file ->
            file.readLines()
                .filter { FORBIDDEN_IN_DOMAIN.containsMatchIn(it) }
                .map { "${file.name}: ${it.trim()}" }
        }

        assertThat(offenders).isEmpty()
    }

    @Test
    fun ui_never_imports_the_data_layer() {
        val offenders = kotlinFilesUnder("ui").flatMap { file ->
            file.readLines()
                .filter { it.trimStart().startsWith("import com.myhealth.data.") }
                .map { "${file.name}: ${it.trim()}" }
        }

        assertThat(offenders).isEmpty()
    }

    @Test
    fun garmin_client_is_reachable_only_from_di_and_its_own_package() {
        val offenders = kotlinFilesUnder(".")
            .filterNot { it.isUnder("di") || it.isUnder("data/garmin") }
            .flatMap { file ->
                file.readLines()
                    .filter { it.trimStart().startsWith("import com.myhealth.data.garmin") }
                    .map { "${file.relativeTo(sourceRoot).path}: ${it.trim()}" }
            }

        assertThat(offenders).isEmpty()
    }

    @Test
    fun the_scan_actually_sees_the_sources() {
        // Guards the three tests above against silently passing on an empty file list.
        assertThat(kotlinFilesUnder("domain").size).isAtLeast(10)
        assertThat(kotlinFilesUnder("ui")).isNotEmpty()
    }

    private fun File.isUnder(relativePath: String): Boolean =
        relativeTo(sourceRoot).invariantPath.startsWith("$relativePath/")

    private fun kotlinFilesUnder(relativePath: String): List<File> {
        val root = if (relativePath == ".") sourceRoot else File(sourceRoot, relativePath)
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private val File.invariantPath: String get() = path.replace(File.separatorChar, '/')

    private companion object {
        val FORBIDDEN_IN_DOMAIN =
            Regex("""^import (android|androidx|kotlinx\.coroutines\.android|com\.myhealth\.(data|ui|di))\.""")

        /** `app/src/main/java/com/myhealth`, from either the module dir or the project root. */
        val sourceRoot: File = run {
            val userDir = File(System.getProperty("user.dir") ?: ".").absoluteFile
            val suffix = "src/main/java/com/myhealth"
            listOf(File(userDir, suffix), File(userDir, "app/$suffix"), File(userDir.parentFile, "app/$suffix"))
                .firstOrNull { it.isDirectory }
                ?: error("Could not locate $suffix from user.dir=$userDir")
        }
    }
}
