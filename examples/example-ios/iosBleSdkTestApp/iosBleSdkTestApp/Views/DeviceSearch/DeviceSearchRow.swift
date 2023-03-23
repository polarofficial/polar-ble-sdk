/// Copyright © 2023 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct DeviceSearchRow: View {
    var polarDeviceInfo: PolarDeviceInfo
    
    var body: some View {
        HStack {
            HStack(spacing: 0) {
                Image(systemName: "wave.3.right")
                    .foregroundColor(.blue)
            }
            .font(.system(size: 24))
            VStack(alignment: .leading, spacing: 4) {
                Text(polarDeviceInfo.name)
                    .font(.headline)
                    .foregroundColor(.primary)
                
                Text("Device ID: \(polarDeviceInfo.deviceId)")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                
                Text("RSSI: \(polarDeviceInfo.rssi)")
                    .font(.footnote)
                    .foregroundColor(.secondary)
                
                HStack {
                    Text("Connectable:")
                    Image(systemName: polarDeviceInfo.connectable ? "checkmark.circle.fill" : "xmark.circle.fill")
                        .foregroundColor(polarDeviceInfo.connectable ? .green : .red)
                }
                .font(.footnote)
                .foregroundColor(.secondary)
            }
        }
    }
}

struct DeviceSearchRow_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            VStack {
                DeviceSearchRow( polarDeviceInfo:  PolarDeviceInfo("ABD", UUID(), -70, "Polar H10", true))
            }
        }
        .previewLayout(.fixed(width: 300, height: 70))
    }
}
