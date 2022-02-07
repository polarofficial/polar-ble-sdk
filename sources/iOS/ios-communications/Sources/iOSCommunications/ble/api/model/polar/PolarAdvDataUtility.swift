import Foundation

open class PolarAdvDataUtility {
        
    private static let BLE_ADV_POLAR_PREFIX_IN_LOCAL_NAME = "Polar"
    
    public static func getPolarModelNameFromAdvLocalName(advLocalName: String) -> String {
        if (isPolarDevice(advLocalName: advLocalName)) {
            let modelName = advLocalName.trimmingCharacters(in: .whitespacesAndNewlines)
                .replacingOccurrences(of: BLE_ADV_POLAR_PREFIX_IN_LOCAL_NAME + " ", with: "")
            
            if let endIndex = modelName.lastIndex(of: " ") {
                let mySubstring = modelName[..<(endIndex)]
                return String(mySubstring)
            } else {
                return ""
            }
        } else {
            return ""
        }
    }
    
    public static func isPolarDevice(advLocalName: String) -> Bool {
        return advLocalName.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix(BLE_ADV_POLAR_PREFIX_IN_LOCAL_NAME) &&
            advLocalName.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: " ").count > 2
    }
}
