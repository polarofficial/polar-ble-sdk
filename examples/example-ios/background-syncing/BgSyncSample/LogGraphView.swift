// Copyright © 2026 Polar Electro Oy. All rights reserved.

import SwiftUI

fileprivate let ZOOM_LEVELS: [Double] = [
    1.0 / 60,    // 1 second
    5.0 / 60,    // 5 seconds
    10.0 / 60,   // 10 seconds
    0.5,         // 30 seconds
    1,           // 1 minute
    2,           // 2 minutes
    5,           // 5 minutes
    15,          // 15 minutes
    30,          // 30 minutes
    60,          // 1 hour
    120,         // 2 hours
    360,         // 6 hours
    720,         // 12 hours
    1440,        // 24 hours
    2880,        // 48 hours
    10080,       // 1 week
    43200,       // 1 month (30 days)
    525600,      // 1 year (365 days)
]

fileprivate func zoomLabel(_ minutes: Double) -> String {
    let tolerance = 0.0001
    if abs(minutes - 1.0/60) < tolerance { return "1s" }
    if abs(minutes - 5.0/60) < tolerance { return "5s" }
    if abs(minutes - 10.0/60) < tolerance { return "10s" }
    
    switch minutes {
    case 0.5: return "30s"
    case 1: return "1m"
    case 2: return "2m"
    case 5: return "5m"
    case 15: return "15m"
    case 30: return "30m"
    case 60: return "1h"
    case 120: return "2h"
    case 360: return "6h"
    case 720: return "12h"
    case 1440: return "24h"
    case 2880: return "48h"
    case 10080: return "1w"
    case 43200: return "1mo"
    case 525600: return "1y"
    default: return String(format: "%.1fm", minutes)
    }
}

// Tracks the horizontal scroll offset (content minX in the scroll coordinate space)
private struct HScrollOffsetKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

// Where to anchor the view after a zoom-level change
private enum ZoomAnchorMode { case start, center, end }

fileprivate let syncStatusColor = Color(red: 126.0/255.0, green: 87.0/255.0, blue: 194.0/255.0)
fileprivate let cleanupStatusColor = Color(red: 66.0/255.0, green: 165.0/255.0, blue: 245.0/255.0)
fileprivate let recordingBarColor = Color(red: 1.0, green: 167.0/255.0, blue: 38.0/255.0)

fileprivate func normalizedDataType(_ dataType: String) -> String {
    switch dataType.uppercased() {
    case "HR_SAMPLES_247", "OFFLINE_HR": return "HR"
    case "PPI_SAMPLES_247", "OFFLINE_PPI": return "PPI"
    case "OFFLINE_TEMPERATURE": return "TEMPERATURE"
    case "SKIN_TEMPERATURE", "OFFLINE_SKIN_TEMPERATURE": return "SKIN_TEMPERATURE"
    case "OFFLINE_ACC": return "ACC"
    case "OFFLINE_DERIVED_ACC": return "DERIVED_ACC"
    case "OFFLINE_ECG": return "ECG"
    case "OFFLINE_GYRO": return "GYRO"
    case "OFFLINE_MAGNETOMETER": return "MAGNETOMETER"
    case "OFFLINE_PPG": return "PPG"
    case "TRAINING": return "TRAINING_SESSIONS"
    default: return dataType.uppercased()
    }
}

fileprivate func typeColor(_ dataType: String) -> Color {
    switch normalizedDataType(dataType) {
    case "STEPS": return Color(red: 102.0/255.0, green: 187.0/255.0, blue: 106.0/255.0)
    case "DISTANCE": return Color(red: 38.0/255.0, green: 166.0/255.0, blue: 154.0/255.0)
    case "ACTIVE_TIME": return Color(red: 156.0/255.0, green: 204.0/255.0, blue: 101.0/255.0)
    case "CALORIES": return Color(red: 1.0, green: 112.0/255.0, blue: 67.0/255.0)
    case "HR": return Color(red: 239.0/255.0, green: 83.0/255.0, blue: 80.0/255.0)
    case "PPI": return Color(red: 1.0, green: 202.0/255.0, blue: 40.0/255.0)
    case "NIGHTLY_RECHARGE": return Color(red: 92.0/255.0, green: 107.0/255.0, blue: 192.0/255.0)
    case "ACTIVITY_SAMPLES": return Color(red: 141.0/255.0, green: 110.0/255.0, blue: 99.0/255.0)
    case "DAILY_SUMMARY": return Color(red: 41.0/255.0, green: 182.0/255.0, blue: 246.0/255.0)
    case "SLEEP": return Color(red: 171.0/255.0, green: 71.0/255.0, blue: 188.0/255.0)
    case "TRAINING_SESSIONS": return Color(red: 0.0, green: 137.0/255.0, blue: 123.0/255.0)
    case "SKIN_TEMPERATURE": return Color(red: 236.0/255.0, green: 64.0/255.0, blue: 122.0/255.0)
    case "TEMPERATURE": return Color(red: 1.0, green: 167.0/255.0, blue: 38.0/255.0)
    case "ACC": return Color(red: 66.0/255.0, green: 165.0/255.0, blue: 245.0/255.0)
    case "DERIVED_ACC": return Color(red: 30.0/255.0, green: 136.0/255.0, blue: 229.0/255.0)
    case "ECG": return Color(red: 142.0/255.0, green: 36.0/255.0, blue: 170.0/255.0)
    case "GYRO": return Color(red: 212.0/255.0, green: 225.0/255.0, blue: 87.0/255.0)
    case "MAGNETOMETER": return Color(red: 161.0/255.0, green: 136.0/255.0, blue: 127.0/255.0)
    case "PPG": return Color(red: 0.0, green: 172.0/255.0, blue: 193.0/255.0)
    default: return Color(red: 176.0/255.0, green: 190.0/255.0, blue: 197.0/255.0)
    }
}

