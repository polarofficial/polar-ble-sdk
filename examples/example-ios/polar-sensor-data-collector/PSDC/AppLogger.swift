/// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation

/// Centralized logger used throughout the app.
///
/// Every call to `AppLogger.log(_:)` writes the message to the system console
/// (visible in Xcode / Console.app, same as `NSLog` used to) *and* appends a
/// timestamped line to a persistent log file on disk. That file is what gets
/// shared when the user taps "Export PSDC app logs".
///
/// Previously most of the app logged with plain `NSLog(...)`, which never
/// touched the exported log file — only the small subset of messages that
/// happened to be routed through `PolarBleApiLogger.message(_:)` were written
/// to disk. As a result the exported log usually contained only the initial
/// header line and no actual entries. Routing all logging through this type
/// fixes that.
enum AppLogger {
    private static let logFileName = "PSDCAppLogs.txt"
    private static let logHeader = "iOS PSDC app logs:\n"
    private static let queue = DispatchQueue(label: "com.polar.psdc.appLogger")
    private static var fileHandle: FileHandle?

    private static let dateFormatter: DateFormatter = {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd HH:mm:ss.SSS"
        df.timeZone = TimeZone(identifier: "UTC")
        return df
    }()

    /// URL of the persistent app log file inside the app's Documents directory.
    static var logFileURL: URL? {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first?
            .appendingPathComponent(logFileName)
    }

    /// Ensures the log file exists (creating it with the standard header if needed)
    /// and returns its URL. Safe to call multiple times.
    @discardableResult
    static func ensureLogFile() -> URL? {
        guard let url = logFileURL else { return nil }
        if !FileManager.default.fileExists(atPath: url.path) {
            do {
                try logHeader.write(to: url, atomically: true, encoding: .utf8)
            } catch {
                NSLog("AppLogger: failed to create log file at \(url): \(error.localizedDescription)")
                return nil
            }
        }
        return url
    }

    /// Logs `message` to the console and appends it (with a UTC timestamp) to the
    /// persistent app log file that is shared via "Export PSDC app logs".
    static func log(_ message: String, file: String = #fileID, function: String = #function, line: Int = #line) {
        NSLog("%@", message)
        let sourceTag = "[\((file as NSString).lastPathComponent):\(line)]"
        queue.async {
            appendToFile("\(message) \(sourceTag)")
        }
    }

    /// Returns the full contents of the log file, creating it first if necessary.
    static func currentLogFileContents() -> String? {
        guard let url = ensureLogFile() else { return nil }
        return try? String(contentsOf: url, encoding: .utf8)
    }

    private static func appendToFile(_ line: String) {
        guard let url = ensureLogFile() else { return }

        if fileHandle == nil {
            fileHandle = try? FileHandle(forWritingTo: url)
        }
        guard let handle = fileHandle else { return }

        let timestamp = dateFormatter.string(from: Date())
        guard let data = "\(timestamp) \(line)\n".data(using: .utf8) else { return }

        handle.seekToEndOfFile()
        handle.write(data)
    }
}

