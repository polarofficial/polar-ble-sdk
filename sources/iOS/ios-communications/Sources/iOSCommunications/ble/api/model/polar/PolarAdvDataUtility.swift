import Foundation

open class PolarAdvDataUtility {
        
    public static func getDeviceNameFromAdvLocalName(advLocalName: String, withPrefixToTrim prefix: String = "Polar") -> String {
        if (isValidDevice(advLocalName: advLocalName, requiredPrefix: prefix)) {
            let modelName = advLocalName.trimmingCharacters(in: .whitespacesAndNewlines)
                .replacingOccurrences(of: prefix != "" ? prefix + " " : "", with: "")
            
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
    
    public static func isValidDevice(advLocalName: String, requiredPrefix: String = "Polar") -> Bool {
        return advLocalName.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix(requiredPrefix) &&
            advLocalName.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: " ").count > 2
    }
}
