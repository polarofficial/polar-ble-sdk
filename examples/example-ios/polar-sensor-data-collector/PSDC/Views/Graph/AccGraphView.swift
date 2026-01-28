//  Copyright © 2026 Polar. All rights reserved.

import SwiftUI

private enum AccConstants {
    static let maxSamples = 3000

    static let leftPadding: CGFloat = 70
    static let graphPadding: CGFloat = 16
    static let headerPadding: CGFloat = 8
    static let verticalPadding: CGFloat = 10

    static let gridLineWidth: CGFloat = 1
    static let lineWidth: CGFloat = 1.5

    static let gridLines = 6
    static let labelTextSize: CGFloat = 12

    static let graphBackground = Color.black
    static let gridColor = Color.gray.opacity(0.3)
    static let textColor = Color.white
    static let buttonRed = Color(red: 0.83, green: 0.18, blue: 0.18)

    static let xColor = Color.cyan
    static let yColor = Color.green
    static let zColor = Color(red: 1, green: 0, blue: 1) // magenta
}

struct AccGraphView: View {
    @ObservedObject private var dataHolder = AccDataHolder.shared
    let onClose: () -> Void

    @State private var lastMaxAbs: Int = 1000

    private var samples: [AccSample] {
        dataHolder.accState.accSamples
    }

    private var xValues: [Int] { samples.map { $0.x } }
    private var yValues: [Int] { samples.map { $0.y } }
    private var zValues: [Int] { samples.map { $0.z } }

    private var symmetricRange: Int {
        calculateSymmetricRange(values: xValues + yValues + zValues)
    }

    var body: some View {
        ZStack {
            AccConstants.graphBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                headerView

                AccPlotterCanvas(
                    xValues: xValues,
                    yValues: yValues,
                    zValues: zValues,
                    maxAbsValue: symmetricRange
                )
                .padding(AccConstants.graphPadding)
            }
        }
    }

    private var headerView: some View {
        HStack {
            if let last = samples.last {
                Text("X: \(last.x)  Y: \(last.y)  Z: \(last.z)")
                    .foregroundColor(AccConstants.textColor)
                    .font(.system(size: 16))
            } else {
                Text("X: --  Y: --  Z: --")
                    .foregroundColor(AccConstants.textColor)
                    .font(.system(size: 16))
            }

            Spacer()

            Button("Close", action: onClose)
                .foregroundColor(AccConstants.textColor)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(AccConstants.buttonRed)
                .cornerRadius(4)
        }
        .padding(AccConstants.headerPadding)
    }

    private func calculateSymmetricRange(values: [Int]) -> Int {
        let visible = values.suffix(AccConstants.maxSamples)
        guard let maxAbs = visible.map({ abs($0) }).max() else {
            return lastMaxAbs
        }

        if maxAbs <= lastMaxAbs {
            return lastMaxAbs
        }

        let step: Int
        switch maxAbs {
        case 0..<1000: step = 1000
        case 1000..<2000: step = 2000
        case 2000..<5000: step = 5000
        case 5000..<10000: step = 10000
        case 10000..<20000: step = 20000
        default: step = 50000
        }

        let expanded = Int(ceil(Double(maxAbs) / Double(step))) * step
        lastMaxAbs = expanded
        return expanded
    }
}

private struct GraphContext {
    let leftPadding: CGFloat
    let graphWidth: CGFloat
    let usableHeight: CGFloat
    let topPadding: CGFloat
    let minVal: Float
    let range: Float
    let stepX: CGFloat
}

private struct AccPlotterCanvas: View {
    let xValues: [Int]
    let yValues: [Int]
    let zValues: [Int]
    let maxAbsValue: Int

    var body: some View {
        Canvas { context, size in
            let graphWidth = size.width - AccConstants.leftPadding
            let usableHeight = size.height - AccConstants.verticalPadding * 2

            let minVal = -Float(maxAbsValue)
            let range = Float(maxAbsValue * 2)

            let stepX = graphWidth / CGFloat(max(AccConstants.maxSamples - 1, 1))

            let gc = GraphContext(
                leftPadding: AccConstants.leftPadding,
                graphWidth: graphWidth,
                usableHeight: usableHeight,
                topPadding: AccConstants.verticalPadding,
                minVal: minVal,
                range: range,
                stepX: stepX
            )

            drawGrid(context: &context, gc: gc, size: size)

            drawAxis(context: &context, gc: gc, values: xValues, color: AccConstants.xColor)
            drawAxis(context: &context, gc: gc, values: yValues, color: AccConstants.yColor)
            drawAxis(context: &context, gc: gc, values: zValues, color: AccConstants.zColor)
        }
    }

    private func drawGrid(
        context: inout GraphicsContext,
        gc: GraphContext,
        size: CGSize
    ) {
        for i in 0...AccConstants.gridLines {
            let value = gc.minVal + Float(i) * (gc.range / Float(AccConstants.gridLines))
            let y = gc.usableHeight
                - CGFloat((value - gc.minVal) / gc.range) * gc.usableHeight
                + gc.topPadding

            var grid = Path()
            grid.move(to: CGPoint(x: gc.leftPadding, y: y))
            grid.addLine(to: CGPoint(x: size.width, y: y))
            context.stroke(grid, with: .color(AccConstants.gridColor), lineWidth: AccConstants.gridLineWidth)

            let label = Text("\(Int(value))")
                .font(.system(size: AccConstants.labelTextSize))
                .foregroundColor(AccConstants.textColor)

            context.draw(label, at: CGPoint(x: gc.leftPadding - 8, y: y), anchor: .trailing)
        }
    }

    private func drawAxis(
        context: inout GraphicsContext,
        gc: GraphContext,
        values: [Int],
        color: Color
    ) {
        guard !values.isEmpty else { return }

        let startIndex = max(values.count - AccConstants.maxSamples, 0)
        let visible = values[startIndex...]

        var path = Path()

        for (i, v) in visible.enumerated() {
            let x = gc.leftPadding + CGFloat(i) * gc.stepX
            let y = gc.usableHeight
                - CGFloat((Float(v) - gc.minVal) / gc.range) * gc.usableHeight
                + gc.topPadding

            if i == 0 {
                path.move(to: CGPoint(x: x, y: y))
            } else {
                path.addLine(to: CGPoint(x: x, y: y))
            }
        }

        context.stroke(path, with: .color(color), lineWidth: AccConstants.lineWidth)
    }
}