// Temporary application, test and quality tool dependency overrides.
// Remove each entry when the centrally managed catalog or upstream dependency supplies
// the fixed version or later. Confirm resolved versions without the override using
// dependencies or dependencyInsight on the affected configuration, then run the relevant tests.
// Keep this file comment-only when no overrides are needed.
// Templates: copy only the required override, replace the placeholder version and
// document the reason and removal condition. Retain these comments when removing overrides.
//
// Subproject dependency constraints:
// subprojects {
//     dependencies.constraints {
//         add("<configuration>", "<group>:<module>:<fixed-version>") {
//             because("<advisory reference>; remove when the upstream dependency supplies the fix")
//         }
//     }
// }

subprojects {
    dependencies.constraints {
        add("checkstyle", "com.google.guava:guava:33.7.2-jre") {
            because("CVE-2026-102554 / GHSA-xxph-c9ww-hj94 (Dependabot alert #7); remove when Checkstyle supplies 33.7.2+")
        }
    }
}
