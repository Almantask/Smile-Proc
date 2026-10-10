// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "SmileCore",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: [
        .library(name: "SmileCore", targets: ["SmileCore"]),
    ],
    dependencies: [
        .package(url: "https://github.com/groue/GRDB.swift", from: "7.11.1"),
    ],
    targets: [
        .target(
            name: "CCommonCrypto",
            path: "Sources/CCommonCrypto",
            publicHeadersPath: "include"
        ),
        .target(
            name: "SmileCore",
            dependencies: [
                "CCommonCrypto",
                .product(name: "GRDB", package: "GRDB.swift"),
            ]
        ),
        .testTarget(
            name: "SmileCoreTests",
            dependencies: ["SmileCore"]
        ),
    ]
)
