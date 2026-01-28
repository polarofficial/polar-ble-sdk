//  Copyright © 2026 Polar. All rights reserved.

import SwiftUI

private enum Constants {
    // Y-Axis HR limits
    static let absoluteMinHr = 40
    static let absoluteMaxHr = 220
    static let minDisplayRange = 40
    static let yAxisSnapInterval = 10
    static let yAxisPadding = 5
    static let yAxisGridStep = 10
    static let defaultMinHr = 60
    static let defaultMaxHr = 100

    // Sample buffer
    static let maxHrSamples = 300

    // UI dimensions
    static let leftPadding: CGFloat = 50
    static let graphPadding: CGFloat = 16
    static let headerPadding: CGFloat = 8
    static let hrLineStrokeWidth: CGFloat = 2
    static let hrPointRadius: CGFloat = 6
    static let gridLineStrokeWidth: CGFloat = 1
    static let yLabelXOffset: CGFloat = 10
    static let yLabelYOffset: CGFloat = 5

    // Text sizes
    static let hrTextSize: CGFloat = 24
    static let labelTextSize: CGFloat = 12

    // Colors
    static let buttonRed = Color(red: 0.83, green: 0.18, blue: 0.18)
    static let graphBackground = Color.black
    static let hrLineColor = Color.red
    static let gridColor = Color.gray.opacity(0.3)
    static let textColor = Color.white
}

struct HrGraphView: View {
    @ObservedObject private var hrDataHolder = HrDataHolder.shared
    let onClose: () -> Void

    private var hrValues: [Int] {
        hrDataHolder.hrState.hrSamples
            .map { $0.hr }
            .filter { $0 > 0 }
    }

    private var displayRange: (min: Int, max: Int) {
        calculateDisplayRange(hrValues: hrValues)
    }

    var body: some View {
        ZStack {
            Constants.graphBackground
                .ignoresSafeArea()

            VStack(spacing: 0) {
                headerView

                HrPlotterCanvas(
                    hrValues: hrValues,
                    displayMinHr: displayRange.min,
                    displayMaxHr: displayRange.max
                )
                .padding(Constants.graphPadding)
            }
        }
    }

    private var headerView: some View {
        HStack {
            Text("HR: \(hrDataHolder.hrState.currentHr) bpm")
                .foregroundColor(Constants.textColor)
                .font(.system(size: Constants.hrTextSize))
            Spacer()
            Button(action: onClose) {
                Text("Close")
                    .foregroundColor(Constants.textColor)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Constants.buttonRed)
                    .cornerRadius(4)
            }
        }
        .padding(Constants.headerPadding)
    }

}

private func floorToInterval(_ value: Int, interval: Int) -> Int {
    (value / interval) * interval
}

private func ceilToInterval(_ value: Int, interval: Int) -> Int {
    ((value + interval - 1) / interval) * interval
}

private func calculateDisplayRange(hrValues: [Int]) -> (min: Int, max: Int) {
    if hrValues.isEmpty {
        return (Constants.defaultMinHr, Constants.defaultMaxHr)
    }

    let actualMin = hrValues.min() ?? Constants.defaultMinHr
    let actualMax = hrValues.max() ?? Constants.defaultMinHr

    var displayMin = max(
        floorToInterval(actualMin - Constants.yAxisPadding, interval: Constants.yAxisSnapInterval),
        Constants.absoluteMinHr
    )
    var displayMax = min(
        ceilToInterval(actualMax + Constants.yAxisPadding, interval: Constants.yAxisSnapInterval),
        Constants.absoluteMaxHr
    )

    let currentRange = displayMax - displayMin
    if currentRange < Constants.minDisplayRange {
        let paddingNeeded = Constants.minDisplayRange - currentRange
        let paddingEachSide = ceilToInterval(paddingNeeded / 2, interval: Constants.yAxisSnapInterval)

        displayMin = max(displayMin - paddingEachSide, Constants.absoluteMinHr)
        displayMax = min(displayMax + paddingEachSide, Constants.absoluteMaxHr)

        let newRange = displayMax - displayMin
        if newRange < Constants.minDisplayRange {
            if displayMin == Constants.absoluteMinHr {
                displayMax = min(
                    floorToInterval(
                        displayMin + Constants.minDisplayRange + Constants.yAxisSnapInterval,
                        interval: Constants.yAxisSnapInterval
                    ),
                    Constants.absoluteMaxHr
                )
            } else {
                displayMin = max(
                    ceilToInterval(
                        displayMax - Constants.minDisplayRange - Constants.yAxisSnapInterval,
                        interval: Constants.yAxisSnapInterval
                    ),
                    Constants.absoluteMinHr
                )
            }
        }
    }

    displayMin = max(
        floorToInterval(displayMin, interval: Constants.yAxisSnapInterval),
        Constants.absoluteMinHr
    )
    displayMax = min(
        ceilToInterval(displayMax, interval: Constants.yAxisSnapInterval),
        Constants.absoluteMaxHr
    )

    return (displayMin, displayMax)
}