fileprivate func chargingMarkerIsCharging(_ state: String) -> Bool? {
    switch state.uppercased() {
    case "CHARGING", "FULL", "TRUE": return true
    case "UNPLUGGED", "FALSE", "DISCHARGING_ACTIVE", "DISCHARGING_INACTIVE": return false
    default: return false
    }
}

fileprivate enum BatteryMarkerState {
    case charging
    case discharging
    case unknown
}

fileprivate struct BatteryMarkerDetail: Identifiable {
    let id = UUID()
    let source: String
    let batteryPercent: Int
    let deltaPercent: Int?
    let chargingState: String
}

fileprivate struct BatteryMarkerHit {
    let rect: CGRect
    let state: BatteryMarkerState
    let color: Color
    let detail: BatteryMarkerDetail
}

fileprivate func markerState(_ state: String) -> BatteryMarkerState {
    switch state.uppercased() {
    case "CHARGING", "FULL", "TRUE": return .charging
    case "UNPLUGGED", "FALSE", "DISCHARGING_ACTIVE", "DISCHARGING_INACTIVE", "DISCHARGINGACTIVE", "DISCHARGINGINACTIVE", "NOT_CHARGING": return .discharging
    default: return .unknown
    }
}

// Data class for selected bar info (matching Android)
fileprivate struct BarDetail: Identifiable {
    let id = UUID()
    let type: String  // "SYNC", "ACTIVITY", "RECORDING", "RECORDING_TYPE", "CLEANUP"
    let dataType: String  // e.g. "STEPS", "SLEEP", empty for sync/cleanup
    let deviceId: String
    let startTime: Date
    let endTime: Date
    let durationMs: Int  // Duration in milliseconds
    let transferredBytes: Int64?
}

/// Graph view for sync logs, showing battery levels, sync phases, and cleanup phases.
/// Displays as a timeline with Y-axis for battery percentage (0-100%) and X-axis for time.
struct LogGraphView: View {
    var graphData: LogGraphData
    let allDevices: [String]
    let loadGraphData: (String) -> LogGraphData  // Closure to reload graph for a device
    @Environment(\.dismiss) var dismiss
    
    @State private var selectedDevice: String
    @State private var showDeviceSelector = false
    @State private var currentGraphData: LogGraphData
    @State private var zoomIndex: Int = ZOOM_LEVELS.count  // Start at maximum
    
    init(graphData: LogGraphData, allDevices: [String], loadGraphData: @escaping (String) -> LogGraphData) {
        self.graphData = graphData
        self.allDevices = allDevices
        self.loadGraphData = loadGraphData
        _selectedDevice = State(initialValue: graphData.deviceId)
        _currentGraphData = State(initialValue: graphData)
    }
    
    var body: some View {
        ZStack {
            Color(UIColor(red: 0.1, green: 0.1, blue: 0.1, alpha: 1.0))
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Compact single-line header with back, title, device ID, and Change button
                HStack(spacing: 8) {
                    // Back button
                    Button(action: { dismiss() }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 16, weight: .semibold))
                            .frame(width: 44, height: 44, alignment: .center)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    
                    Text("Sync Log Graph - \(selectedDevice)")
                        .font(.system(.headline, design: .default))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                    
                    Spacer()
                    
                    // Show Change button only if multiple devices
                    if allDevices.count > 1 {
                        Button(action: { showDeviceSelector = true }) {
                            Text("Change")
                                .font(.system(.body, design: .default))
                                .fontWeight(.medium)
                                .foregroundColor(.blue)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 8)
                .background(Color(.systemGray6))
                
                // Main graph area - uses all freed space
                if currentGraphData.batteryReadings.isEmpty &&
                   currentGraphData.syncPhases.isEmpty &&
                   currentGraphData.cleanupPhases.isEmpty {
                    VStack {
                        Spacer()
                        Text("No data to display")
                            .font(.body)
                            .foregroundColor(.gray)
                        Spacer()
                    }
                } else {
                    TimelineGraphView(graphData: currentGraphData, zoomIndex: $zoomIndex)
                        .frame(maxHeight: .infinity)
                }
            }
        }
        .sheet(isPresented: $showDeviceSelector) {
            DeviceSelectorSheet(devices: allDevices, selectedDevice: $selectedDevice)
        }
        .onChange(of: selectedDevice) { newDevice in
            currentGraphData = loadGraphData(newDevice)
        }
        .navigationBarBackButtonHidden(true)
        .preferredColorScheme(nil)
        .onAppear {
            AppDelegate.orientationLock = .landscapeRight
            UIDevice.current.setValue(UIInterfaceOrientation.landscapeRight.rawValue, forKey: "orientation")
            UIViewController.attemptRotationToDeviceOrientation()
        }
        .onDisappear {
            AppDelegate.orientationLock = .portrait
            UIDevice.current.setValue(UIInterfaceOrientation.portrait.rawValue, forKey: "orientation")
            UIViewController.attemptRotationToDeviceOrientation()
        }
    }
}

