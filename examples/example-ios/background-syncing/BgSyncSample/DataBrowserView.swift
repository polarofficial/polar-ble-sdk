// Copyright © 2026 Polar Electro Oy. All rights reserved.

import SwiftUI

struct DataBrowserView: View {
    let deviceIds: [String]
    @StateObject private var manager = SyncManager.shared
    @State private var selectedDevice: String = ""
    @State private var selectedType: SyncableDataType = .steps
    @State private var selectedEntry: SyncedDataEntry?

    var body: some View {
        VStack(spacing: 0) {
            if deviceIds.count > 1 {
                devicePicker
            }
            dataTypePicker
            entryList
        }
        .navigationTitle("Data Browser")
        .sheet(item: $selectedEntry) { entry in
            DataDetailView(entry: entry)
        }
        .onAppear {
            if selectedDevice.isEmpty {
                selectedDevice = deviceIds.first ?? ""
            }
        }
    }

    private var devicePicker: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(deviceIds, id: \.self) { deviceId in
                    Button(deviceId) {
                        selectedDevice = deviceId
                    }
                    .buttonStyle(.bordered)
                    .tint(selectedDevice == deviceId ? .blue : .gray)
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
        }
    }

    private var dataTypePicker: some View {
        let device = selectedDevice.isEmpty ? (deviceIds.first ?? "") : selectedDevice
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(SyncableDataType.allCases, id: \.self) { type in
                    let count = device.isEmpty ? 0 : manager.listEntries(deviceId: device, dataType: type).count
                    Button {
                        selectedType = type
                    } label: {
                        VStack(spacing: 2) {
                            Text(type.rawValue)
                            Text("(\(count))")
                                .font(.system(size: 9))
                                .foregroundColor(.secondary)
                        }
                    }
                    .buttonStyle(.bordered)
                    .tint(selectedType == type ? .polarRed : .gray)
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
        }
    }

    private var entryList: some View {
        let device = selectedDevice.isEmpty ? (deviceIds.first ?? "") : selectedDevice
        let entries = device.isEmpty ? [] : manager.listEntries(deviceId: device, dataType: selectedType)
            .sorted { $0.date > $1.date }
        return Group {
            if entries.isEmpty {
                VStack {
                    Spacer()
                    Text("No \(selectedType.rawValue) data")
                        .foregroundColor(.secondary)
                    Spacer()
                }
            } else {
                List(entries) { entry in
                    Button {
                        selectedEntry = entry
                    } label: {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(Self.dateFormatter.string(from: entry.date))
                                    .font(.body)
                                Text("\(entry.sizeBytes) bytes")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .foregroundColor(.secondary)
                        }
                    }
                    .foregroundColor(.primary)
                }
            }
        }
    }

    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateStyle = .medium
        return f
    }()
}

struct DataDetailView: View {
    let entry: SyncedDataEntry
    @StateObject private var manager = SyncManager.shared
    @State private var fullContent: String? = nil
    @State private var displayContent: String? = nil
    @State private var totalSampleCount: Int = 0
    @State private var isTruncated = false
    @State private var isLoading = true

    private static let maxDisplaySamples = 500

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    LabeledContent("Type", value: entry.dataType.rawValue)
                    LabeledContent("Date", value: Self.dateFormatter.string(from: entry.date))
                    LabeledContent("Size", value: "\(entry.sizeBytes) bytes")

                    Divider()

                    if isLoading {
                        HStack {
                            Spacer()
                            VStack(spacing: 8) {
                                ProgressView()
                                Text("Loading JSON…")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                        }
                        .padding(.top, 40)
                    } else {
                        if isTruncated {
                            HStack(spacing: 6) {
                                Image(systemName: "info.circle")
                                    .foregroundColor(.orange)
                                Text("Showing first \(Self.maxDisplaySamples) of \(totalSampleCount) samples. Use share to export full data.")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            .padding(.vertical, 4)
                        }

                        Text("JSON Content")
                            .font(.headline)

                        if let content = displayContent {
                            Text(content)
                                .font(.system(.caption, design: .monospaced))
                                .textSelection(.enabled)
                        } else {
                            Text("Failed to read file")
                                .foregroundColor(.red)
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Detail")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    if let content = fullContent {
                        ShareLink(
                            item: content,
                            subject: Text(entry.dataType.rawValue),
                            message: Text(Self.dateFormatter.string(from: entry.date))
                        ) {
                            Image(systemName: "square.and.arrow.up")
                        }
                    }
                }
            }
            .task {
                await loadContent()
            }
        }
    }

    private func loadContent() async {
        let content = await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                continuation.resume(returning: manager.readEntry(entry: entry))
            }
        }
        let result = Self.truncateForDisplay(content, maxSamples: Self.maxDisplaySamples)
        fullContent = content
        displayContent = result.display
        totalSampleCount = result.totalSamples
        isTruncated = result.truncated
        isLoading = false
    }

    /// Parse the JSON, truncate the `samples` array to `maxSamples` entries, and
    /// re-serialise with pretty-printing. Returns the original string unchanged when
    /// the sample count is within the limit or the JSON has no `samples` key.
    private static func truncateForDisplay(
        _ jsonStr: String?,
        maxSamples: Int
    ) -> (display: String?, totalSamples: Int, truncated: Bool) {
        guard let jsonStr else { return (nil, 0, false) }
        guard let data = jsonStr.data(using: .utf8),
              var dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let samples = dict["samples"] as? [Any],
              samples.count > maxSamples else {
            return (jsonStr, 0, false)
        }
        let totalCount = samples.count
        dict["samples"] = Array(samples.prefix(maxSamples))
        dict["_truncatedSamples"] = true
        dict["_totalSamplesCount"] = totalCount
        guard let truncatedData = try? JSONSerialization.data(withJSONObject: dict, options: .prettyPrinted),
              let truncatedStr = String(data: truncatedData, encoding: .utf8) else {
            return (jsonStr, 0, false)
        }
        return (truncatedStr, totalCount, true)
    }

    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateStyle = .long
        return f
    }()
}
