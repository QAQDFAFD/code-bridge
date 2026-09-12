// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "CodeBridgeMac",
    platforms: [
        .macOS(.v14)
    ],
    products: [
        .executable(name: "CodeBridgeMac", targets: ["CodeBridgeMac"]),
        .library(name: "CodeBridgeCore", targets: ["CodeBridgeCore"])
    ],
    targets: [
        .target(name: "CodeBridgeCore"),
        .executableTarget(
            name: "CodeBridgeMac",
            dependencies: ["CodeBridgeCore"]
        ),
        .testTarget(
            name: "CodeBridgeCoreTests",
            dependencies: ["CodeBridgeCore"]
        )
    ]
)