// Device selector sheet modal
private struct DeviceSelectorSheet: View {
    let devices: [String]
    @Binding var selectedDevice: String
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationView {
            List {
                ForEach(devices, id: \.self) { deviceId in
                    Button(action: {
                        selectedDevice = deviceId
                        dismiss()
                    }) {
                        HStack {
                            Text(deviceId)
                                .foregroundColor(.primary)
                            Spacer()
                            if deviceId == selectedDevice {
                                Image(systemName: "checkmark")
                                    .foregroundColor(.blue)
                                    .fontWeight(.bold)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Select Device")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
    }
}

private struct TimelineGraphView: View {
    let graphData: LogGraphData
    @Binding var zoomIndex: Int  // Bound zoom index for controls in left column

    @State private var centerFraction: CGFloat = 1.0  // Center time preserved across zoom
    @State private var isZooming = false               // Freeze center tracking during a zoom change
    @State private var zoomAnchorMode: ZoomAnchorMode = .end  // Anchor edge/center after zoom
    @State private var scrollOffset: CGFloat = 0       // Track current scroll position in pixels
    @State private var selectedBar: BarDetail?         // Track which bar was tapped
    @State private var selectedBatteryMarker: BatteryMarkerDetail?

    private let yAxisWidth: CGFloat = 65
    private let barRowsHeight: CGFloat = 100
    private let timeAxisHeight: CGFloat = 40
    private let batteryHeaderHeight: CGFloat = 40
    private let minPixelsPerMinute: CGFloat = 6

    var timeRange: (min: Date, max: Date)? {
        var timestamps: [Date] = []
        // Use all phase times (sync/cleanup/activity/recording), not battery readings which may span hours
        timestamps.append(contentsOf: graphData.syncPhases.flatMap { [$0.startTime, $0.endTime] })
        timestamps.append(contentsOf: graphData.activityTypePhases.flatMap { [$0.startTime, $0.endTime] })
        timestamps.append(contentsOf: graphData.recordingPhases.flatMap { [$0.startTime, $0.endTime] })
        timestamps.append(contentsOf: graphData.recordingTypePhases.flatMap { [$0.startTime, $0.endTime] })
        timestamps.append(contentsOf: graphData.cleanupPhases.flatMap { [$0.startTime, $0.endTime] })
        guard let min = timestamps.min(), let max = timestamps.max() else { return nil }
        return (min, max)
    }

    // Always use full data range, regardless of zoom
    // Zoom affects viewport detail level, not what time span is covered.
    // Extend the end by 10 seconds so trailing timeline is visible after the last stage.
    private func displayTimeRange(dataMin: Date, dataMax: Date) -> (min: Date, max: Date) {
        return (dataMin, dataMax.addingTimeInterval(10))
    }

    private func totalMinutes(_ a: Date, _ b: Date) -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: a, to: b)
        return max(5, (c.hour ?? 0) * 60 + (c.minute ?? 0))
    }

    private func detailTimeString(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss.SSS"
        return formatter.string(from: date)
    }

    private func formatBytes(_ bytes: Int64) -> String {
        if bytes < 1024 { return "\(bytes) B" }
        if bytes < 1024 * 1024 { return String(format: "%.1f KB", Double(bytes) / 1024.0) }
        return String(format: "%.1f MB", Double(bytes) / (1024.0 * 1024.0))
    }

    var body: some View {
        Group {
            if let (dataMin, dataMax) = timeRange {
                let (minTime, maxTime) = displayTimeRange(dataMin: dataMin, dataMax: dataMax)
                let totalMinutes = totalMinutes(minTime, maxTime)

            // Dynamic MAX: largest zoom level still more zoomed-in than the data span;
            // one step beyond that is MAX (fit all data). Zooming out stops at MAX.
            let maxUsefulIndex = ZOOM_LEVELS.lastIndex(where: { $0 < Double(totalMinutes) }) ?? -1
            let maxZoomIndex = maxUsefulIndex + 1

            GeometryReader { geo in
                let batteryHeight = max(80, geo.size.height - barRowsHeight - timeAxisHeight - batteryHeaderHeight)
                let visibleWidth = geo.size.width - yAxisWidth
                // Zoom-in cap: keep content width within a safe limit so extremely wide
                // content can't exhaust memory / rendering limits on long sessions.
                let maxContentWidth: CGFloat = 1000000
                let minAllowedZoomMinutes = Double(totalMinutes) * Double(visibleWidth) / Double(maxContentWidth)
                let minZoomIndexFromConstraint = ZOOM_LEVELS.firstIndex(where: { $0 >= minAllowedZoomMinutes }) ?? (ZOOM_LEVELS.count - 1)
                // Hard minimum: always allow zooming to at least 1 second (first zoom level in our array)
                let minZoomIndex = min(maxZoomIndex, max(0, minZoomIndexFromConstraint))
                let effIndex = min(max(zoomIndex, minZoomIndex), maxZoomIndex)
                let zoomMinutes: Double? = effIndex <= maxUsefulIndex ? ZOOM_LEVELS[effIndex] : nil
                // zoomMinutes = number of minutes that should fill the visible width.
                // MAX mode (nil) fits ALL data into a single screen width. Width is hard-capped.
                let graphWidth: CGFloat = {
                    if let zoomMinutes = zoomMinutes {
                        let pixelsPerMinute = visibleWidth / CGFloat(zoomMinutes)
                        return min(maxContentWidth, max(visibleWidth, CGFloat(totalMinutes) * pixelsPerMinute))
                    } else {
                        return visibleWidth
                    }
                }()

                // Fraction of the content to center on after a zoom, based on the captured anchor.
                // start  -> viewport leading at time 0; end -> viewport trailing at the last time;
                // center -> keep the same centered time centered.
                let anchorFraction: CGFloat = {
                    guard graphWidth > 0 else { return 1 }
                    let halfFrac = (visibleWidth / 2) / graphWidth
                    switch zoomAnchorMode {
                    case .start:  return min(1, halfFrac)
                    case .end:    return max(0, 1 - halfFrac)
                    case .center: return centerFraction
                    }
                }()

                HStack(spacing: 0) {
                    // ── Fixed Y-axis column (does not scroll) ─────────────
                    VStack(spacing: 0) {
                        // Sync stage row labels (aligned with the five bar rows)
                        VStack(alignment: .trailing, spacing: 0) {
                            ForEach(["Sync", "Activity", "Offline Recording", "Measurement", "Cleanup"], id: \.self) { lbl in
                                Text(lbl)
                                    .font(.system(size: 9))
                                    .foregroundColor(.white.opacity(0.7))
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.7)
                                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
                                    .padding(.trailing, 4)
                            }
                        }
                        .frame(width: yAxisWidth, height: barRowsHeight)
                        .background(Color(UIColor(red: 0.15, green: 0.15, blue: 0.15, alpha: 1.0)))

                        // Battery levels heading + color legend
                        VStack(alignment: .leading, spacing: 1) {
                            Text("Battery levels")
                                .font(.system(size: 9, weight: .semibold))
                                .foregroundColor(.white)
                                .lineLimit(1)
                            
                            HStack(spacing: 4) {
                                HStack(spacing: 2) {
                                    Circle().fill(Color.red).frame(width: 6, height: 6)
                                    Text("Phone")
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundColor(.red)
                                        .lineLimit(1)
                                }
                                .frame(height: 16)
                                
                                HStack(spacing: 2) {
                                    Circle().fill(Color(red: 0, green: 0.867, blue: 0)).frame(width: 6, height: 6)
                                    Text("Device")
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundColor(Color(red: 0, green: 0.867, blue: 0))
                                        .lineLimit(1)
                                }
                                .frame(height: 16)
                            }
                            .frame(height: 16)
                        }
                        .padding(.horizontal, 2)
                        .padding(.vertical, 1)
                        .background(Color(UIColor(red: 0.1, green: 0.1, blue: 0.1, alpha: 1.0)))

                        // Battery percentage scale
                        VStack(alignment: .trailing, spacing: 0) {
                            ForEach((0...5).reversed(), id: \.self) { idx in
                                Text("\(idx * 20)%")
                                    .font(.system(size: 8))
                                    .foregroundColor(.white)
                                    .frame(maxHeight: .infinity)
                                    .padding(.trailing, 4)
                            }
                        }
                        .frame(width: yAxisWidth, height: batteryHeight)
                        .background(Color(UIColor(red: 0.1, green: 0.1, blue: 0.1, alpha: 1.0)))

                        // Zoom controls (horizontal, on the time-axis line)
                        HStack(spacing: 2) {
                            Button(action: { isZooming = true; zoomIndex = max(minZoomIndex, effIndex - 1) }) {
                                Image(systemName: "plus.circle.fill")
                                    .font(.system(size: 16))
                                    .foregroundColor(.blue)
                            }
                            .buttonStyle(.plain)

                            Text(effIndex <= maxUsefulIndex ? zoomLabel(ZOOM_LEVELS[effIndex]) : "MAX")
                                .font(.system(size: 8, weight: .semibold))
                                .foregroundColor(.white)
                                .lineLimit(1)
                                .minimumScaleFactor(0.6)
                                .frame(width: 30)

                            Button(action: { isZooming = true; zoomIndex = min(maxZoomIndex, effIndex + 1) }) {
                                Image(systemName: "minus.circle.fill")
                                    .font(.system(size: 16))
                                    .foregroundColor(.blue)
                            }
                            .buttonStyle(.plain)
                        }
                        .frame(width: yAxisWidth, height: timeAxisHeight)
                        .background(Color(UIColor(red: 0.15, green: 0.15, blue: 0.15, alpha: 1.0)))
                    }

                    // ── Single ScrollView containing all graph rows ──
                    ScrollViewReader { proxy in
                        ScrollView(.horizontal, showsIndicators: true) {
                            VStack(spacing: 0) {
                                BarRowsView(graphData: graphData,
                                            minTime: minTime, maxTime: maxTime,
                                            graphWidth: graphWidth,
                                            visibleWidth: visibleWidth, scrollOffset: scrollOffset,
                                            onBarTapped: { bar in
                                                selectedBar = bar
                                            })
                                .frame(width: graphWidth, height: barRowsHeight)
                                .background(Color(UIColor(red: 0.15, green: 0.15, blue: 0.15, alpha: 1.0)))

                                // Battery header spacer (matches left-column heading row)
                                Color(UIColor(red: 0.1, green: 0.1, blue: 0.1, alpha: 1.0))
                                    .frame(width: graphWidth, height: batteryHeaderHeight)

                                BatteryCanvasView(graphData: graphData,
                                                  minTime: minTime, maxTime: maxTime,
                                                  nMinutes: totalMinutes,
                                                  graphWidth: graphWidth,
                                                  graphHeight: batteryHeight,
                                                  onMarkerTapped: { marker in
                                                      selectedBatteryMarker = marker
                                                  })
                                .frame(width: graphWidth, height: batteryHeight)
                                .background(Color(UIColor(red: 0.1, green: 0.1, blue: 0.1, alpha: 1.0)))

                                TimeAxisView(minTime: minTime, maxTime: maxTime,
                                             nMinutes: totalMinutes,
                                             graphWidth: graphWidth,
                                             visibleWidth: visibleWidth)
                                .frame(width: graphWidth, height: timeAxisHeight)
                                .background(Color(UIColor(red: 0.15, green: 0.15, blue: 0.15, alpha: 1.0)))

                                // Single position anchor for zoom (start / center / end via fraction)
                                HStack(spacing: 0) {
                                    Color.clear.frame(width: max(0, min(graphWidth, anchorFraction * graphWidth)), height: 1)
                                    Color.clear.frame(width: 1, height: 1).id("zoom-anchor")
                                    Spacer(minLength: 0)
                                }
                                .frame(width: graphWidth, height: 1)

                                // Anchor at trailing edge for initial scroll-to-end
                                Color.clear.frame(width: 1, height: 1).id("graph-end")
                            }
                            .background(
                                GeometryReader { g in
                                    Color.clear.preference(
                                        key: HScrollOffsetKey.self,
                                        value: -g.frame(in: .named("scrollArea")).minX
                                    )
                                }
                            )
                        }
                        .coordinateSpace(name: "scrollArea")
                        .onPreferenceChange(HScrollOffsetKey.self) { minX in
                            // Track the current scroll offset for use in bar clipping
                            scrollOffset = max(0, minX)
                            // Track the anchor (start / center / end) only from user scrolling (not during a zoom change)
                            if !isZooming, graphWidth > 0 {
                                let maxOffset = max(0, graphWidth - visibleWidth)
                                let atStart = scrollOffset <= 2
                                let atEnd = scrollOffset >= maxOffset - 2
                                zoomAnchorMode = atEnd ? .end : (atStart ? .start : .center)
                                let centerX = scrollOffset + visibleWidth / 2
                                centerFraction = max(0, min(1, centerX / graphWidth))
                            }
                        }
                        .onAppear {
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                                proxy.scrollTo("graph-end", anchor: .trailing)
                            }
                        }
                        .onChange(of: zoomIndex) { _ in
                            // Restore the captured position after the zoom-level (width) change.
                            // A single anchor placed at the target fraction is centered in the viewport.
                            DispatchQueue.main.async {
                                proxy.scrollTo("zoom-anchor", anchor: .center)
                            }
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                                proxy.scrollTo("zoom-anchor", anchor: .center)
                            }
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                                isZooming = false
                            }
                        }
                    }
                }
            }
        } else {
            Text("No data available")
                .foregroundColor(.white)
        }
        }
        .sheet(item: $selectedBar) { bar in
            VStack(spacing: 0) {
                HStack {
                    Text("Bar Details - \(bar.type)")
                        .font(.headline)
                    Spacer()
                    Button(action: { selectedBar = nil }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.gray)
                    }
                }
                .padding()
                .background(Color(UIColor(red: 0.1, green: 0.1, blue: 0.1, alpha: 1.0)))

                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        DetailRowView(label: "Device ID", value: bar.deviceId)
                        DetailRowView(label: "Data Type", value: bar.dataType.isEmpty ? "N/A" : bar.dataType)
                        DetailRowView(label: "Start", value: detailTimeString(bar.startTime))
                        DetailRowView(label: "End", value: detailTimeString(bar.endTime))
                        DetailRowView(label: "Duration", value: "\(bar.durationMs)ms")
                        if let bytes = bar.transferredBytes {
                            DetailRowView(label: "Transferred Bytes", value: formatBytes(bytes))
                        }
                        Spacer()
                    }
                    .padding()
                }
                .background(Color(UIColor(red: 0.15, green: 0.15, blue: 0.15, alpha: 1.0)))

                Button(action: { selectedBar = nil }) {
                    Text("Close")
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
                .padding()
            }
            .background(Color(UIColor(red: 0.1, green: 0.1, blue: 0.1, alpha: 1.0)))
        }
        .alert(item: $selectedBatteryMarker) { marker in
            Alert(
                title: Text("Battery Marker - \(marker.source)"),
                message: Text("Battery: \(marker.batteryPercent)%\nDelta: \(marker.deltaPercent.map { $0 >= 0 ? "+\($0)%" : "\($0)%" } ?? "N/A")\nState: \(marker.chargingState.uppercased())"),
                dismissButton: .default(Text("Close"))
            )
        }
    }
}

