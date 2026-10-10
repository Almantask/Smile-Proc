import XCTest

final class CheckInUITests: XCTestCase {
    func testTapOkayShowsInHistory() {
        let app = XCUIApplication()
        app.launchArguments = ["-uiTesting"]
        app.launch()

        let okay = app.buttons["Log okay"]
        XCTAssertTrue(okay.waitForExistence(timeout: 8))
        okay.tap()

        let done = app.buttons["Done"]
        if done.waitForExistence(timeout: 4) {
            done.tap()
        }

        let button = app.buttons["All entries"]
        let link = app.links["All entries"]
        let allEntries: XCUIElement
        if button.waitForExistence(timeout: 4) {
            allEntries = button
        } else {
            allEntries = link
        }
        XCTAssertTrue(allEntries.waitForExistence(timeout: 2))
        allEntries.tap()

        XCTAssertTrue(app.staticTexts["Okay"].waitForExistence(timeout: 4))
    }
}