struct HrPlotterCanvas: View {
    let hrValues: [Int]
    let displayMinHr: Int
    let displayMaxHr: Int

    var body: some View {
        Canvas { context, size in
            let width = size.width
            let height = size.height
            let leftPadding = Constants.leftPadding
            let graphWidth = width - leftPadding

            let minHr = CGFloat(displayMinHr)
            let maxHr = CGFloat(displayMaxHr)
            let hrRange = max(maxHr - minHr, 1)

            for hr in stride(
                from: displayMinHr,
                through: displayMaxHr,
                by: Constants.yAxisGridStep
            ) {
                let y = height - ((CGFloat(hr) - minHr) / hrRange * height)

                let gridPath = Path { path in
                    path.move(to: CGPoint(x: leftPadding, y: y))
                    path.addLine(to: CGPoint(x: width, y: y))
                }

                context.stroke(
                    gridPath,
                    with: .color(Constants.gridColor),
                    lineWidth: Constants.gridLineStrokeWidth
                )

                let labelText = Text("\(hr)")
                    .font(.system(size: Constants.labelTextSize))
                    .foregroundColor(Constants.textColor)

                context.draw(
                    labelText,
                    at: CGPoint(x: Constants.yLabelXOffset + 10, y: y)
                )
            }

            guard !hrValues.isEmpty else { return }

            let stepX = graphWidth / CGFloat(max(Constants.maxHrSamples - 1, 1))

            if hrValues.count > 1 {
                var linePath = Path()

                for (index, hr) in hrValues.enumerated() {
                    let x = leftPadding + CGFloat(index) * stepX
                    let clampedHr = CGFloat(min(max(hr, displayMinHr), displayMaxHr))
                    let y = height - ((clampedHr - minHr) / hrRange * height)

                    if index == 0 {
                        linePath.move(to: CGPoint(x: x, y: y))
                    } else {
                        linePath.addLine(to: CGPoint(x: x, y: y))
                    }
                }

                context.stroke(
                    linePath,
                    with: .color(Constants.hrLineColor),
                    lineWidth: Constants.hrLineStrokeWidth
                )
            }

            if let lastHr = hrValues.last {
                let lastIndex = hrValues.count - 1
                let x = leftPadding + CGFloat(lastIndex) * stepX
                let clampedHr = CGFloat(min(max(lastHr, displayMinHr), displayMaxHr))
                let y = height - ((clampedHr - minHr) / hrRange * height)

                let circlePath = Path(
                    ellipseIn: CGRect(
                        x: x - Constants.hrPointRadius,
                        y: y - Constants.hrPointRadius,
                        width: Constants.hrPointRadius * 2,
                        height: Constants.hrPointRadius * 2
                    )
                )

                context.fill(circlePath, with: .color(Constants.hrLineColor))
            }
        }
    }
}

struct HrGraphView_Previews: PreviewProvider {
    static var previews: some View {
        HrGraphView(onClose: {})
            .previewInterfaceOrientation(.landscapeLeft)
    }
}