// Detail row composable for bar details sheet (matching Android)
fileprivate struct DetailRowView: View {
    let label: String
    let value: String
    
    var body: some View {
        HStack {
            Text(label)
                .font(.body)
                .foregroundColor(.gray)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(value)
                .font(.body)
                .fontWeight(.semibold)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(.vertical, 4)
    }
}

fileprivate struct BarHitBox {
    let rect: CGRect
    let color: Color
    let barDetail: BarDetail
}

private struct BarRowsView: View {
    let graphData: LogGraphData
    let minTime: Date
    let maxTime: Date
    let graphWidth: CGFloat
    let visibleWidth: CGFloat
    let scrollOffset: CGFloat
    var onBarTapped: (BarDetail) -> Void = { _ in }

    private func makeBarRect(
        startSec: TimeInterval,
        endSec: TimeInterval,
        rowY: CGFloat,
        rowHeight: CGFloat,
        totalSeconds: TimeInterval
    ) -> CGRect? {
        guard endSec > 0, startSec < totalSeconds else { return nil }
        let clampedStart = max(0, startSec)
        let clampedEnd = min(totalSeconds, endSec)
        guard clampedEnd > clampedStart else { return nil }
        let x = CGFloat(clampedStart / totalSeconds) * graphWidth
        let width = max(1, CGFloat((clampedEnd - clampedStart) / totalSeconds) * graphWidth)
        return CGRect(x: x, y: rowY, width: width, height: rowHeight)
    }

    private func makeHitBoxes(size: CGSize) -> [BarHitBox] {
        let totalSeconds = max(1, maxTime.timeIntervalSince(minTime))

        let rowHeight = size.height / 5
        let barVerticalGap: CGFloat = 3
        let barHeight = max(1, rowHeight - barVerticalGap)
        let barYOffset = barVerticalGap / 2
        let rowY: [CGFloat] = [
            barYOffset,
            rowHeight + barYOffset,
            rowHeight * 2 + barYOffset,
            rowHeight * 3 + barYOffset,
            rowHeight * 4 + barYOffset
        ]
        let rowColors: [Color] = [
            syncStatusColor,
            Color(red: 77.0/255.0, green: 182.0/255.0, blue: 172.0/255.0),
            recordingBarColor,
            Color(red: 0.0, green: 172.0/255.0, blue: 193.0/255.0),
            cleanupStatusColor
        ]

        var hitBoxes: [BarHitBox] = []

        for phase in graphData.syncPhases {
            let startSec = phase.startTime.timeIntervalSince(minTime)
            let endSec = phase.endTime.timeIntervalSince(minTime)
            guard let rect = makeBarRect(
                startSec: startSec,
                endSec: endSec,
                rowY: rowY[0],
                rowHeight: barHeight,
                totalSeconds: totalSeconds
            ) else { continue }
            let durationMs = Int((phase.endTime.timeIntervalSince(phase.startTime)) * 1000)
            hitBoxes.append(BarHitBox(
                rect: rect,
                color: rowColors[0],
                barDetail: BarDetail(type: "SYNC", dataType: "", deviceId: phase.deviceId, startTime: phase.startTime, endTime: phase.endTime, durationMs: durationMs, transferredBytes: phase.transferredBytes)
            ))
        }

        for phase in graphData.activityTypePhases where phase.category == "ACTIVITY" {
            let startSec = phase.startTime.timeIntervalSince(minTime)
            let endSec = phase.endTime.timeIntervalSince(minTime)
            guard let rect = makeBarRect(
                startSec: startSec,
                endSec: endSec,
                rowY: rowY[1],
                rowHeight: barHeight,
                totalSeconds: totalSeconds
            ) else { continue }
            let durationMs = Int((phase.endTime.timeIntervalSince(phase.startTime)) * 1000)
            hitBoxes.append(BarHitBox(
                rect: rect,
                color: typeColor(phase.dataType),
                barDetail: BarDetail(type: "ACTIVITY", dataType: phase.dataType, deviceId: phase.deviceId, startTime: phase.startTime, endTime: phase.endTime, durationMs: durationMs, transferredBytes: phase.transferredBytes)
            ))
        }

        for phase in graphData.recordingPhases {
            let startSec = phase.startTime.timeIntervalSince(minTime)
            let endSec = phase.endTime.timeIntervalSince(minTime)
            guard let rect = makeBarRect(
                startSec: startSec,
                endSec: endSec,
                rowY: rowY[2],
                rowHeight: barHeight,
                totalSeconds: totalSeconds
            ) else { continue }
            let durationMs = Int((phase.endTime.timeIntervalSince(phase.startTime)) * 1000)
            hitBoxes.append(BarHitBox(
                rect: rect,
                color: recordingBarColor,
                barDetail: BarDetail(type: "Offline Recording", dataType: "", deviceId: phase.deviceId, startTime: phase.startTime, endTime: phase.endTime, durationMs: durationMs, transferredBytes: phase.transferredBytes)
            ))
        }

        for phase in graphData.recordingTypePhases where phase.category == "OFFLINE" {
            let startSec = phase.startTime.timeIntervalSince(minTime)
            let endSec = phase.endTime.timeIntervalSince(minTime)
            guard let rect = makeBarRect(
                startSec: startSec,
                endSec: endSec,
                rowY: rowY[3],
                rowHeight: barHeight,
                totalSeconds: totalSeconds
            ) else { continue }
            let durationMs = Int((phase.endTime.timeIntervalSince(phase.startTime)) * 1000)
            hitBoxes.append(BarHitBox(
                rect: rect,
                color: typeColor(phase.dataType),
                barDetail: BarDetail(type: "Measurement", dataType: phase.dataType, deviceId: phase.deviceId, startTime: phase.startTime, endTime: phase.endTime, durationMs: durationMs, transferredBytes: phase.transferredBytes)
            ))
        }

        for phase in graphData.cleanupPhases {
            let startSec = phase.startTime.timeIntervalSince(minTime)
            let endSec = phase.endTime.timeIntervalSince(minTime)
            guard let rect = makeBarRect(
                startSec: startSec,
                endSec: endSec,
                rowY: rowY[4],
                rowHeight: barHeight,
                totalSeconds: totalSeconds
            ) else { continue }
            let durationMs = Int((phase.endTime.timeIntervalSince(phase.startTime)) * 1000)
            hitBoxes.append(BarHitBox(
                rect: rect,
                color: rowColors[4],
                barDetail: BarDetail(type: "CLEANUP", dataType: "", deviceId: phase.deviceId, startTime: phase.startTime, endTime: phase.endTime, durationMs: durationMs, transferredBytes: phase.transferredBytes)
            ))
        }

        return hitBoxes
    }

    var body: some View {
        GeometryReader { geometry in
            let hitBoxes = makeHitBoxes(size: geometry.size)
            let rowHeight = geometry.size.height / 5
            let rowColors: [Color] = [
                syncStatusColor,
                Color(red: 77.0/255.0, green: 182.0/255.0, blue: 172.0/255.0),
                recordingBarColor,
                Color(red: 0.0, green: 172.0/255.0, blue: 193.0/255.0),
                cleanupStatusColor
            ]

            Canvas { context, _ in
                for index in 0..<5 {
                    context.fill(
                        Path(CGRect(x: 0, y: CGFloat(index) * rowHeight, width: graphWidth, height: rowHeight)),
                        with: .color(rowColors[index].opacity(0.1))
                    )
                }

                for hitBox in hitBoxes {
                    context.fill(Path(roundedRect: hitBox.rect, cornerRadius: 2), with: .color(hitBox.color))
                }
            }
            .contentShape(Rectangle())
            .onTapGesture { tapPoint in
                guard let hitBox = hitBoxes.first(where: { $0.rect.contains(tapPoint) }) else { return }
                onBarTapped(hitBox.barDetail)
            }
        }
    }
}

private struct BatteryCanvasView: View {
    let graphData: LogGraphData
    let minTime: Date
    let maxTime: Date
    let nMinutes: Int
    let graphWidth: CGFloat
    let graphHeight: CGFloat
    let onMarkerTapped: (BatteryMarkerDetail) -> Void

    private func markerHalf(for state: BatteryMarkerState) -> CGFloat {
        state == .unknown ? 2 : 4
    }

    private func markerY(percent: Int, height: CGFloat, markerHalf: CGFloat) -> CGFloat {
        let raw = height * CGFloat(100 - percent) / 100
        return min(max(markerHalf, raw), height - markerHalf)
    }

    private func buildMarkers(totalSeconds: TimeInterval, height: CGFloat) -> [BatteryMarkerHit] {
        var markers: [BatteryMarkerHit] = []

        for (index, r) in graphData.batteryReadings.enumerated() {
            let x = CGFloat(r.timestamp.timeIntervalSince(minTime) / totalSeconds) * graphWidth
            let state = markerState(r.phoneChargingState)
            let half = markerHalf(for: state)
            let y = markerY(percent: r.phoneBatteryPercent, height: height, markerHalf: half)
            let delta = index > 0 ? r.phoneBatteryPercent - graphData.batteryReadings[index - 1].phoneBatteryPercent : nil
            let markerRect = CGRect(x: x - half, y: y - half, width: half * 2, height: half * 2)
            markers.append(BatteryMarkerHit(
                rect: markerRect,
                state: state,
                color: .red,
                detail: BatteryMarkerDetail(
                    source: "Phone",
                    batteryPercent: r.phoneBatteryPercent,
                    deltaPercent: delta,
                    chargingState: r.phoneChargingState
                )
            ))
        }

        var previousDevicePercent: Int?
        for r in graphData.batteryReadings {
            guard let devicePercent = r.deviceBatteryPercent else { continue }
            let x = CGFloat(r.timestamp.timeIntervalSince(minTime) / totalSeconds) * graphWidth
            let state = markerState(r.deviceChargingState)
            let half = markerHalf(for: state)
            let y = markerY(percent: devicePercent, height: height, markerHalf: half)
            let delta = previousDevicePercent.map { devicePercent - $0 }
            previousDevicePercent = devicePercent
            let markerRect = CGRect(x: x - half, y: y - half, width: half * 2, height: half * 2)
            markers.append(BatteryMarkerHit(
                rect: markerRect,
                state: state,
                color: .green,
                detail: BatteryMarkerDetail(
                    source: "Device",
                    batteryPercent: devicePercent,
                    deltaPercent: delta,
                    chargingState: r.deviceChargingState
                )
            ))
        }

        return markers
    }

    var body: some View {
        GeometryReader { geo in
            let totalSeconds = max(1, maxTime.timeIntervalSince(minTime))
            let markers = buildMarkers(totalSeconds: totalSeconds, height: geo.size.height)

            Canvas { context, size in
                func xFor(_ t: Date) -> CGFloat {
                    CGFloat(t.timeIntervalSince(minTime) / totalSeconds) * graphWidth
                }
                let h = size.height

                // Horizontal grid lines (every 20%)
                for pct in stride(from: 0, through: 100, by: 20) {
                    let y = h * CGFloat(pct) / 100
                    var path = Path()
                    path.move(to: CGPoint(x: 0, y: y))
                    path.addLine(to: CGPoint(x: graphWidth, y: y))
                    context.stroke(path, with: .color(.gray.opacity(0.2)), lineWidth: 0.5)
                }

                // Vertical grid lines (bounded count)
                let gridStride = max(5, nMinutes / 500)
                for m in stride(from: 0, through: nMinutes, by: gridStride) {
                    let x = CGFloat(Double(m * 60) / totalSeconds) * graphWidth
                    var path = Path()
                    path.move(to: CGPoint(x: x, y: 0))
                    path.addLine(to: CGPoint(x: x, y: h))
                    context.stroke(path, with: .color(.gray.opacity(0.1)), lineWidth: 0.3)
                }

                // Phone battery (red)
                if graphData.batteryReadings.count > 1 {
                    var path = Path(); var first = true
                    for r in graphData.batteryReadings {
                        let x = xFor(r.timestamp)
                        let y = h * CGFloat(100 - r.phoneBatteryPercent) / 100
                        if first { path.move(to: .init(x: x, y: y)); first = false }
                        else { path.addLine(to: .init(x: x, y: y)) }
                    }
                    context.stroke(path, with: .color(.red), lineWidth: 2)
                }

                // Device battery (green)
                let dev = graphData.batteryReadings.filter { $0.deviceBatteryPercent != nil }
                if dev.count > 1 {
                    var path = Path(); var first = true
                    for r in dev {
                        let x = xFor(r.timestamp)
                        let y = h * CGFloat(100 - (r.deviceBatteryPercent ?? 0)) / 100
                        if first { path.move(to: .init(x: x, y: y)); first = false }
                        else { path.addLine(to: .init(x: x, y: y)) }
                    }
                    context.stroke(path, with: .color(.green), lineWidth: 2)
                }

                // Markers
                for marker in markers {
                    let markerPath = Path(marker.rect)
                    switch marker.state {
                    case .charging:
                        context.fill(markerPath, with: .color(marker.color))
                    case .discharging:
                        context.stroke(markerPath, with: .color(marker.color), lineWidth: 2)
                    case .unknown:
                        context.fill(markerPath, with: .color(marker.color))
                    }
                }
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onEnded { value in
                        if let hit = markers.last(where: { $0.rect.insetBy(dx: -6, dy: -6).contains(value.location) }) {
                            onMarkerTapped(hit.detail)
                        }
                    }
            )
        }
    }
}

private struct TimeAxisView: View {
    let minTime: Date
    let maxTime: Date
    let nMinutes: Int
    let graphWidth: CGFloat
    let visibleWidth: CGFloat

    var body: some View {
        let totalSeconds = max(1, maxTime.timeIntervalSince(minTime))
        let visibleSeconds = max(1, totalSeconds * Double(visibleWidth / max(graphWidth, 1)))
        let targetLabelCount = 6.0
        let minPixelSpacing = 70.0
        let minStepByCountSec = visibleSeconds / targetLabelCount
        let minStepBySpacingSec = visibleSeconds * Double(minPixelSpacing / max(visibleWidth, 1))
        let requiredStepSec = max(1.0, max(minStepByCountSec, minStepBySpacingSec))
        let niceSeconds: [Double] = [1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600, 7200, 10800, 21600, 43200, 86400]
        let niceStep = niceSeconds.first(where: { $0 >= requiredStepSec }) ?? 86400
        // Hard cap total tick/label count to keep memory/rendering bounded on huge timelines.
        let maxTickCount = 1200.0
        let capStep = totalSeconds / maxTickCount
        let stepSec = max(niceStep, capStep)
        let showSeconds = stepSec < 60
        let firstTick = 0.0
        let lastTick = ceil(totalSeconds / stepSec) * stepSec
        let fmtTime = DateFormatter()
        fmtTime.dateFormat = showSeconds ? "HH:mm:ss" : "HH:mm"
        let fmtDate = DateFormatter()
        fmtDate.dateFormat = "yyyy-MM-dd"

        return Canvas { context, size in
            var tick = firstTick
            while tick <= lastTick {
                let x = CGFloat(tick / totalSeconds) * graphWidth
                var path = Path()
                path.move(to: CGPoint(x: x, y: 0))
                path.addLine(to: CGPoint(x: x, y: 4))
                context.stroke(path, with: .color(.white.opacity(0.4)), lineWidth: 0.8)

                let t = minTime.addingTimeInterval(tick)
                let dateText = Text(fmtDate.string(from: t))
                    .font(.system(size: 8, weight: .regular))
                    .foregroundColor(.white.opacity(0.85))
                let timeText = Text(fmtTime.string(from: t))
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(.white.opacity(0.95))
                context.draw(dateText, at: CGPoint(x: x + 2, y: 2), anchor: .topLeading)
                context.draw(timeText, at: CGPoint(x: x + 2, y: 14), anchor: .topLeading)

                tick += stepSec
            }
        }
        .frame(width: graphWidth)
    }
}

#Preview {
    LogGraphView(graphData: LogGraphData(
        deviceId: "TEST123",
        batteryReadings: [],
        syncPhases: [],
        activityTypePhases: [],
        recordingPhases: [],
        recordingTypePhases: [],
        cleanupPhases: []
    ), allDevices: ["TEST123"], loadGraphData: { _ in
        LogGraphData(
            deviceId: "TEST123",
            batteryReadings: [],
            syncPhases: [],
            activityTypePhases: [],
            recordingPhases: [],
            recordingTypePhases: [],
            cleanupPhases: []
        )
    })
}
