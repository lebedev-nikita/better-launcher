# Better Launcher

<!-- impeccable:product-schema 1 -->

## Platform
android

## Stack
Delegated by the user. Kotlin and native Android Views, Material 3 settings, Gradle Kotlin DSL. Android 12+ (API 31), explicitly selected by the user.

## Product Purpose
An Android home-screen launcher. The initial direction explicitly reproduces Niagara Launcher's interface and workflows.

## Capabilities and Constraints
Launch installed apps, display favorites and an alphabetical app list, search, manage favorites, show notifications and app pop-ups, customize appearance, and host widgets. Request optional system access only when its corresponding feature is enabled. Actual installed apps and system data must supply the content.

## Brand Commitments
The reference is Niagara Launcher. The repository's product name is Better Launcher. Use original source and assets under that name.

## Open Decisions
The user requested a complete Niagara copy. The current implementation covers core workflows; remaining parity gaps are recorded in docs/features.md. Tablet support and store distribution are unspecified. Initial verification targets phones; do not claim full Niagara feature parity until those gaps are closed and verified.
